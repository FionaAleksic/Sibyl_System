package org.sibyl.core;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/admin")
public class SibylAdminController {
    private final SibylAccounts accounts;
    private final JdbcTemplate jdbc;

    public SibylAdminController(SibylAccounts accounts, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    @GetMapping("/account")
    public Map<String, Object> account(Principal principal) { return accounts.account(principal.getName()); }

    public record PasswordChange(String currentPassword, String newPassword) {}

    @PostMapping("/account/password")
    public Map<String, Object> change(Principal principal, @RequestBody PasswordChange change) {
        if (change == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try { accounts.changePassword(principal.getName(), change.currentPassword(), change.newPassword()); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid current or new password");
        }
        return Map.of("changed", true, "message", "Please sign in with the new password");
    }

    public record Settings(String organization, String language, String timezone, String accent) {}

    @GetMapping("/settings")
    public Settings settings() {
        return jdbc.queryForObject(
            "SELECT organization, language, timezone, accent FROM sibyl_organization WHERE id=1",
            (rs, row) -> new Settings(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)));
    }

    @PutMapping("/settings")
    @Transactional
    public Settings updateSettings(Principal principal, @RequestBody Settings body) {
        if (body == null || body.organization() == null || body.organization().isBlank()
            || body.organization().length() > 80
            || !("de".equals(body.language()) || "en".equals(body.language()))
            || !("Europe/Berlin".equals(body.timezone())
                 || "Europe/London".equals(body.timezone()) || "UTC".equals(body.timezone())
                 || "America/New_York".equals(body.timezone()))
            || !("classic".equals(body.accent()) || "teal".equals(body.accent())
                 || "violet".equals(body.accent()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid organization settings");
        }
        jdbc.update("""
            UPDATE sibyl_organization SET organization=?, language=?, timezone=?, accent=?,
                updated_at=CURRENT_TIMESTAMP WHERE id=1
            """, body.organization().trim(), body.language(), body.timezone(), body.accent());
        jdbc.update("INSERT INTO sibyl_audit_events (actor, action) VALUES (?, 'organization-settings-updated')",
            principal.getName());
        return settings();
    }
}
