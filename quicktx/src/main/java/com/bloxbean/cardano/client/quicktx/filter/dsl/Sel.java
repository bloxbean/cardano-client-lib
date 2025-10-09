package com.bloxbean.cardano.client.quicktx.filter.dsl;

import com.bloxbean.cardano.client.quicktx.filter.Order;

/**
 * Selection helpers for ordering.
 */
public final class Sel {
    private Sel() {}

    public static Order lovelaceAsc() { return Order.lovelace(Order.Direction.ASC); }
    public static Order lovelaceDesc() { return Order.lovelace(Order.Direction.DESC); }
    public static Order amountUnitAsc(String unit) { return Order.amountUnit(unit, Order.Direction.ASC); }
    public static Order amountUnitDesc(String unit) { return Order.amountUnit(unit, Order.Direction.DESC); }
    public static Order addressAsc() { return Order.address(Order.Direction.ASC); }
    public static Order addressDesc() { return Order.address(Order.Direction.DESC); }
    public static Order dataHashAsc() { return Order.dataHash(Order.Direction.ASC); }
    public static Order dataHashDesc() { return Order.dataHash(Order.Direction.DESC); }
    public static Order inlineDatumAsc() { return Order.inlineDatum(Order.Direction.ASC); }
    public static Order inlineDatumDesc() { return Order.inlineDatum(Order.Direction.DESC); }
    public static Order referenceScriptHashAsc() { return Order.referenceScriptHash(Order.Direction.ASC); }
    public static Order referenceScriptHashDesc() { return Order.referenceScriptHash(Order.Direction.DESC); }
    public static Order txHashAsc() { return Order.txHash(Order.Direction.ASC); }
    public static Order txHashDesc() { return Order.txHash(Order.Direction.DESC); }
    public static Order outputIndexAsc() { return Order.outputIndex(Order.Direction.ASC); }
    public static Order outputIndexDesc() { return Order.outputIndex(Order.Direction.DESC); }
}

