package org.sibyl.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Service;

/**
 * Generic form metadata for any downloaded addon ZIP. Schema is read only from
 * the reviewed local archive, never from a URL chosen by the browser.
 * Templates are UI hints, NOT saved automatically and never contain secrets.
 */
@Service
public class AddonConfigTemplates {
    private static final int MAX_SCHEMA = 64 * 1024;
    private final ObjectMapper mapper;
    private final AddonCatalog catalog;
    private final Path root = Path.of(System.getenv().getOrDefault(
        "SIBYL_ADDON_DATA", "/var/lib/sibyl/addons")).toAbsolutePath().normalize();

    public AddonConfigTemplates(ObjectMapper mapper, AddonCatalog catalog) {
        this.mapper = mapper;
        this.catalog = catalog;
    }

    public record Template(JsonNode schema, ObjectNode defaults, List<String> required,
                           String releaseTag, boolean available) {}

    public Template load(String id, String tag) {
        catalog.get(id);
        Path archive = findArchive(id, tag);
        if (archive == null)
            return new Template(mapper.createObjectNode(), mapper.createObjectNode(),
                List.of(), "", false);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry("config.schema.json");
            if (entry == null || entry.isDirectory() || entry.getSize() < 0
                || entry.getSize() > MAX_SCHEMA)
                return new Template(mapper.createObjectNode(), mapper.createObjectNode(),
                    List.of(), archive.getFileName().toString().replace(".zip", ""), false);
            JsonNode schema;
            try (InputStream input = zip.getInputStream(entry)) {
                schema = mapper.readTree(input.readNBytes(MAX_SCHEMA + 1));
            }
            if (schema == null || !"object".equals(schema.path("type").asText())
                || !schema.path("properties").isObject()
                || schema.path("properties").size() > 80)
                throw new IllegalArgumentException("Unsupported addon configuration schema");
            ObjectNode defaults = mapper.createObjectNode();
            schema.path("properties").fields().forEachRemaining(field -> {
                String key = field.getKey();
                JsonNode rule = field.getValue();
                if (!key.matches("[a-zA-Z][a-zA-Z0-9_.-]{0,63}") || secret(key)) return;
                if (rule.has("default") && !rule.path("default").isNull())
                    defaults.set(key, rule.get("default").deepCopy());
                else if (rule.path("type").asText().equals("array")
                    && rule.path("prefixItems").isArray()) {
                    var values = mapper.createArrayNode();
                    rule.path("prefixItems").forEach(part -> {
                        if (part.has("const")) values.add(part.get("const").asText());
                    });
                    if (!values.isEmpty()) defaults.set(key, values);
                }
            });
            List<String> required = new ArrayList<>();
            if (schema.path("required").isArray()) schema.path("required").forEach(value -> {
                String key = value.asText();
                if (schema.path("properties").has(key) && !secret(key)) required.add(key);
            });
            return new Template(schema, defaults, List.copyOf(required),
                archive.getFileName().toString().replace(".zip", ""), true);
        } catch (Exception ex) {
            throw new IllegalStateException("Addon configuration schema cannot be loaded", ex);
        }
    }

    private Path findArchive(String id, String releaseTag) {
        Path dir = root.resolve(id).normalize();
        if (!dir.startsWith(root) || Files.isSymbolicLink(root) || Files.isSymbolicLink(dir)
            || !Files.isDirectory(dir)) return null;
        if (releaseTag != null && !releaseTag.isBlank()) {
            if (!releaseTag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-zA-Z0-9.-]+)?"))
                throw new IllegalArgumentException("Invalid release version");
            return safeArchive(dir.resolve(releaseTag + ".zip"));
        }
        try (var files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().matches(
                    "v[0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-zA-Z0-9.-]+)?\\.zip"))
                .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                .map(this::safeArchive)
                .filter(p -> p != null)
                .findFirst().orElse(null);
        } catch (Exception ex) { return null; }
    }

    private Path safeArchive(Path path) {
        try {
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && Files.size(path) <= 64L * 1024 * 1024) return path;
        } catch (Exception ignored) { }
        return null;
    }

    public static boolean secret(String name) {
        return name.toLowerCase(Locale.ROOT).matches(
            ".*(password|passwd|secret|token|credential(?!environmentvariable)|privatekey|apikey).*");
    }

    public List<String> missingRequired(Template template, JsonNode values) {
        List<String> missing = new ArrayList<>();
        for (String key : template.required()) {
            JsonNode value = values.path(key);
            if (value.isMissingNode() || value.isNull()
                || value.isTextual() && value.asText().isBlank())
                missing.add(key);
        }
        return missing;
    }

    /** Subset of JSON schema sufficient for current first-party addon schemas. */
    public boolean validate(Template template, JsonNode values) {
        if (!values.isObject() || !missingRequired(template, values).isEmpty()) return false;
        if (!template.available()) return true; // No ZIP: legacy sanitized settings contract.
        JsonNode properties = template.schema().path("properties");
        var iter = values.fields();
        while (iter.hasNext()) {
            Map.Entry<String, JsonNode> field = iter.next();
            JsonNode rule = properties.path(field.getKey());
            JsonNode value = field.getValue();
            if (rule.isMissingNode() || secret(field.getKey()) || !check(rule, value)) return false;
        }
        return true;
    }

    private boolean check(JsonNode rule, JsonNode value) {
        String type = rule.path("type").asText("");
        if (type.equals("string")) {
            if (!value.isTextual()) return false;
            String text = value.asText();
            if (text.length() < rule.path("minLength").asInt(0)
                || text.length() > rule.path("maxLength").asInt(2048)) return false;
            if (rule.has("pattern")) {
                try {
                    if (!java.util.regex.Pattern.compile(rule.get("pattern").asText())
                        .matcher(text).matches()) return false;
                } catch (RuntimeException ex) { return false; }
            }
        } else if (type.equals("integer")) {
            if (!value.isIntegralNumber() || value.asLong() < rule.path("minimum").asLong(Long.MIN_VALUE)
                || value.asLong() > rule.path("maximum").asLong(Long.MAX_VALUE)) return false;
        } else if (type.equals("boolean")) {
            if (!value.isBoolean()) return false;
        } else if (type.equals("array")) {
            if (!value.isArray()
                || value.size() < rule.path("minItems").asInt(0)
                || value.size() > rule.path("maxItems").asInt(100)) return false;
            JsonNode prefix = rule.path("prefixItems");
            for (int i = 0; i < prefix.size(); i++) {
                if (i >= value.size() || !check(prefix.get(i), value.get(i))) return false;
            }
        } else if (!type.isEmpty()) return false;
        if (rule.has("const") && !rule.get("const").equals(value)) return false;
        if (rule.has("enum")) {
            boolean matched = false;
            for (JsonNode allowed : rule.get("enum")) matched |= allowed.equals(value);
            if (!matched) return false;
        }
        return true;
    }
}
