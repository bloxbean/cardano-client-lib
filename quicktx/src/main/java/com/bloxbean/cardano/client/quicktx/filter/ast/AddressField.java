package com.bloxbean.cardano.client.quicktx.filter.ast;

import java.util.Objects;

public final class AddressField implements FieldRef {
    public static final AddressField INSTANCE = new AddressField();

    private AddressField() {}

    @Override
    public <T> T accept(FieldRefVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AddressField; // singleton semantics
    }

    @Override
    public int hashCode() {
        return Objects.hash("AddressField");
    }

    @Override
    public String toString() {
        return "AddressField";
    }
}

