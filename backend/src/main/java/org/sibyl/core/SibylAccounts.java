package org.sibyl.core;

import org.springframework.boot.ApplicationRunner;
import java.util.Map;
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
                ON CONFLICT (username) DO NOTHING
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
        return Map.of("username", username, "role", "ADMIN",
            "mustChangePassword", mustChangePassword(username));
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
