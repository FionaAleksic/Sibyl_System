package org.sibyl.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class GitHubReleaseService {
    private static final int MAX_DOWNLOAD = 64 * 1024 * 1024;
    private static final long MAX_UNPACKED = 128L * 1024 * 1024;
    private static final String API = "https://api.github.com/repos/";
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Path root = Path.of(System.getenv().getOrDefault("SIBYL_ADDON_DATA", "/var/lib/sibyl/addons"));

    public record ReleaseInfo(String tag, boolean prerelease, String body, JsonNode asset, String digest) { }

    public GitHubReleaseService(ObjectMapper mapper) { this.mapper = mapper; }

    private HttpRequest request(URI uri, String accept) {
        String token = System.getenv("SIBYL_GITHUB_TOKEN");
        if (!Objects.equals(uri.getHost(), "api.github.com") || !"https".equals(uri.getScheme()))
            throw new IllegalArgumentException("Invalid GitHub API endpoint");
        // Public repositories work without any token: every Sibyl installation can query them.
        // Private repositories remain opt-in and require a SERVER-SIDE token.
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .header("Accept", accept)
                .header("User-Agent", "Sibyl-System-Addon-Manager")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(20)).GET();
        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.build();
    }

    public ReleaseInfo latest(AddonCatalog.Addon addon, String channel) throws Exception {
        if (!channel.equals("stable") && !channel.equals("prerelease")) throw new IllegalArgumentException("Unknown channel");
        String suffix = channel.equals("stable") ? "/releases/latest" : "/releases?per_page=100";
        URI uri = URI.create(API + addon.repo() + suffix);
        HttpResponse<String> response = http.send(request(uri, "application/vnd.github+json"),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) throw new IOException("GitHub release lookup returned HTTP " + response.statusCode());
        JsonNode json = mapper.readTree(response.body());
        JsonNode release;
        if (channel.equals("stable")) release = json;
        else {
            release = null;
            for (JsonNode r : json) {
                if (r.path("prerelease").asBoolean(false) && !r.path("draft").asBoolean(true)) {
                    release = r; break;
                }
            }
        }
        if (release == null || release.isNull() || release.path("draft").asBoolean(true)) return null;
        String tag = release.path("tag_name").asText("");
        if (!tag.matches("v?[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?"))
            throw new IOException("Release tag is not a supported SemVer");
        JsonNode found = null;
        // Never download a generic source archive or an unrelated asset.
        // Every add-on ships exactly one versioned ZIP as a GitHub Release asset.
        String expectedName = addon.id() + "-" + tag + ".zip";
        for (JsonNode asset : release.path("assets")) {
            if (expectedName.equals(asset.path("name").asText())) { found = asset; break; }
        }
        String digest = found == null ? "" : found.path("digest").asText("");
        return new ReleaseInfo(tag, release.path("prerelease").asBoolean(false),
                release.path("body").asText(""), found, digest);
    }

    public boolean isDownloaded(AddonCatalog.Addon addon) throws IOException {
        Path dir = root.resolve(addon.id());
        if (!Files.isDirectory(dir)) return false;
        try (var entries = Files.list(dir)) {
            return entries.anyMatch(p -> p.getFileName().toString().endsWith(".zip"));
        }
    }

    public synchronized String download(AddonCatalog.Addon addon, String channel, String expectedTag) throws Exception {
        ReleaseInfo release = latest(addon, channel); // Re-check current GitHub release; NEVER a branch.
        if (release == null || !release.tag().equals(expectedTag)) throw new IllegalStateException("Release changed or unavailable");
        if (release.asset() == null || !release.digest().matches("sha256:[a-fA-F0-9]{64}"))
            throw new IllegalStateException("Verified ZIP release asset with SHA-256 digest required");
        // GitHub REST API asset endpoints are best for private repos with a token.
        // Public repos use the public, published browser_download_url (no token).
        String token = System.getenv("SIBYL_GITHUB_TOKEN");
        boolean privateAccess = token != null && !token.isBlank();
        String url = privateAccess
                ? release.asset().path("url").asText("")
                : release.asset().path("browser_download_url").asText("");
        String expectedName = addon.id() + "-" + release.tag() + ".zip";
        URI source = URI.create(url);
        if (!"https".equalsIgnoreCase(source.getScheme())
                || source.getRawQuery() != null || source.getRawFragment() != null
                || source.getUserInfo() != null || source.getPort() != -1)
            throw new IllegalStateException("Invalid GitHub asset source");
        if (privateAccess) {
            String prefix = "/repos/" + addon.repo() + "/releases/assets/";
            if (!"api.github.com".equals(source.getHost())
                    || !source.getPath().startsWith(prefix)
                    || !source.getPath().substring(prefix.length()).matches("[0-9]+"))
                throw new IllegalStateException("Unexpected private GitHub asset URL");
        } else {
            String expectedPath = "/" + addon.repo() + "/releases/download/" + release.tag() + "/" + expectedName;
            if (!"github.com".equals(source.getHost()) || !expectedPath.equals(source.getPath()))
                throw new IllegalStateException("Unexpected public GitHub asset URL");
        }
        if (release.asset().path("size").asLong(Long.MAX_VALUE) > MAX_DOWNLOAD)
            throw new IllegalStateException("Release ZIP exceeds size limit");
        if (isDownloaded(addon)) throw new IllegalStateException("This add-on already has one downloaded version");

        Path dir = root.resolve(addon.id());
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, ".pending-", ".zip");
        try {
            HttpRequest downloadRequest = privateAccess
                    ? request(source, "application/octet-stream")
                    : HttpRequest.newBuilder(source)
                        .header("Accept", "application/octet-stream")
                        .header("User-Agent", "Sibyl-System-Addon-Manager")
                        .timeout(Duration.ofSeconds(30)).GET().build();
            var response = http.send(downloadRequest,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 302 || response.statusCode() == 303 ||
                    response.statusCode() == 307 || response.statusCode() == 308) {
                URI target = response.uri().resolve(response.headers().firstValue("location")
                        .orElseThrow(() -> new IOException("GitHub asset redirect has no Location")));
                try (InputStream discarded = response.body()) { }
                String targetHost = target.getHost() == null ? "" : target.getHost().toLowerCase(Locale.ROOT);
                if (!"https".equalsIgnoreCase(target.getScheme()) ||
                    !(targetHost.equals("release-assets.githubusercontent.com") ||
                      targetHost.equals("objects.githubusercontent.com")))
                    throw new IOException("GitHub asset redirected to unapproved host");
                // Do not forward the private GitHub API bearer token to asset/CDN hosts.
                response = http.send(HttpRequest.newBuilder(target)
                        .header("Accept", "application/octet-stream")
                        .timeout(Duration.ofSeconds(60)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
            }
            if (response.statusCode() != 200) {
                try (InputStream ignored = response.body()) { }
                throw new IOException("Release download returned HTTP " + response.statusCode());
            }
            String host = response.uri().getHost().toLowerCase(Locale.ROOT);
            if (!(host.equals("api.github.com") || host.equals("objects.githubusercontent.com") ||
                    host.equals("release-assets.githubusercontent.com") || host.equals("github.com")))
                throw new IOException("Asset outside approved GitHub endpoints");
            MessageDigest dig = MessageDigest.getInstance("SHA-256");
            int total = 0;
            try (InputStream in = response.body(); var out = Files.newOutputStream(tmp)) {
                byte[] block = new byte[8192];
                int n;
                while ((n = in.read(block)) != -1) {
                    total += n;
                    if (total > MAX_DOWNLOAD) throw new IOException("Asset too large");
                    dig.update(block, 0, n);
                    out.write(block, 0, n);
                }
            }
            String sha = HexFormat.of().formatHex(dig.digest());
            if (!sha.equalsIgnoreCase(release.digest().substring("sha256:".length())))
                throw new IOException("Release checksum mismatch");
            validateZip(tmp, addon.id(), release.tag());
            Path destination = dir.resolve(release.tag() + ".zip");
            Files.move(tmp, destination, StandardCopyOption.ATOMIC_MOVE);
            return release.tag();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private void validateZip(Path zip, String addonId, String tag) throws Exception {
        JsonNode manifest = null;
        int entries = 0;
        long total = 0;
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            byte[] buffer = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                if (++entries > 2000) throw new IOException("Too many ZIP entries");
                String name = e.getName();
                if (name.startsWith("/") || name.contains("\\") || name.contains("../") || name.equals("..") || name.contains(":"))
                    throw new IOException("Unsafe archive member path");
                java.io.ByteArrayOutputStream manifestBytes = name.equals("module.json") ? new java.io.ByteArrayOutputStream() : null;
                int n;
                while ((n = zin.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_UNPACKED) throw new IOException("ZIP exceeds decompression limit");
                    if (manifestBytes != null) {
                        if (manifestBytes.size() + n > 65536) throw new IOException("Manifest exceeds size limit");
                        manifestBytes.write(buffer, 0, n);
                    }
                }
                if (manifestBytes != null) manifest = mapper.readTree(manifestBytes.toByteArray());
                zin.closeEntry();
            }
        }
        String expectedVersion = tag.startsWith("v") ? tag.substring(1) : tag;
        if (manifest == null || !addonId.equals(manifest.path("id").asText()) ||
            !expectedVersion.equals(manifest.path("version").asText()))
            throw new IOException("Manifest ID/version mismatch");
    }
}
