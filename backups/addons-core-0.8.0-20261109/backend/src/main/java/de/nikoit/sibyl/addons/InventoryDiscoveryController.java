package de.nikoit.sibyl.addons;

import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Read-only inventory discovery endpoints protected by /api/v1/admin/** RBAC. */
@RestController
@RequestMapping("/api/v1/admin/inventory")
public class InventoryDiscoveryController {
    private final InventoryDiscoveryService inventory;
    private final AddonRegistry addons;
    public InventoryDiscoveryController(InventoryDiscoveryService inventory,AddonRegistry addons) {
        this.inventory=inventory;this.addons=addons;
    }
    @GetMapping("/discovered")
    public List<InventoryDiscoveryService.Asset> all(org.springframework.security.core.Authentication auth) {
        addons.accessible("Sibyl.inventory",auth);
        return inventory.list();
    }
    @GetMapping("/status")
    public InventoryDiscoveryService.Status status(org.springframework.security.core.Authentication auth) {
        addons.accessible("Sibyl.inventory",auth);
        return inventory.status();
    }
    @PostMapping("/refresh")
    public InventoryDiscoveryService.Status refresh(org.springframework.security.core.Authentication auth) {
        addons.accessible("Sibyl.inventory",auth);
        return inventory.refresh();
    }
}
