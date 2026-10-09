package org.sibyl.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stores only non-secret add-on configuration.
 * Credentials must be provisioned separately into a protected secret store.
 * The add-on runtime is responsible for schema/behavior validation at activation.
 */
@RestController
@RequestMapping("/api/v1/admin/addon-settings")
public class SibylAddonSettingsController {
    private final JdbcTemplate jdbc;
    private final AddonCatalog catalog;
    private final ObjectMapper mapper;

    public SibylAddonSettingsController(JdbcTemplate jdbc, AddonCatalog catalog, ObjectMapper mapper) {
        this.jdbc = jdbc; this.catalog = catalog; this.mapper = mapper;
    }

    @GetMapping("/{id}")
    public Map<String,Object> get(@PathVariable String id) {
        validAddon(id);
        var rows = jdbc.query(
            "SELECT settings_json::text FROM sibyl_addon_settings WHERE addon_id=?",
            (rs,n) -> rs.getString(1), id);
        try {
            JsonNode value = rows.isEmpty()?mapper.createObjectNode():mapper.readTree(rows.get(0));
            return Map.of("id",id,"settings",value);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read settings");
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String,Object> update(Principal principal, @PathVariable String id, @RequestBody JsonNode data) {
        validAddon(id);
        if (data == null || !data.isObject() || data.toString().length() > 24_000
            || !nonSecretTree(data, 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Configuration must be small JSON without password, token or secret fields");
        }
        jdbc.update("""
            INSERT INTO sibyl_addon_settings (addon_id, settings_json)
            VALUES (?, CAST(? AS jsonb))
            ON CONFLICT (addon_id) DO UPDATE
            SET settings_json=EXCLUDED.settings_json, updated_at=CURRENT_TIMESTAMP
            """, id, data.toString());
        jdbc.update("INSERT INTO sibyl_audit_events (actor, action) VALUES (?, ?)",
            principal.getName(), "addon-settings-updated:" + id);
        return get(id);
    }

    private void validAddon(String id) {
        try { catalog.get(id); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown add-on");
        }
    }

    private boolean nonSecretTree(JsonNode node, int depth) {
        if (depth > 6 || node.isBinary() || node.isNull()) return false;
        if (node.isObject()) {
            if (node.size() > 100) return false;
            Iterator<Map.Entry<String,JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String key = entry.getKey();
                if (key.length() > 64 || !key.matches("[a-zA-Z][a-zA-Z0-9_.-]*")
                    || key.toLowerCase(Locale.ROOT).matches(".*(password|passwd|secret|token|credential|privatekey|apikey).*")
                    || !nonSecretTree(entry.getValue(),depth+1)) return false;
            }
        } else if (node.isArray()) {
            if (node.size() > 100) return false;
            for (JsonNode item : node) if (!nonSecretTree(item,depth+1)) return false;
        } else if (node.isTextual()) {
            if (node.asText().length() > 2048) return false;
        } else if (!node.isNumber() && !node.isBoolean()) {
            return false;
        }
        return true;
    }
}
