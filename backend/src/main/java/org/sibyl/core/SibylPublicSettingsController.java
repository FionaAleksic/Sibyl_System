package org.sibyl.core;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SibylPublicSettingsController {
    private final JdbcTemplate jdbc;

    public SibylPublicSettingsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // No secrets or admin data in this response.
    @GetMapping("/api/v1/settings/public")
    public Map<String, String> publicSettings() {
        return jdbc.queryForObject(
            "SELECT organization, language, accent FROM sibyl_organization WHERE id=1",
            (rs, row) -> Map.of(
                "organization", rs.getString("organization"),
                "language", rs.getString("language"),
                "accent", rs.getString("accent")));
    }
}
