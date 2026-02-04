package com.bloxbean.cardano.client.plutus.blueprint.registry;

import java.util.Objects;

/**
 * Immutable value object representing the canonical signature of a {@link com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema}.
 */
public final class SchemaSignature {

    private final String value;

    private SchemaSignature(String value) {
        this.value = Objects.requireNonNull(value, "value cannot be null");
    }

    public static SchemaSignature of(String value) {
        return new SchemaSignature(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemaSignature)) return false;
        SchemaSignature that = (SchemaSignature) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "SchemaSignature{" + value + '}';
    }
}
