package com.bloxbean.cardano.client.quicktx.filter.ast;

import java.util.Objects;

public final class InlineDatumField implements FieldRef {
    public static final InlineDatumField INSTANCE = new InlineDatumField();

    private InlineDatumField() {}

    @Override
    public <T> T accept(FieldRefVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof InlineDatumField;
    }

    @Override
    public int hashCode() {
        return Objects.hash("InlineDatumField");
    }

    @Override
    public String toString() {
        return "InlineDatumField";
    }
}

