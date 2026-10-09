package org.sibyl.core;

import java.io.InputStream;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AddonCatalog {
    public record Addon(String id, String repo, String name, String type) {}
    public record Catalog(int schema, String provider, boolean releaseOnly, List<Addon> modules) {}
    private final Catalog catalog;

    AddonCatalog(ObjectMapper mapper) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/catalog/default.json")) {
            if (stream == null) throw new IllegalStateException("Missing signed/reviewed add-on catalog");
            catalog = mapper.readValue(stream, Catalog.class);
        }
        if (!catalog.releaseOnly() || !"github".equals(catalog.provider()) || catalog.schema() != 1) {
            throw new IllegalStateException("Unsupported release-only catalog");
        }
        for (Addon addon: catalog.modules()) {
            if (!addon.id().matches("Sibyl\\.[a-z][a-z0-9-]*") ||
                !addon.repo().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                throw new IllegalStateException("Invalid trusted repository descriptor");
            }
        }
    }
    public List<Addon> modules() { return catalog.modules(); }
    public Addon get(String id) {
        return modules().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow(
            () -> new IllegalArgumentException("Unknown add-on id"));
    }
}
