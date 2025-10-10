package com.bloxbean.cardano.client.quicktx.signing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Composite registry that queries multiple registries in order.
 */
public class SignerRegistryChain implements SignerRegistry {

    private final List<SignerRegistry> registries = new ArrayList<>();

    public SignerRegistryChain() {}

    public SignerRegistryChain(List<SignerRegistry> registries) {
        if (registries != null) this.registries.addAll(registries);
    }

    public SignerRegistryChain add(SignerRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registries.add(registry);
        return this;
    }

    @Override
    public Optional<SignerBinding> resolve(String ref) {
        for (SignerRegistry registry : registries) {
            Optional<SignerBinding> binding = registry.resolve(ref);
            if (binding.isPresent()) return binding;
        }
        return Optional.empty();
    }
}

