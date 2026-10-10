package org.sibyl.core;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** A short-lived HTTP-only session cookie is issued by Spring Security at login. */
@RestController
@RequestMapping("/api/v1/auth")
public class SibylAuthController {
    private final SibylAccounts accounts;
    public SibylAuthController(SibylAccounts accounts) { this.accounts = accounts; }

    @GetMapping("/csrf")
    public Map<String,String> csrf(CsrfToken token) {
        return Map.of("header",token.getHeaderName(), "token",token.getToken());
    }

    @GetMapping("/me")
    public Map<String,Object> me(Principal principal) {
        return accounts.account(principal.getName());
    }

    public record PasswordChange(String currentPassword, String newPassword) {}

    @PostMapping("/password")
    public Map<String,Object> change(Principal principal, @RequestBody PasswordChange body) {
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try {
            accounts.changePassword(principal.getName(),body.currentPassword(),body.newPassword());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Current password or new password invalid");
        }
        return Map.of("changed",true,"signInAgain",true);
    }
}
