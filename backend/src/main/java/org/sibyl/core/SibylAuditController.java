package org.sibyl.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only, cursor-paginated administrative audit history. No raw request
 * bodies, credentials, session identifiers or arbitrary SQL are returned.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
public class SibylAuditController {
    private final JdbcTemplate jdbc;
    public SibylAuditController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record AuditEvent(long id, String actor, String action, Instant createdAt) {}

    @GetMapping
    public Map<String, List<AuditEvent>> list(
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) Long before) {
        if (limit < 1 || limit > 100 || (before != null && before <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid audit cursor or page size");
        }
        List<AuditEvent> items = jdbc.query(
            "SELECT id, actor, action, created_at FROM sibyl_audit_events " +
            "WHERE id < ? ORDER BY id DESC LIMIT ?",
            (rs, row) -> new AuditEvent(
                rs.getLong("id"), rs.getString("actor"), rs.getString("action"),
                rs.getTimestamp("created_at").toInstant()),
            before == null ? Long.MAX_VALUE : before, limit);
        return Map.of("items", items);
    }
}
