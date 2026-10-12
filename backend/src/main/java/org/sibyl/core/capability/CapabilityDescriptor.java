package org.sibyl.core.capability;

import java.util.Set;

/**
 * An API contract, NOT a grant of access. The deployment service must validate
 * signed/hashed packages, dependencies and permissions before registering it.
 * No addon code is loaded into Sibyl Core by this descriptor.
 */
public record CapabilityDescriptor(
        String id, String apiVersion, String providerAddonId, Set<String> methods) {

    public CapabilityDescriptor {
        if (id == null || !id.matches("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*){1,5}"))
            throw new IllegalArgumentException("Invalid capability ID");
        if (apiVersion == null || !apiVersion.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))
            throw new IllegalArgumentException("Invalid API version; expected major.minor.patch");
        if (providerAddonId == null || !providerAddonId.matches("Sibyl\\.[a-z][a-z0-9-]{0,62}"))
            throw new IllegalArgumentException("Invalid provider addon ID");
        if (methods == null || methods.isEmpty() || methods.size() > 64)
            throw new IllegalArgumentException("Capability needs 1..64 methods");
        methods = Set.copyOf(methods);
        for (String method : methods)
            if (method == null || !method.matches("[a-z][a-zA-Z0-9]{0,63}"))
                throw new IllegalArgumentException("Invalid method");
    }

    public int majorVersion() {
        return Integer.parseInt(apiVersion.substring(0, apiVersion.indexOf('.')));
    }
}
