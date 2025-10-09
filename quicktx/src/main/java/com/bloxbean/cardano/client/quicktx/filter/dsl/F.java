package com.bloxbean.cardano.client.quicktx.filter.dsl;

import com.bloxbean.cardano.client.quicktx.filter.ast.*;

import java.math.BigInteger;

/**
 * Static helpers to build typed comparison nodes concisely.
 */
public final class F {
    private F() {}

    // -------- String fields --------
    public static StrField dataHash() { return new StrField(DataHashField.INSTANCE); }
    public static StrField inlineDatum() { return new StrField(InlineDatumField.INSTANCE); }
    public static StrField address() { return new StrField(AddressField.INSTANCE); }
    public static StrField referenceScriptHash() { return new StrField(ReferenceScriptHashField.INSTANCE); }
    public static StrField txHash() { return new StrField(TxHashField.INSTANCE); }

    // -------- Numeric fields --------
    public static NumField lovelace() { return new NumField(new AmountQuantityField("lovelace")); }
    public static NumField amountUnit(String unit) { return new NumField(new AmountQuantityField(unit)); }
    public static NumField outputIndex() { return new NumField(OutputIndexField.INSTANCE); }

    // -------- Builders --------
    public static final class StrField {
        private final FieldRef ref;
        private StrField(FieldRef ref) { this.ref = ref; }

        public FilterNode eq(String value) { return new Comparison(ref, CmpOp.EQ, Value.ofString(value)); }
        public FilterNode ne(String value) { return new Comparison(ref, CmpOp.NE, Value.ofString(value)); }
        public FilterNode isNull() { return new Comparison(ref, CmpOp.EQ, Value.nullValue()); }
        public FilterNode notNull() { return new Comparison(ref, CmpOp.NE, Value.nullValue()); }
    }

    public static final class NumField {
        private final FieldRef ref;
        private NumField(FieldRef ref) { this.ref = ref; }

        public FilterNode eq(long v) { return new Comparison(ref, CmpOp.EQ, Value.ofInteger(BigInteger.valueOf(v))); }
        public FilterNode ne(long v) { return new Comparison(ref, CmpOp.NE, Value.ofInteger(BigInteger.valueOf(v))); }
        public FilterNode gt(long v) { return new Comparison(ref, CmpOp.GT, Value.ofInteger(BigInteger.valueOf(v))); }
        public FilterNode gte(long v) { return new Comparison(ref, CmpOp.GTE, Value.ofInteger(BigInteger.valueOf(v))); }
        public FilterNode lt(long v) { return new Comparison(ref, CmpOp.LT, Value.ofInteger(BigInteger.valueOf(v))); }
        public FilterNode lte(long v) { return new Comparison(ref, CmpOp.LTE, Value.ofInteger(BigInteger.valueOf(v))); }
    }
}

