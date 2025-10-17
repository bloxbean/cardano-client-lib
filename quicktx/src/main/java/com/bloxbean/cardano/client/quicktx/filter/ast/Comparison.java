package com.bloxbean.cardano.client.quicktx.filter.ast;

import java.util.Objects;

/**
 * Represents a comparison operation between a field and a value.
 * This is a leaf node in the filter AST that performs the actual filtering logic.
 *
 * <p>A comparison consists of:
 * <ul>
 *   <li>Field: What UTxO property to compare (address, amount, etc.)</li>
 *   <li>Operator: How to compare (EQ, NE, GT, GTE, LT, LTE)</li>
 *   <li>Value: What to compare against (string, number, or null)</li>
 * </ul>
 *
 * <p>Type constraints are enforced by the visitor implementations:
 * <ul>
 *   <li>String fields only support EQ/NE operations</li>
 *   <li>Numeric fields support all comparison operations</li>
 * </ul>
 *
 * @see FieldRef
 * @see CmpOp
 * @see Value
 */
public final class Comparison implements FilterNode {
    private final FieldRef field;
    private final CmpOp op;
    private final Value value;

    /**
     * Creates a new comparison with the specified field, operator, and value.
     *
     * @param field the field to compare (must not be null)
     * @param op the comparison operator (must not be null)
     * @param value the value to compare against (must not be null)
     * @throws IllegalArgumentException if any parameter is null
     */
    public Comparison(FieldRef field, CmpOp op, Value value) {
        if (field == null) throw new IllegalArgumentException("field cannot be null");
        if (op == null) throw new IllegalArgumentException("op cannot be null");
        if (value == null) throw new IllegalArgumentException("value cannot be null");
        this.field = field;
        this.op = op;
        this.value = value;
    }

    public FieldRef getField() {
        return field;
    }

    public CmpOp getOp() {
        return op;
    }

    public Value getValue() {
        return value;
    }

    @Override
    public <T> T accept(FilterVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Comparison)) return false;
        Comparison that = (Comparison) o;
        return Objects.equals(field, that.field) && op == that.op && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, op, value);
    }

    @Override
    public String toString() {
        return "Comparison{" + field + " " + op + " " + value + "}";
    }
}

