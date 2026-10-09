package org.sibyl.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public final class AddonController {
    private final AddonCatalog catalog;
    private final GitHubReleaseService releases;

    public AddonController(AddonCatalog catalog, GitHubReleaseService releases) {
        this.catalog = catalog;
        this.releases = releases;
    }

    @GetMapping("/admin/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("header", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/addons/catalog")
    public Map<String, Object> catalog(@RequestParam(defaultValue = "stable") String channel) {
        if (!channel.equals("stable") && !channel.equals("prerelease")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid release channel");
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (AddonCatalog.Addon addon : catalog.modules()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", addon.id());
            item.put("name", addon.name());
            item.put("description", "GitHub: " + addon.repo());
            item.put("type", addon.type());
            try {
                boolean downloaded = releases.isDownloaded(addon);
                GitHubReleaseService.ReleaseInfo release = releases.latest(addon, channel);
                String status = release == null || release.asset() == null ||
                    !release.digest().matches("sha256:[a-fA-F0-9]{64}") ? "no_release" :
                    downloaded ? "downloaded" : "available";
                item.put("status", status);
                if (release != null) {
                    item.put("tag", release.tag());
                    item.put("version", release.tag());
                }
            } catch (GitHubReleaseService.RepositoryUnavailableException ex) {
                item.put("status", "repository_unavailable");
                item.put("error", "Repository ist privat, nicht vorhanden oder ohne Berechtigung nicht sichtbar");
            } catch (Exception ex) {
                item.put("status", "error");
                item.put("error", "GitHub-Release-API nicht erreichbar oder nicht eingerichtet");
            }
            entries.add(item);
        }
        return Map.of("addons", entries, "channel", channel);
    }

    public record DownloadRequest(String channel, String releaseTag) {}

    @PostMapping("/admin/addons/{id}/download")
    public Map<String, String> download(@PathVariable String id, @RequestBody DownloadRequest request) {
        if (request == null || request.channel() == null ||
            (!request.channel().equals("stable") && !request.channel().equals("prerelease")) ||
            request.releaseTag() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel and releaseTag required");
        }
        try {
            String version = releases.download(catalog.get(id), request.channel(), request.releaseTag());
            return Map.of("id", id, "version", version, "state", "downloaded_not_activated");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown add-on ID");
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to fetch and verify GitHub release");
        }
    }
}
