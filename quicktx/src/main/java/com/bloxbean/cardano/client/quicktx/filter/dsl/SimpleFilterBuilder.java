package com.bloxbean.cardano.client.quicktx.filter.dsl;

import com.bloxbean.cardano.client.quicktx.filter.ImmutableUtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.Order;
import com.bloxbean.cardano.client.quicktx.filter.Selection;
import com.bloxbean.cardano.client.quicktx.filter.UtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.ast.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A lightweight, fluent builder for common UTxO filter cases.
 *
 * Defaults:
 * - Implicit AND across added conditions
 * - No limit (ALL) unless set
 * - No ordering unless set
 */
public final class SimpleFilterBuilder {
    private final List<FilterNode> terms = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private Integer limit; // null means ALL

    // ---- Conditions (strings) ----
    public SimpleFilterBuilder dataHashEq(String hex) {
        terms.add(new Comparison(DataHashField.INSTANCE, CmpOp.EQ, Value.ofString(hex)));
        return this;
    }

    public SimpleFilterBuilder dataHashNe(String hex) {
        terms.add(new Comparison(DataHashField.INSTANCE, CmpOp.NE, Value.ofString(hex)));
        return this;
    }

    public SimpleFilterBuilder dataHashIsNull() {
        terms.add(new Comparison(DataHashField.INSTANCE, CmpOp.EQ, Value.nullValue()));
        return this;
    }

    public SimpleFilterBuilder dataHashNotNull() {
        terms.add(new Comparison(DataHashField.INSTANCE, CmpOp.NE, Value.nullValue()));
        return this;
    }

    public SimpleFilterBuilder inlineDatumEq(String hex) {
        terms.add(new Comparison(InlineDatumField.INSTANCE, CmpOp.EQ, Value.ofString(hex)));
        return this;
    }

    public SimpleFilterBuilder inlineDatumIsNull() {
        terms.add(new Comparison(InlineDatumField.INSTANCE, CmpOp.EQ, Value.nullValue()));
        return this;
    }

    public SimpleFilterBuilder inlineDatumNotNull() {
        terms.add(new Comparison(InlineDatumField.INSTANCE, CmpOp.NE, Value.nullValue()));
        return this;
    }

    public SimpleFilterBuilder addressEq(String address) {
        terms.add(new Comparison(AddressField.INSTANCE, CmpOp.EQ, Value.ofString(address)));
        return this;
    }

    // ---- Conditions (lovelace numeric) ----
    public SimpleFilterBuilder lovelaceEq(long v) { return lovelace(CmpOp.EQ, v); }
    public SimpleFilterBuilder lovelaceNe(long v) { return lovelace(CmpOp.NE, v); }
    public SimpleFilterBuilder lovelaceGt(long v) { return lovelace(CmpOp.GT, v); }
    public SimpleFilterBuilder lovelaceGte(long v) { return lovelace(CmpOp.GTE, v); }
    public SimpleFilterBuilder lovelaceLt(long v) { return lovelace(CmpOp.LT, v); }
    public SimpleFilterBuilder lovelaceLte(long v) { return lovelace(CmpOp.LTE, v); }

    private SimpleFilterBuilder lovelace(CmpOp op, long v) {
        terms.add(new Comparison(new AmountQuantityField("lovelace"), op, Value.ofInteger(BigInteger.valueOf(v))));
        return this;
    }

    // ---- Conditions (asset quantity by unit) ----
    public SimpleFilterBuilder amountUnitEq(String unit, long v) { return amountUnit(CmpOp.EQ, unit, v); }
    public SimpleFilterBuilder amountUnitNe(String unit, long v) { return amountUnit(CmpOp.NE, unit, v); }
    public SimpleFilterBuilder amountUnitGt(String unit, long v) { return amountUnit(CmpOp.GT, unit, v); }
    public SimpleFilterBuilder amountUnitGte(String unit, long v) { return amountUnit(CmpOp.GTE, unit, v); }
    public SimpleFilterBuilder amountUnitLt(String unit, long v) { return amountUnit(CmpOp.LT, unit, v); }
    public SimpleFilterBuilder amountUnitLte(String unit, long v) { return amountUnit(CmpOp.LTE, unit, v); }

    private SimpleFilterBuilder amountUnit(CmpOp op, String unit, long v) {
        terms.add(new Comparison(new AmountQuantityField(unit), op, Value.ofInteger(BigInteger.valueOf(v))));
        return this;
    }

    // ---- Ordering ----
    public SimpleFilterBuilder orderByLovelaceAsc() { orders.add(Order.lovelace(Order.Direction.ASC)); return this; }
    public SimpleFilterBuilder orderByLovelaceDesc() { orders.add(Order.lovelace(Order.Direction.DESC)); return this; }
    public SimpleFilterBuilder orderByAmountUnitAsc(String unit) { orders.add(Order.amountUnit(unit, Order.Direction.ASC)); return this; }
    public SimpleFilterBuilder orderByAmountUnitDesc(String unit) { orders.add(Order.amountUnit(unit, Order.Direction.DESC)); return this; }

    // ---- Limit ----
    public SimpleFilterBuilder limit(int n) {
        if (n < 0) throw new IllegalArgumentException("limit must be >= 0");
        this.limit = n; return this;
    }
    public SimpleFilterBuilder limitAll() { this.limit = null; return this; }

    // ---- Build ----
    public UtxoFilterSpec build() {
        FilterNode root;
        if (terms.isEmpty()) {
            root = new And(Collections.emptyList()); // match all
        } else if (terms.size() == 1) {
            root = terms.get(0);
        } else {
            root = new And(terms);
        }
        Selection sel = Selection.of(orders, limit);
        return ImmutableUtxoFilterSpec.builder(root).selection(sel).build();
    }
}

