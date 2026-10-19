package org.sibyl.core.capability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Capability discovery independent of AD, inventory or any particular addon.
 *
 * Important boundary: this is only an in-memory CONTRACT registry, not an
 * executor. It must never load addon classes, accept user-supplied class names,
 * execute shell commands or forward secrets. Dispatch to isolated workers
 * requires a separately reviewed, authenticated and authorized broker.
 */
@Component
public final class CapabilityRegistry {
    private final Map<String, CapabilityDescriptor> providers = new HashMap<>();

    /** Never overwrite a provider implicitly: upgrades require a controlled transition. */
    public synchronized void register(CapabilityDescriptor descriptor) {
        if (providers.containsKey(descriptor.id()))
            throw new IllegalStateException("Capability already registered: " + descriptor.id());
        providers.put(descriptor.id(), descriptor);
    }

    /** Called on addon deactivation after worker shutdown. */
    public synchronized void unregisterProvider(String addonId) {
        providers.entrySet().removeIf(e -> e.getValue().providerAddonId().equals(addonId));
    }

    /** Reject incompatible major versions instead of guessing a compatible API. */
    public synchronized Optional<CapabilityDescriptor> resolve(String id, int requestedMajor) {
        if (requestedMajor < 0) throw new IllegalArgumentException("Negative API major version");
        CapabilityDescriptor found = providers.get(id);
        return found != null && found.majorVersion() == requestedMajor
                ? Optional.of(found) : Optional.empty();
    }

    public synchronized List<CapabilityDescriptor> snapshot() {
        List<CapabilityDescriptor> list = new ArrayList<>(providers.values());
        list.sort(Comparator.comparing(CapabilityDescriptor::id));
        return List.copyOf(list);
    }
}
