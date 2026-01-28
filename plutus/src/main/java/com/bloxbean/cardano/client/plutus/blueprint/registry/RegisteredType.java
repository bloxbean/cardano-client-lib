package com.bloxbean.cardano.client.plutus.blueprint.registry;

import java.util.Objects;

/**
 * Describes a Java type that can represent a blueprint schema.
 */
public final class RegisteredType {

    private final String packageName;
    private final String simpleName;

    public RegisteredType(String packageName, String simpleName) {
        this.packageName = Objects.requireNonNull(packageName, "packageName cannot be null");
        this.simpleName = Objects.requireNonNull(simpleName, "simpleName cannot be null");
    }

    public String packageName() {
        return packageName;
    }

    public String simpleName() {
        return simpleName;
    }

    public String canonicalName() {
        if (packageName.isEmpty())
            return simpleName;
        return packageName + '.' + simpleName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegisteredType)) return false;
        RegisteredType that = (RegisteredType) o;
        return packageName.equals(that.packageName) && simpleName.equals(that.simpleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, simpleName);
    }

    @Override
    public String toString() {
        return canonicalName();
    }
}
