package com.bloxbean.cardano.client.quicktx.filter.ast;

import java.util.Objects;

public final class AmountQuantityField implements FieldRef {
    private final String unit; // e.g., "lovelace" or policyId+assetNameHex

    public AmountQuantityField(String unit) {
        if (unit == null || unit.isEmpty())
            throw new IllegalArgumentException("unit must not be null or empty");
        this.unit = unit;
    }

    public String getUnit() {
        return unit;
    }

    @Override
    public <T> T accept(FieldRefVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AmountQuantityField)) return false;
        AmountQuantityField that = (AmountQuantityField) o;
        return unit.equals(that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit);
    }

    @Override
    public String toString() {
        return "AmountQuantityField{" + unit + '}';
    }
}

