package org.sibyl.core.capability;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityRegistryTest {
    private static CapabilityDescriptor identity() {
        return new CapabilityDescriptor("identity.users", "1.2.0",
                "Sibyl.ad", Set.of("findUsers", "lookupUser"));
    }

    @Test void exposesOnlyMatchingMajorVersion() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(identity());
        assertEquals("Sibyl.ad", registry.resolve("identity.users", 1)
                .orElseThrow().providerAddonId());
        assertTrue(registry.resolve("identity.users", 2).isEmpty());
        assertTrue(registry.resolve("inventory.devices", 1).isEmpty());
    }

    @Test void neverSilentlyReplacesAProvider() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(identity());
        assertThrows(IllegalStateException.class, () -> registry.register(identity()));
        assertThrows(IllegalStateException.class, () -> registry.register(
                new CapabilityDescriptor("identity.users", "1.0.0",
                        "Sibyl.directory", Set.of("findUsers"))));
    }

    @Test void deactivationOnlyRemovesTheOwningProviders() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(identity());
        registry.register(new CapabilityDescriptor("inventory.devices", "1.0.0",
                "Sibyl.inventory", Set.of("listDevices")));
        registry.unregisterProvider("Sibyl.ad");
        assertTrue(registry.resolve("identity.users", 1).isEmpty());
        assertEquals(1, registry.snapshot().size());
    }

    @Test void validatesManifestMetadataAndIsImmutable() {
        assertThrows(IllegalArgumentException.class, () -> new CapabilityDescriptor(
                "../internal", "1.0.0", "Sibyl.ad", Set.of("read")));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityDescriptor(
                "identity.users", "anything", "Sibyl.ad", Set.of("read")));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityDescriptor(
                "identity.users", "1.0.0", "other", Set.of("read")));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityDescriptor(
                "identity.users", "1.0.0", "Sibyl.ad", Set.of()));
        assertThrows(UnsupportedOperationException.class, () -> identity().methods().add("write"));
    }
}
