package com.bloxbean.cardano.client.quicktx.filter.ast;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Represents a typed value in filter comparisons.
 * Values can be strings, integers (BigInteger), or null.
 *
 * <p>This class provides type safety by encoding the value type
 * in the Kind enum and providing type-specific accessor methods.
 *
 * <p>Usage examples:
 * <pre>{@code
 * Value stringVal = Value.ofString("addr_test1xyz");
 * Value intVal = Value.ofInteger(BigInteger.valueOf(1000000));
 * Value nullVal = Value.nullValue();
 * }</pre>
 *
 * @see Comparison
 */
public final class Value {
    public enum Kind { STRING, INTEGER, NULL }

    private final Kind kind;
    private final String stringValue;
    private final BigInteger integerValue;

    private Value(Kind kind, String stringValue, BigInteger integerValue) {
        this.kind = kind;
        this.stringValue = stringValue;
        this.integerValue = integerValue;
    }

    /**
     * Creates a string value.
     *
     * @param s the string value (must not be null; use nullValue() for null)
     * @return a Value containing the string
     * @throws IllegalArgumentException if s is null
     */
    public static Value ofString(String s) {
        if (s == null) throw new IllegalArgumentException("string value cannot be null; use nullValue()");
        return new Value(Kind.STRING, s, null);
    }

    /**
     * Creates an integer value.
     *
     * @param i the BigInteger value (must not be null)
     * @return a Value containing the integer
     * @throws IllegalArgumentException if i is null
     */
    public static Value ofInteger(BigInteger i) {
        if (i == null) throw new IllegalArgumentException("integer value cannot be null");
        return new Value(Kind.INTEGER, null, i);
    }

    /**
     * Creates a null value for comparisons.
     *
     * @return a Value representing null
     */
    public static Value nullValue() {
        return new Value(Kind.NULL, null, null);
    }

    public Kind getKind() {
        return kind;
    }

    public String asString() {
        if (kind != Kind.STRING) throw new IllegalStateException("Not a STRING value");
        return stringValue;
    }

    public BigInteger asInteger() {
        if (kind != Kind.INTEGER) throw new IllegalStateException("Not an INTEGER value");
        return integerValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Value)) return false;
        Value value = (Value) o;
        return kind == value.kind && Objects.equals(stringValue, value.stringValue) && Objects.equals(integerValue, value.integerValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, stringValue, integerValue);
    }

    @Override
    public String toString() {
        switch (kind) {
            case STRING: return '"' + stringValue + '"';
            case INTEGER: return String.valueOf(integerValue);
            case NULL: default: return "null";
        }
    }
}

