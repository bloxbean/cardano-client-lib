package com.bloxbean.cardano.client.plutus.blueprint.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides contextual information about the schema lookup (namespace, blueprint identifier, hints, etc.).
 */
public final class LookupContext {

    public static final LookupContext EMPTY = new LookupContext(null, null, Collections.emptyMap());

    private final String namespace;
    private final String blueprintName;
    private final Map<String, String> hints;

    public LookupContext(String namespace, String blueprintName) {
        this(namespace, blueprintName, Collections.emptyMap());
    }

    public LookupContext(String namespace, String blueprintName, Map<String, String> hints) {
        this.namespace = namespace;
        this.blueprintName = blueprintName;
        this.hints = hints != null ? Collections.unmodifiableMap(new HashMap<>(hints)) : Collections.emptyMap();
    }

    public String namespace() {
        return namespace;
    }

    public String blueprintName() {
        return blueprintName;
    }

    /**
     * Returns the unmodifiable hints map.
     */
    public Map<String, String> hints() {
        return hints;
    }

    /**
     * Retrieves a single hint value by key.
     */
    public Optional<String> hint(String key) {
        return Optional.ofNullable(hints.get(key));
    }

    public LookupContext withNamespace(String ns) {
        return new LookupContext(ns, blueprintName, hints);
    }

    public LookupContext withBlueprintName(String name) {
        return new LookupContext(namespace, name, hints);
    }

    public LookupContext withHint(String key, String value) {
        Map<String, String> newHints = new HashMap<>(hints);
        newHints.put(key, value);
        return new LookupContext(namespace, blueprintName, newHints);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LookupContext)) return false;
        LookupContext that = (LookupContext) o;
        return Objects.equals(namespace, that.namespace)
                && Objects.equals(blueprintName, that.blueprintName)
                && Objects.equals(hints, that.hints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, blueprintName, hints);
    }

    @Override
    public String toString() {
        return "LookupContext{" +
                "namespace='" + namespace + '\'' +
                ", blueprintName='" + blueprintName + '\'' +
                ", hints=" + hints +
                '}';
    }
}
