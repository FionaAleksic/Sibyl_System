package org.sibyl.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Platform-neutral, add-on-owned deployment contract v1.
 * Only known operations are executable. Arbitrary commands and unrestricted
 * JVM entry points are never executed by the Core process.
 */
public record AddonDeploymentPlan(
        String id, String version, String kind,
        List<Dependency> dependencies, List<Operation> operations) {
    public record Dependency(String id, String version) {}
    public record Operation(String type, String path, String label) {}
    public static final int MAX_BYTES = 64 * 1024;

    public AddonDeploymentPlan {
        dependencies = List.copyOf(dependencies);
        operations = List.copyOf(operations);
    }

    public static AddonDeploymentPlan read(ZipFile zip, ObjectMapper mapper,
                                          String expectedId, String expectedVersion)
            throws Exception {
        ZipEntry descriptor = zip.getEntry("deployment.json");
        ZipEntry manifest = zip.getEntry("module.json");
        if (descriptor == null || manifest == null
            || descriptor.getSize() > MAX_BYTES || descriptor.getSize() < 0
            || manifest.getSize() > MAX_BYTES || manifest.getSize() < 0)
            throw new IllegalArgumentException("Deployment descriptor/manifest missing or too large");
        JsonNode root;
        JsonNode meta;
        try (InputStream data = zip.getInputStream(descriptor);
             InputStream metadata = zip.getInputStream(manifest)) {
            root = mapper.readTree(data);
            meta = mapper.readTree(metadata);
        }
        if (root == null || root.path("schema").asInt(-1) != 1
            || !expectedId.equals(root.path("id").asText())
            || !expectedVersion.equals(root.path("version").asText())
            || !expectedId.equals(meta.path("id").asText())
            || !expectedVersion.equals(meta.path("version").asText()))
            throw new IllegalArgumentException("Deployment ID/version mismatch");
        String kind = root.path("kind").asText();
        if (!kind.equals("feature") && !kind.equals("dependency"))
            throw new IllegalArgumentException("Unknown addon kind");
        if (!root.path("dependencies").isArray() || root.path("dependencies").size() > 16
            || !root.path("operations").isArray() || root.path("operations").size() > 24)
            throw new IllegalArgumentException("Invalid deployment contract arrays");
        List<Dependency> deps = new ArrayList<>();
        Set<String> depIds = new HashSet<>();
        for (JsonNode dep : root.path("dependencies")) {
            String id = dep.path("id").asText();
            String version = dep.path("version").asText();
            if (!validId(id) || id.equals(expectedId) || !depIds.add(id)
                || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-rc\\.[0-9]+)?"))
                throw new IllegalArgumentException("Invalid or duplicate dependency");
            deps.add(new Dependency(id,version));
        }
        List<Operation> operations = new ArrayList<>();
        Set<String> entries = new HashSet<>();
        for (JsonNode operation : root.path("operations")) {
            String type=operation.path("type").asText();
            if (!Set.of("publish-ui","migrate-db","register-worker").contains(type))
                throw new IllegalArgumentException("Unsupported install operation: " + type);
            String path=operation.path("path").asText();
            String label=operation.path("label").asText();
            if (!entries.add(type+":"+path) || zip.getEntry(path)==null
                || zip.getEntry(path).isDirectory())
                throw new IllegalArgumentException("Missing or duplicate install artifact");
            if (type.equals("publish-ui") && (kind.equals("dependency")
                || !validResource(path) || !path.endsWith(".html")
                || label.isBlank() || label.length()>48))
                throw new IllegalArgumentException("Invalid UI publication operation");
            if (type.equals("migrate-db") && (!path.matches("migrations/V[0-9]{3,6}__[A-Za-z0-9_-]{1,80}\\.sql")
                || !label.isBlank()))
                throw new IllegalArgumentException("Invalid database migration declaration");
            if (type.equals("register-worker") && (!path.matches("backend/[A-Za-z0-9_-]{1,80}\\.jar")
                || !label.isBlank()))
                throw new IllegalArgumentException("Invalid worker artifact declaration");
            if (type.equals("register-worker") && operations.stream()
                .anyMatch(o->o.type().equals("register-worker")))
                throw new IllegalArgumentException("Only one worker per addon version");
            operations.add(new Operation(type,path,label));
        }
        if (kind.equals("feature") && operations.stream().filter(o->o.type().equals("publish-ui")).count()!=1)
            throw new IllegalArgumentException("Feature addon needs exactly one UI entry");
        if (kind.equals("dependency") && operations.stream().anyMatch(o->o.type().equals("publish-ui")))
            throw new IllegalArgumentException("Dependency is not a navigation feature");
        return new AddonDeploymentPlan(expectedId,expectedVersion,kind,deps,operations);
    }
    public static boolean validId(String id) {
        return id != null && id.matches("Sibyl\\.[a-z][a-z0-9-]{0,62}");
    }
    public static boolean validResource(String path) {
        return path != null && path.length() <= 180
            && path.matches("ui/[A-Za-z0-9_./-]+")
            && !path.contains("//") && !path.contains("..")
            && !path.endsWith("/");
    }
}
