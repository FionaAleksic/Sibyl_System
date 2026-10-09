package org.sibyl.core;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SibylInstallationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Test @Order(1)
    void bootstrapCreatesExactlyOneHashedAdminAndDefaultSettings() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sibyl_users WHERE username='admin'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
        String hash = jdbc.queryForObject(
            "SELECT password_hash FROM sibyl_users WHERE username='admin'", String.class);
        org.junit.jupiter.api.Assertions.assertTrue(bcrypt.matches("friend", hash));
        org.junit.jupiter.api.Assertions.assertNotEquals("friend", hash);
        Boolean force = jdbc.queryForObject(
            "SELECT must_change_password FROM sibyl_users WHERE username='admin'", Boolean.class);
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, force);
        String name = jdbc.queryForObject(
            "SELECT organization FROM sibyl_organization WHERE id=1", String.class);
        org.junit.jupiter.api.Assertions.assertEquals("Sibyl System", name);
    }

    @Test @Order(2)
    void unauthenticatedAdministrationIsDenied() throws Exception {
        mvc.perform(get("/api/v1/admin/account")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/addons/catalog?channel=prerelease"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/settings/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organization", equalTo("Sibyl System")));
    }

    @Test @Order(3)
    void bootstrapAccountMustChangePasswordBeforeConfiguration() throws Exception {
        mvc.perform(get("/api/v1/admin/account").with(httpBasic("admin", "friend")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mustChangePassword", equalTo(true)));
        mvc.perform(get("/api/v1/admin/settings").with(httpBasic("admin", "friend")))
            .andExpect(status().isLocked());
        mvc.perform(put("/api/v1/admin/settings").with(httpBasic("admin", "friend"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"organization":"Example","language":"de",
                     "timezone":"UTC","accent":"teal"}
                    """))
            .andExpect(status().isLocked());
    }

    @Test @Order(4)
    void changingBootstrapPasswordEnablesDatabaseSettingsAndRejectsFriend() throws Exception {
        String newPassword = "Changed-Example-Password-2026!";
        mvc.perform(post("/api/v1/admin/account/password")
                .with(httpBasic("admin", "friend")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"friend",
                     "newPassword":"Changed-Example-Password-2026!"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.changed", equalTo(true)));
        mvc.perform(get("/api/v1/admin/account").with(httpBasic("admin", "friend")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/account").with(httpBasic("admin", newPassword)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mustChangePassword", equalTo(false)));
        mvc.perform(put("/api/v1/admin/settings").with(httpBasic("admin", newPassword))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"organization":"Example Org","language":"de",
                     "timezone":"UTC","accent":"teal"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organization", equalTo("Example Org")));
        mvc.perform(get("/api/v1/settings/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organization", equalTo("Example Org")));
        mvc.perform(get("/api/v1/admin/settings").with(httpBasic("admin", newPassword)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accent", equalTo("teal")));
    }
    @Test @Order(5)
    void addonSettingsArePersistedWithoutAllowingPasswordFields() throws Exception {
        String password = "Changed-Example-Password-2026!";
        mvc.perform(put("/api/v1/admin/addon-settings/Sibyl.ad")
                .with(httpBasic("admin", password)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"serverUrl":"ldaps://ad.example.org:636","pageSize":250}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.settings.serverUrl", equalTo("ldaps://ad.example.org:636")));
        mvc.perform(get("/api/v1/admin/addon-settings/Sibyl.ad")
                .with(httpBasic("admin", password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.settings.pageSize", equalTo(250)));
        mvc.perform(put("/api/v1/admin/addon-settings/Sibyl.ad")
                .with(httpBasic("admin", password)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bindPassword":"never-store-secrets"}
                    """))
            .andExpect(status().isBadRequest());
    }

}
