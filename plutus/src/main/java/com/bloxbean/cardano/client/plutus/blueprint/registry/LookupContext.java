package com.bloxbean.cardano.client.plutus.blueprint.registry;

import java.util.Objects;

/**
 * Provides contextual information about the schema lookup (namespace, blueprint identifier, etc.).
 */
public final class LookupContext {

    public static final LookupContext EMPTY = new LookupContext(null, null);

    private final String namespace;
    private final String blueprintName;

    public LookupContext(String namespace, String blueprintName) {
        this.namespace = namespace;
        this.blueprintName = blueprintName;
    }

    public String namespace() {
        return namespace;
    }

    public String blueprintName() {
        return blueprintName;
    }

    public LookupContext withNamespace(String ns) {
        return new LookupContext(ns, blueprintName);
    }

    public LookupContext withBlueprintName(String name) {
        return new LookupContext(namespace, name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LookupContext)) return false;
        LookupContext that = (LookupContext) o;
        return Objects.equals(namespace, that.namespace) && Objects.equals(blueprintName, that.blueprintName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, blueprintName);
    }

    @Override
    public String toString() {
        return "LookupContext{" +
                "namespace='" + namespace + '\'' +
                ", blueprintName='" + blueprintName + '\'' +
                '}';
    }
}
