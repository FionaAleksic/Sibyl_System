package org.sibyl.core;

import org.springframework.boot.ApplicationRunner;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SibylAccounts implements UserDetailsService, ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public SibylAccounts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(org.springframework.boot.ApplicationArguments arguments) {
        // First installation only. Never overwrite an existing password or recreate a deleted admin.
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sibyl_users", Integer.class);
        if (count != null && count == 0) {
            jdbc.update("""
                INSERT INTO sibyl_users (username, password_hash, role, must_change_password)
                VALUES ('admin', ?, 'ADMIN', TRUE)
                ON DUPLICATE KEY UPDATE username=username
                """, encoder.encode("friend"));
            jdbc.update("INSERT INTO sibyl_audit_events (actor, action) VALUES ('system', 'bootstrap-admin-created')");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) throw new UsernameNotFoundException("Unknown user");
        var rows = jdbc.query(
            "SELECT username, password_hash, role, enabled FROM sibyl_users WHERE username=?",
            (rs, row) -> User.withUsername(rs.getString("username"))
                .password("{bcrypt}" + rs.getString("password_hash"))
                .roles(rs.getString("role"))
                .disabled(!rs.getBoolean("enabled")).build(),
            username
        );
        if (rows.isEmpty()) throw new UsernameNotFoundException("Unknown user");
        return rows.get(0);
    }

    public boolean mustChangePassword(String username) {
        Boolean result = jdbc.queryForObject(
            "SELECT must_change_password FROM sibyl_users WHERE username=?", Boolean.class, username);
        return result != null && result;
    }

    public Map<String, Object> account(String username) {
        String role = jdbc.queryForObject(
            "SELECT role FROM sibyl_users WHERE username=?", String.class, username);
        return Map.of("username", username, "role", role,
            "mustChangePassword", mustChangePassword(username));
    }

    public record AccountInfo(String username, String role, boolean enabled, boolean mustChangePassword) {}

    public List<AccountInfo> listAccounts() {
        return jdbc.query(
            "SELECT username,role,enabled,must_change_password FROM sibyl_users ORDER BY username",
            (rs, row) -> new AccountInfo(rs.getString("username"), rs.getString("role"),
                rs.getBoolean("enabled"), rs.getBoolean("must_change_password")));
    }

    @Transactional
    public AccountInfo createAccount(String actor, String username, String password, String role) {
        if (username == null || !username.matches("[a-zA-Z][a-zA-Z0-9._-]{2,49}")
            || password == null || password.length() < 12 || password.length() > 128
            || password.equals("friend")
            || !("USER".equals(role) || "ADMIN".equals(role))) {
            throw new IllegalArgumentException("Invalid account fields");
        }
        String normalized = username.toLowerCase(Locale.ROOT);
        if (normalized.equals("admin")) throw new IllegalArgumentException("Reserved account");
        jdbc.update("""
            INSERT INTO sibyl_users (username, password_hash, role, must_change_password, enabled)
            VALUES (?, ?, ?, TRUE, TRUE)
            """, normalized, encoder.encode(password), role);
        jdbc.update("INSERT INTO sibyl_audit_events (actor, action) VALUES (?, ?)",
            actor, "account-created:" + normalized + ":" + role);
        return new AccountInfo(normalized, role, true, true);
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        if (currentPassword == null || newPassword == null
            || newPassword.length() < 12 || newPassword.length() > 128
            || newPassword.equals("friend") || newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("New password must be 12-128 characters and different from the initial password");
        }
        String hash = jdbc.queryForObject("SELECT password_hash FROM sibyl_users WHERE username=?",
            String.class, username);
        if (hash == null || !encoder.matches(currentPassword, hash))
            throw new IllegalArgumentException("Current password is invalid");
        jdbc.update("""
            UPDATE sibyl_users SET password_hash=?, must_change_password=FALSE,
                updated_at=CURRENT_TIMESTAMP WHERE username=?
            """, encoder.encode(newPassword), username);
        jdbc.update("INSERT INTO sibyl_audit_events (actor, action) VALUES (?, 'password-changed')", username);
    }
}
