package org.sibyl.core.capability;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only admin introspection. Existing /api/v1/admin/** security requires ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/capabilities")
public final class CapabilityAdminController {
    private final CapabilityRegistry registry;

    public CapabilityAdminController(CapabilityRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<CapabilityDescriptor> list() {
        return registry.snapshot();
    }
}
