package org.sibyl.core;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Only ADMIN can provision new accounts. No anonymous self-registration. */
@RestController
@RequestMapping("/api/v1/admin/users")
public class SibylUserAdminController {
    private final SibylAccounts accounts;
    public SibylUserAdminController(SibylAccounts accounts) { this.accounts = accounts; }

    @GetMapping
    public List<SibylAccounts.AccountInfo> list() { return accounts.listAccounts(); }

    public record CreateUser(String username, String password, String role) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SibylAccounts.AccountInfo create(Principal principal, @RequestBody CreateUser data) {
        if (data == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try { return accounts.createAccount(principal.getName(),
                    data.username(), data.password(), data.role()); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid username, password or role");
        }
        catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
    }
}
