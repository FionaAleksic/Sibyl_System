package org.sibyl.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * RC.8 first-party compatibility bridge.
 * Runs ONLY specifically reviewed/pinned 0.1.0 add-on library entrypoints.
 * Arbitrary Java plugins are NOT dynamically loaded. Future untrusted plugins
 * require process isolation, sandboxing and a versioned plugin SDK.
 */
@RestController
@RequestMapping("/api/v1/modules")
public class SibylModuleController {
    private record Approved(String id, String version, String digest, String name,
                            String category, String className) {}
    private static final List<Approved> APPROVED = List.of(
        new Approved("Sibyl.ad", "0.1.0-rc.1",
            "5eb57f3150e949fb55884b6092c76767e87a76534e3b5636f45ad73c6f39ba9a",
            "Active Directory", "dependency", ""),
        new Approved("Sibyl.inventory", "0.1.0-rc.1",
            "0bde21d9abde6468ae2e8728140d17f85a21f27c20d6afb8dbfde83a7a62160e",
            "Inventar", "feature", "org.sibyl.addons.inventory.InventorySnapshot"),
        new Approved("Sibyl.planning", "0.1.0-rc.1",
            "57c687f77d35febd09348d41af9b51bbbf089fbfa1bf0bfb8a9f367055cea033",
            "Planung", "feature", "org.sibyl.addons.planning.FairRotation"),
        new Approved("Sibyl.doc", "0.1.0-rc.1",
            "83e9716f141540003a7d5715deaf128329c2a0034ef6544dd19cb5e56e410158",
            "Dokumentation", "feature", ""),
        new Approved("Sibyl.report-book", "0.1.0-rc.1",
            "573cfc6789f38684bc89a3f792bebc4af5786426cf9f6c23b2cba3877332ea91",
            "Berichtsheft", "feature", "")
    );
    private final Path root = Path.of(System.getenv().getOrDefault(
        "SIBYL_ADDON_DATA", "/var/lib/sibyl/addons")).toAbsolutePath().normalize();
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    public SibylModuleController(ObjectMapper json, JdbcTemplate jdbc) {
        this.json = json;
        this.jdbc = jdbc;
    }

    private Path packageFile(Approved addon) {
        return root.resolve(addon.id()).resolve("v" + addon.version() + ".zip");
    }
    private boolean verified(Approved addon) {
        Path path = packageFile(addon);
        try {
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)
                || Files.isSymbolicLink(path.getParent()) || Files.size(path) > 64L*1024*1024) return false;
            byte[] checksum;
            try (InputStream in = Files.newInputStream(path)) {
                checksum = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
            }
            if (!HexFormat.of().formatHex(checksum).equals(addon.digest())) return false;
            try (ZipFile zip = new ZipFile(path.toFile())) {
                ZipEntry manifest = zip.getEntry("module.json");
                if (manifest == null || manifest.getSize() > 65536) return false;
                JsonNode metadata;
                try (InputStream in = zip.getInputStream(manifest)) {
                    metadata = json.readTree(in);
                }
                return addon.id().equals(metadata.path("id").asText())
                    && addon.version().equals(metadata.path("version").asText());
            }
        } catch (Exception ignored) {
            return false;
        }
    }
    private Approved requirePackage(String id) {
        Approved addon = APPROVED.stream().filter(a->a.id().equals(id))
            .findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!verified(addon))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Addon fehlt, wurde verändert oder die Version wird nicht unterstützt");
        return addon;
    }

    @GetMapping
    public Map<String,Object> modules() {
        List<Map<String,Object>> entries = new ArrayList<>();
        for (Approved addon : APPROVED) {
            if (!verified(addon)) continue;
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("id", addon.id());
            item.put("version", addon.version());
            item.put("name", addon.name());
            item.put("type", addon.category());
            item.put("page", addon.category().equals("feature")
                ? "/module.html?id=" + addon.id() : "");
            item.put("state", addon.category().equals("dependency")
                ? "downloaded_configuration_required" : "adapter_available");
            entries.add(item);
        }
        return Map.of("modules", entries, "runtime", "first-party-bridge-rc8");
    }

    /** Invocation only of digest-pinned first-party functions, not generic ZIP code. */
    private Object invokePinned(String id, String method, Class<?>[] signature, Object... args) {
        Approved addon = requirePackage(id);
        if (addon.className().isBlank()) throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
        Path temp = null;
        try (ZipFile zip = new ZipFile(packageFile(addon).toFile())) {
            String jarName = "sibyl-" + id.substring("Sibyl.".length())
                + "-" + addon.version() + ".jar";
            ZipEntry member = zip.getEntry(jarName);
            if (member == null || member.getSize() > 8L * 1024 * 1024)
                throw new IllegalStateException("Expected first-party class archive missing");
            temp = Files.createTempFile("sibyl-approved-addon-", ".jar");
            try (InputStream in = zip.getInputStream(member)) {
                Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try (URLClassLoader loader = new URLClassLoader(
                new URL[]{temp.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                Class<?> runtime = Class.forName(addon.className(), true, loader);
                return runtime.getMethod(method, signature).invoke(null, args);
            }
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof IllegalArgumentException)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Eingabe");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Addon-Verarbeitung fehlgeschlagen");
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Freigegebener Addon-Baustein nicht ladbar");
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
        }
    }

    public record PlanningInput(List<String> participants, int turns) {}
    @PostMapping("/planning/preview")
    public Map<String,Object> planning(@RequestBody PlanningInput input) {
        if (input == null || input.participants()==null || input.participants().size()>80
            || input.turns()<1 || input.turns()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximal 80 Personen / 100 Runden");
        Object result = invokePinned("Sibyl.planning", "generate",
            new Class<?>[]{List.class, int.class}, input.participants(), input.turns());
        return Map.of("assignments", result, "preview", true,
            "notice", "Nur lokale Vorschau. AD-Import und persistierte Pläne benötigen weitere Adapter.");
    }

    public record IpamInput(String cidr, Map<String,String> leases, List<String> reserved) {}
    @PostMapping("/inventory/ipam")
    public Map<String,Object> ipam(@RequestBody IpamInput input) {
        if (input == null || input.cidr()==null || !input.cidr().matches(
                "(?:[0-9]{1,3}\\.){3}[0-9]{1,3}/(?:2[3-9]|30)")
            || input.leases()!=null && input.leases().size()>512
            || input.reserved()!=null && input.reserved().size()>512)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nur /23 bis /30 (maximal 510 Hosts)");
        Object addresses = invokePinned("Sibyl.inventory", "addressList",
            new Class<?>[]{String.class, Map.class, Set.class},
            input.cidr(), input.leases()==null?Map.of():input.leases(),
            input.reserved()==null?Set.of():Set.copyOf(input.reserved()));
        return Map.of("cidr",input.cidr(),"addresses",addresses,
            "notice","Bestandsübersicht aus eingegebenen Daten. Automatische DHCP-/ICMP-Collector folgen.");
    }

    public record WikiInput(String title, String body, Integer version) {}
    private void requireDoc() { requirePackage("Sibyl.doc"); }
    @GetMapping("/wiki/pages")
    public List<Map<String,Object>> wikiPages() {
        requireDoc();
        return jdbc.queryForList("""
            SELECT id,title,body,owner,version,updated_at FROM sibyl_wiki_pages
            WHERE archived=0 ORDER BY updated_at DESC LIMIT 200
            """);
    }
    private void validate(WikiInput input) {
        if (input==null || input.title()==null || input.title().isBlank()
            || input.title().length()>240 || input.body()==null
            || input.body().length()>100_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dokument ungültig");
    }
    @PostMapping("/wiki/pages")
    @Transactional
    public Map<String,Object> createWiki(Principal principal, @RequestBody WikiInput input) {
        requireDoc(); validate(input);
        String id=UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO sibyl_wiki_pages (id,title,body,owner,version)
            VALUES (?,?,?,?,1)
            """, id, input.title().strip(), input.body(), principal.getName());
        jdbc.update("INSERT INTO sibyl_audit_events(actor,action) VALUES (?,?)",
            principal.getName(), "wiki-page-created");
        return Map.of("id",id,"version",1);
    }
    @PutMapping("/wiki/pages/{id}")
    @Transactional
    public Map<String,Object> editWiki(Principal principal, Authentication auth,
                                      @PathVariable String id, @RequestBody WikiInput input) {
        requireDoc(); validate(input);
        if (!id.matches("[0-9a-fA-F-]{36}") || input.version()==null || input.version()<1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        boolean admin=auth.getAuthorities().stream().anyMatch(
            a->a.getAuthority().equals("ROLE_ADMIN"));
        int rows = admin
            ? jdbc.update("""
                UPDATE sibyl_wiki_pages SET title=?,body=?,version=version+1,
                    updated_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND version=? AND archived=0
                """,input.title().strip(),input.body(),id,input.version())
            : jdbc.update("""
                UPDATE sibyl_wiki_pages SET title=?,body=?,version=version+1,
                    updated_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND version=? AND owner=? AND archived=0
                """,input.title().strip(),input.body(),id,input.version(),principal.getName());
        if (rows!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Version geändert oder kein Schreibrecht");
        jdbc.update("INSERT INTO sibyl_audit_events(actor,action) VALUES (?,?)",
            principal.getName(),"wiki-page-updated");
        return Map.of("id",id,"version",input.version()+1);
    }
}
