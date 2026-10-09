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
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Path root = Path.of(System.getenv().getOrDefault("SIBYL_ADDON_DATA", "/var/lib/sibyl/addons"));

    public record ReleaseInfo(String tag, boolean prerelease, String body, JsonNode asset, String digest) { }

    public GitHubReleaseService(ObjectMapper mapper) { this.mapper = mapper; }

    private HttpRequest request(URI uri, String accept) {
        String token = System.getenv("SIBYL_GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("SIBYL_GITHUB_TOKEN is required for the private GitHub repositories");
        }
        if (!Objects.equals(uri.getHost(), "api.github.com") || !"https".equals(uri.getScheme()))
            throw new IllegalArgumentException("Invalid GitHub API endpoint");
        return HttpRequest.newBuilder(uri).header("Accept", accept)
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(20)).GET().build();
    }

    public ReleaseInfo latest(AddonCatalog.Addon addon, String channel) throws Exception {
        if (!channel.equals("stable") && !channel.equals("prerelease")) throw new IllegalArgumentException("Unknown channel");
        String suffix = channel.equals("stable") ? "/releases/latest" : "/releases?per_page=20";
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
        for (JsonNode asset : release.path("assets")) {
            if (asset.path("name").asText("").endsWith(".zip")) { found = asset; break; }
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
        String url = release.asset().path("url").asText();
        String prefix = API + addon.repo() + "/releases/assets/";
        if (!url.startsWith(prefix) || !url.substring(prefix.length()).matches("[0-9]+"))
            throw new IllegalStateException("Unexpected asset URL");
        if (release.asset().path("size").asLong(Long.MAX_VALUE) > MAX_DOWNLOAD)
            throw new IllegalStateException("Release ZIP exceeds size limit");
        if (isDownloaded(addon)) throw new IllegalStateException("This add-on already has one downloaded version");

        Path dir = root.resolve(addon.id());
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, ".pending-", ".zip");
        try {
            var response = http.send(request(URI.create(url), "application/octet-stream"),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try (InputStream ignored = response.body()) { }
                throw new IOException("Release download returned HTTP " + response.statusCode());
            }
            String host = response.uri().getHost().toLowerCase(Locale.ROOT);
            if (!(host.equals("api.github.com") || host.equals("objects.githubusercontent.com") ||
                    host.equals("release-assets.githubusercontent.com") || host.equals("github.com")))
                throw new IOException("Asset redirected outside approved GitHub endpoints");
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
