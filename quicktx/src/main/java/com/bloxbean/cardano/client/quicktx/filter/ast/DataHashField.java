package com.bloxbean.cardano.client.quicktx.filter.ast;

import java.util.Objects;

public final class DataHashField implements FieldRef {
    public static final DataHashField INSTANCE = new DataHashField();

    private DataHashField() {}

    @Override
    public <T> T accept(FieldRefVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DataHashField;
    }

    @Override
    public int hashCode() {
        return Objects.hash("DataHashField");
    }

    @Override
    public String toString() {
        return "DataHashField";
    }
}

