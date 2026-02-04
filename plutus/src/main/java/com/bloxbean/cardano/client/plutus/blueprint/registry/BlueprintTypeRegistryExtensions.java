package com.bloxbean.cardano.client.plutus.blueprint.registry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows applications to register additional mappings without subclassing the default registry.
 */
public final class BlueprintTypeRegistryExtensions {

    private static final Map<String, RegisteredType> TITLE_OVERRIDES = new ConcurrentHashMap<>();

    private BlueprintTypeRegistryExtensions() {
    }

    public static void registerByTitle(String title, RegisteredType type) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("title cannot be null or blank");
        TITLE_OVERRIDES.put(title, type);
    }

    public static void registerByTitle(String title, String packageName, String simpleName) {
        registerByTitle(title, new RegisteredType(packageName, simpleName));
    }

    public static Optional<RegisteredType> findByTitle(String title) {
        return Optional.ofNullable(TITLE_OVERRIDES.get(title));
    }
}
