package org.sibyl.core;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SibylInstallationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private static MockHttpSession adminSession, userSession, secondAdminSession;
    private static final String ADMIN_PASSWORD = "Test-Admin-Password-2026!";
    private static final String USER_PASSWORD = "Test-User-Password-2026!";
    private static final String OTHER_ADMIN_PASSWORD = "Second-Admin-Password-2026!";

    MockHttpSession signIn(String username, String password) throws Exception {
        var result=mvc.perform(post("/login").param("username", username)
            .param("password", password).with(csrf()))
            .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test @Order(1)
    void installationHasOneHashedAdminOnly() {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sibyl_users", Integer.class));
        String hash=jdbc.queryForObject("SELECT password_hash FROM sibyl_users WHERE username='admin'",String.class);
        assertTrue(new BCryptPasswordEncoder().matches("friend",hash));
        assertNotEquals("friend",hash);
        assertEquals("ADMIN",jdbc.queryForObject("SELECT role FROM sibyl_users WHERE username='admin'",String.class));
        assertEquals(Boolean.TRUE,jdbc.queryForObject(
          "SELECT must_change_password FROM sibyl_users WHERE username='admin'",Boolean.class));
    }

    @Test @Order(2)
    void anonymousSeesOnlyLoginNotContentsOrApi() throws Exception {
        mvc.perform(get("/login.html")).andExpect(status().isOk());
        mvc.perform(get("/login.css")).andExpect(status().isOk());
        mvc.perform(get("/login.js")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk());
        for(String p:new String[]{"/", "/index.html", "/admin.html", "/addons.html",
                    "/sibyl.js", "/sibyl.css", "/admin.js", "/addons.js"}) {
            mvc.perform(get(p)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
        }
        for(String p:new String[]{"/api/v1/addons/catalog", "/api/v1/settings/public",
                    "/api/v1/admin/users", "/api/v1/auth/me"}) {
            mvc.perform(get(p)).andExpect(status().isUnauthorized());
        }
    }

    @Test @Order(3)
    void firstLoginCannotEnterApplicationUntilPasswordChanged() throws Exception {
        adminSession=signIn("admin","friend");
        mvc.perform(get("/api/v1/auth/me").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role",equalTo("ADMIN")))
            .andExpect(jsonPath("$.mustChangePassword",equalTo(true)));
        mvc.perform(get("/").session(adminSession)).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login.html?change=1"));
        mvc.perform(get("/api/v1/admin/users").session(adminSession)).andExpect(status().isLocked());
        mvc.perform(post("/api/v1/auth/password").session(adminSession).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"friend\",\"newPassword\":\""+ADMIN_PASSWORD+"\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(adminSession))
            .andExpect(jsonPath("$.mustChangePassword",equalTo(false)));
        mvc.perform(get("/admin.html").session(adminSession)).andExpect(status().isOk());
    }

    @Test @Order(4)
    void onlyAdminCanCreateUserAndAnotherAdmin() throws Exception {
        mvc.perform(post("/api/v1/admin/users").session(adminSession).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"test.user\",\"role\":\"USER\",\"password\":\""+USER_PASSWORD+"\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role",equalTo("USER")));
        mvc.perform(post("/api/v1/admin/users").session(adminSession).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"test.admin\",\"role\":\"ADMIN\",\"password\":\""+OTHER_ADMIN_PASSWORD+"\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role",equalTo("ADMIN")));
        mvc.perform(get("/api/v1/admin/users").session(adminSession))
            .andExpect(status().isOk()).andExpect(jsonPath("$[2].role",equalTo("USER")));
    }

    @Test @Order(5)
    void userMustRotatePasswordAndNeverAccessAdminOrAddonBrowser() throws Exception {
        userSession=signIn("test.user",USER_PASSWORD);
        mvc.perform(get("/").session(userSession))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login.html?change=1"));
        mvc.perform(post("/api/v1/auth/password").session(userSession).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\""+USER_PASSWORD+"\",\"newPassword\":\"User-Replaced-Password-2026!\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/").session(userSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(userSession))
            .andExpect(jsonPath("$.role",equalTo("USER")));
        for(String p:new String[]{"/admin.html", "/addons.html", "/admin.js",
                 "/addons.js", "/api/v1/addons/catalog", "/api/v1/admin/users",
                 "/api/v1/admin/addon-settings/Sibyl.ad"}) {
            mvc.perform(get(p).session(userSession)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/admin/users").session(userSession).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/settings/public").session(userSession)).andExpect(status().isOk());
    }

    @Test @Order(6)
    void secondAdministratorCanSeeAddonAndCreateUsersAfterRotation() throws Exception {
        secondAdminSession=signIn("test.admin",OTHER_ADMIN_PASSWORD);
        mvc.perform(get("/api/v1/addons/catalog?channel=prerelease").session(secondAdminSession))
            .andExpect(status().isLocked());
        mvc.perform(post("/api/v1/auth/password").session(secondAdminSession).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\""+OTHER_ADMIN_PASSWORD+"\",\"newPassword\":\"Changed-Second-Admin-2026!\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/addons.html").session(secondAdminSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/addons/catalog?channel=prerelease").session(secondAdminSession))
            .andExpect(status().isOk()).andExpect(jsonPath("$.addons").isArray());
    }

    @Test @Order(7)
    void mysqlSettingsPersistAndSecretsAreRejected() throws Exception {
        mvc.perform(put("/api/v1/admin/settings").session(adminSession).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"organization\":\"Example\",\"language\":\"de\",\"timezone\":\"UTC\",\"accent\":\"teal\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.organization",equalTo("Example")));
        mvc.perform(put("/api/v1/admin/addon-settings/Sibyl.ad").session(adminSession).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"serverUrl\":\"ldaps://ad.example.org:636\"}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/addon-settings/Sibyl.ad").session(adminSession).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bindPassword\":\"must-not-save\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test @Order(8)
    void logoutInvalidatesSession() throws Exception {
        mvc.perform(post("/logout").session(userSession).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/").session(userSession))
            .andExpect(status().is3xxRedirection());
    }
}
