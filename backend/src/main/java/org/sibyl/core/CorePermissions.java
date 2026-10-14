package org.sibyl.core;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Conservative core permission definitions.
 * Roles are resolved server-side and never accepted from request headers.
 * Third-party addon scopes must use a separate permission broker.
 */
public final class CorePermissions {
    private CorePermissions() {}

    public static List<String> forRole(String role) {
        return switch (role) {
            case "ADMIN" -> List.of("audit.read", "accounts.manage", "settings.manage", "addons.manage",
                "inventory.read", "networks.manage", "alerts.manage");
            case "SECURITY_OPERATOR" -> List.of("inventory.read", "networks.manage", "alerts.manage");
            case "AUDITOR" -> List.of("audit.read");
            case "USER" -> List.of();
            default -> throw new IllegalArgumentException("Unrecognized account role");
        };
    }

    public static List<GrantedAuthority> authorities(String role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String permission : forRole(role)) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        return List.copyOf(authorities);
    }
}
