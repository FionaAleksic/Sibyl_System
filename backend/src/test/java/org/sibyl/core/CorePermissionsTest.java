package org.sibyl.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CorePermissionsTest {
    @Test void rolesDoNotInheritAdminPermissions() {
        assertTrue(CorePermissions.forRole("ADMIN").contains("audit.read"));
        assertEquals(java.util.List.of("audit.read"), CorePermissions.forRole("AUDITOR"));
        assertTrue(CorePermissions.forRole("USER").isEmpty());
        assertFalse(CorePermissions.forRole("AUDITOR").contains("addons.manage"));
        assertThrows(IllegalArgumentException.class, () -> CorePermissions.forRole("SUPERUSER"));
    }
    @Test void grantedAuthoritiesPreserveRoleAndExplicitPermission() {
        var names = CorePermissions.authorities("AUDITOR").stream()
            .map(a -> a.getAuthority()).toList();
        assertTrue(names.contains("ROLE_AUDITOR"));
        assertTrue(names.contains("audit.read"));
        assertFalse(names.contains("ROLE_ADMIN"));
    }
}
