package com.bloxbean.cardano.client.quicktx.filter;

import java.util.Objects;

public final class Order {
    public enum Field {
        LOVELACE,
        AMOUNT_UNIT, // requires unit
        ADDRESS,
        DATA_HASH,
        INLINE_DATUM,
        REFERENCE_SCRIPT_HASH,
        TX_HASH,
        OUTPUT_INDEX
    }

    public enum Direction { ASC, DESC }

    private final Field field;
    private final Direction direction;
    private final String unit; // only for AMOUNT_UNIT

    private Order(Field field, Direction direction, String unit) {
        this.field = Objects.requireNonNull(field, "field");
        this.direction = Objects.requireNonNull(direction, "direction");
        if (field == Field.AMOUNT_UNIT) {
            if (unit == null || unit.isEmpty())
                throw new IllegalArgumentException("unit is required for AMOUNT_UNIT order");
        } else {
            if (unit != null)
                throw new IllegalArgumentException("unit must be null unless field is AMOUNT_UNIT");
        }
        this.unit = unit;
    }

    public static Order lovelace(Direction dir) {
        return new Order(Field.LOVELACE, dir, null);
    }

    public static Order amountUnit(String unit, Direction dir) {
        return new Order(Field.AMOUNT_UNIT, dir, unit);
    }

    public static Order address(Direction dir) { return new Order(Field.ADDRESS, dir, null); }
    public static Order dataHash(Direction dir) { return new Order(Field.DATA_HASH, dir, null); }
    public static Order inlineDatum(Direction dir) { return new Order(Field.INLINE_DATUM, dir, null); }
    public static Order referenceScriptHash(Direction dir) { return new Order(Field.REFERENCE_SCRIPT_HASH, dir, null); }
    public static Order txHash(Direction dir) { return new Order(Field.TX_HASH, dir, null); }
    public static Order outputIndex(Direction dir) { return new Order(Field.OUTPUT_INDEX, dir, null); }

    public Field getField() { return field; }
    public Direction getDirection() { return direction; }
    public String getUnit() { return unit; }
}

