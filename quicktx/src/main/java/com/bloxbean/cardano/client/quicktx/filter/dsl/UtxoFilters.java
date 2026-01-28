package com.bloxbean.cardano.client.quicktx.filter.dsl;

import com.bloxbean.cardano.client.quicktx.filter.Order;
import com.bloxbean.cardano.client.quicktx.filter.UtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.ast.*;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Unified entry point for UTxO Filter DSL.
 *
 * <p>Provides fields, logical operators, ordering, and spec building in a single class
 * for improved developer experience and discoverability.
 *
 * <h2>Basic Usage (Recommended for Most Cases)</h2>
 * <pre>{@code
 * import static UtxoFilters.*;
 *
 * // Simple filter with ordering
 * var spec = filter(
 *     and(
 *         lovelace().gte(2_000_000),
 *         address().eq("addr_test1xyz")
 *     )
 * ).orderBy(lovelaceDesc()).limit(5).build();
 *
 * // Common patterns
 * var spec = minLovelace(2_000_000);
 * var spec = specificUtxo("abc123", 1);
 * var spec = withReferenceScript();
 * }</pre>
 *
 * <h2>Advanced: Custom Filter Nodes</h2>
 * <p>For complex conditions not covered by this DSL, build FilterNode AST directly:
 * <pre>{@code
 * import com.bloxbean.cardano.client.quicktx.filter.ast.*;
 * import static UtxoFilters.*;
 *
 * // Build custom FilterNode
 * FilterNode custom = new Or(Arrays.asList(
 *     new Comparison(AddressField.INSTANCE, CmpOp.EQ, Value.ofString("addr1")),
 *     new Comparison(TxHashField.INSTANCE, CmpOp.EQ, Value.ofString("abc123"))
 * ));
 *
 * // Use with UtxoFilters
 * var spec = filter(custom).orderBy(lovelaceDesc()).build();
 * }</pre>
 *
 * <h2>Alternative: YAML for Complex Filters</h2>
 * <p>For human-readable, config-driven filters, use YAML:
 * <pre>{@code
 * String yaml = """
 *     or:
 *       - and:
 *           - address: "addr1"
 *           - lovelace: { gte: 1000000 }
 *       - referenceScriptHash: { ne: null }
 *     selection:
 *       order: [ "lovelace desc" ]
 *       limit: 5
 *     """;
 * var spec = UtxoFilterYaml.parse(yaml);
 * }</pre>
 *
 * <h2>Available Fields</h2>
 * <ul>
 *   <li><b>String fields:</b> address, dataHash, inlineDatum, referenceScriptHash, txHash
 *       <br>Operators: eq, ne, isNull, notNull</li>
 *   <li><b>Numeric fields:</b> lovelace, amountUnit(unit), outputIndex
 *       <br>Operators: eq, ne, gt, gte, lt, lte</li>
 * </ul>
 *
 * @see FilterNode
 * @see UtxoFilterSpec
 * @see com.bloxbean.cardano.client.quicktx.filter.yaml.UtxoFilterYaml
 * @since 0.8.0
 */
public final class UtxoFilters {
    private UtxoFilters() {}

    // ========== Field Builders ==========

    /**
     * Creates a string field builder for address comparisons.
     *
     * @return field builder for address
     * @see StrField
     */
    public static StrField address() {
        return new StrField(AddressField.INSTANCE);
    }

    /**
     * Creates a string field builder for dataHash comparisons.
     *
     * @return field builder for dataHash
     * @see StrField
     */
    public static StrField dataHash() {
        return new StrField(DataHashField.INSTANCE);
    }

    /**
     * Creates a string field builder for inlineDatum comparisons.
     *
     * @return field builder for inlineDatum
     * @see StrField
     */
    public static StrField inlineDatum() {
        return new StrField(InlineDatumField.INSTANCE);
    }

    /**
     * Creates a string field builder for referenceScriptHash comparisons.
     *
     * @return field builder for referenceScriptHash
     * @see StrField
     */
    public static StrField referenceScriptHash() {
        return new StrField(ReferenceScriptHashField.INSTANCE);
    }

    /**
     * Creates a string field builder for txHash comparisons.
     *
     * @return field builder for txHash
     * @see StrField
     */
    public static StrField txHash() {
        return new StrField(TxHashField.INSTANCE);
    }

    /**
     * Creates a numeric field builder for lovelace amount comparisons.
     *
     * @return field builder for lovelace
     * @see NumField
     */
    public static NumField lovelace() {
        return new NumField(new AmountQuantityField("lovelace"));
    }

    /**
     * Creates a numeric field builder for native asset amount comparisons.
     *
     * @param unit the asset unit (policyId + assetName)
     * @return field builder for the specified asset unit
     * @see NumField
     */
    public static NumField amountUnit(String unit) {
        return new NumField(new AmountQuantityField(unit));
    }

    /**
     * Creates a numeric field builder for outputIndex comparisons.
     *
     * @return field builder for outputIndex
     * @see NumField
     */
    public static NumField outputIndex() {
        return new NumField(OutputIndexField.INSTANCE);
    }

    // ========== Logical Operators ==========

    /**
     * Creates an AND logical node combining multiple filter conditions.
     * All conditions must be satisfied for a UTxO to match.
     *
     * @param nodes the filter conditions to combine
     * @return AND filter node
     */
    public static FilterNode and(FilterNode... nodes) {
        return new And(Arrays.asList(nodes));
    }

    /**
     * Creates an OR logical node combining multiple filter conditions.
     * At least one condition must be satisfied for a UTxO to match.
     *
     * @param nodes the filter conditions to combine
     * @return OR filter node
     */
    public static FilterNode or(FilterNode... nodes) {
        return new Or(Arrays.asList(nodes));
    }

    /**
     * Creates a NOT logical node inverting a filter condition.
     *
     * @param node the filter condition to invert
     * @return NOT filter node
     */
    public static FilterNode not(FilterNode node) {
        return new Not(node);
    }

    // ========== Ordering ==========

    /**
     * Creates an ascending order by lovelace amount.
     * @return order specification
     */
    public static Order lovelaceAsc() {
        return Order.lovelace(Order.Direction.ASC);
    }

    /**
     * Creates a descending order by lovelace amount.
     * @return order specification
     */
    public static Order lovelaceDesc() {
        return Order.lovelace(Order.Direction.DESC);
    }

    /**
     * Creates an ascending order by asset amount.
     * @param unit the asset unit (policyId + assetName)
     * @return order specification
     */
    public static Order amountUnitAsc(String unit) {
        return Order.amountUnit(unit, Order.Direction.ASC);
    }

    /**
     * Creates a descending order by asset amount.
     * @param unit the asset unit (policyId + assetName)
     * @return order specification
     */
    public static Order amountUnitDesc(String unit) {
        return Order.amountUnit(unit, Order.Direction.DESC);
    }

    /**
     * Creates an ascending order by address.
     * @return order specification
     */
    public static Order addressAsc() {
        return Order.address(Order.Direction.ASC);
    }

    /**
     * Creates a descending order by address.
     * @return order specification
     */
    public static Order addressDesc() {
        return Order.address(Order.Direction.DESC);
    }

    /**
     * Creates an ascending order by dataHash.
     * @return order specification
     */
    public static Order dataHashAsc() {
        return Order.dataHash(Order.Direction.ASC);
    }

    /**
     * Creates a descending order by dataHash.
     * @return order specification
     */
    public static Order dataHashDesc() {
        return Order.dataHash(Order.Direction.DESC);
    }

    /**
     * Creates an ascending order by inlineDatum.
     * @return order specification
     */
    public static Order inlineDatumAsc() {
        return Order.inlineDatum(Order.Direction.ASC);
    }

    /**
     * Creates a descending order by inlineDatum.
     * @return order specification
     */
    public static Order inlineDatumDesc() {
        return Order.inlineDatum(Order.Direction.DESC);
    }

    /**
     * Creates an ascending order by referenceScriptHash.
     * @return order specification
     */
    public static Order referenceScriptHashAsc() {
        return Order.referenceScriptHash(Order.Direction.ASC);
    }

    /**
     * Creates a descending order by referenceScriptHash.
     * @return order specification
     */
    public static Order referenceScriptHashDesc() {
        return Order.referenceScriptHash(Order.Direction.DESC);
    }

    /**
     * Creates an ascending order by txHash.
     * @return order specification
     */
    public static Order txHashAsc() {
        return Order.txHash(Order.Direction.ASC);
    }

    /**
     * Creates a descending order by txHash.
     * @return order specification
     */
    public static Order txHashDesc() {
        return Order.txHash(Order.Direction.DESC);
    }

    /**
     * Creates an ascending order by outputIndex.
     * @return order specification
     */
    public static Order outputIndexAsc() {
        return Order.outputIndex(Order.Direction.ASC);
    }

    /**
     * Creates a descending order by outputIndex.
     * @return order specification
     */
    public static Order outputIndexDesc() {
        return Order.outputIndex(Order.Direction.DESC);
    }

    // ========== Spec Building ==========

    /**
     * Creates a filter spec builder from a filter condition.
     *
     * <p>Usage:
     * <pre>{@code
     * var spec = filter(lovelace().gte(2_000_000))
     *     .orderBy(lovelaceDesc())
     *     .limit(5)
     *     .build();
     * }</pre>
     *
     * @param root the root filter condition
     * @return builder for creating the filter spec
     */
    public static Spec.Builder filter(FilterNode root) {
        return Spec.of(root);
    }

    // ========== Common Patterns ==========

    /**
     * Creates a filter spec for minimum lovelace amount.
     * Convenience method for the common pattern of filtering by minimum ADA.
     *
     * <p>Equivalent to: {@code filter(lovelace().gte(amount)).build()}
     *
     * @param amount minimum lovelace amount
     * @return filter spec
     */
    public static UtxoFilterSpec minLovelace(long amount) {
        return filter(lovelace().gte(amount)).build();
    }

    /**
     * Creates a filter spec for a specific UTxO by transaction hash and output index.
     *
     * <p>Equivalent to:
     * <pre>{@code
     * filter(and(txHash().eq(txHash), outputIndex().eq(outputIndex))).build()
     * }</pre>
     *
     * @param txHash the transaction hash
     * @param outputIndex the output index
     * @return filter spec for the specific UTxO
     */
    public static UtxoFilterSpec specificUtxo(String txHash, int outputIndex) {
        return filter(and(
            txHash().eq(txHash),
            outputIndex().eq(outputIndex)
        )).build();
    }

    /**
     * Creates a filter spec for UTxOs with reference scripts.
     * Convenience method for finding UTxOs that have reference scripts attached.
     *
     * <p>Equivalent to: {@code filter(referenceScriptHash().notNull()).build()}
     *
     * @return filter spec for UTxOs with reference scripts
     */
    public static UtxoFilterSpec withReferenceScript() {
        return filter(referenceScriptHash().notNull()).build();
    }

    /**
     * Creates a filter spec for UTxOs at a specific address with minimum lovelace.
     * Common pattern for selecting payment UTxOs from a wallet.
     *
     * @param address the address to filter by
     * @param minLovelace minimum lovelace amount
     * @return filter spec
     */
    public static UtxoFilterSpec addressWithMinLovelace(String address, long minLovelace) {
        return filter(and(
            address().eq(address),
            lovelace().gte(minLovelace)
        )).build();
    }

    // ========== Field Builder Classes ==========

    /**
     * Builder for string field comparisons.
     * Supports equality, inequality, and null checks.
     */
    public static final class StrField {
        private final FieldRef ref;

        private StrField(FieldRef ref) {
            this.ref = ref;
        }

        /**
         * Creates an equality comparison.
         * @param value the value to compare against
         * @return filter node
         */
        public FilterNode eq(String value) {
            return new Comparison(ref, CmpOp.EQ, Value.ofString(value));
        }

        /**
         * Creates an inequality comparison.
         * @param value the value to compare against
         * @return filter node
         */
        public FilterNode ne(String value) {
            return new Comparison(ref, CmpOp.NE, Value.ofString(value));
        }

        /**
         * Creates a null check (field IS NULL).
         * @return filter node
         */
        public FilterNode isNull() {
            return new Comparison(ref, CmpOp.EQ, Value.nullValue());
        }

        /**
         * Creates a not-null check (field IS NOT NULL).
         * @return filter node
         */
        public FilterNode notNull() {
            return new Comparison(ref, CmpOp.NE, Value.nullValue());
        }
    }

    /**
     * Builder for numeric field comparisons.
     * Supports all comparison operators (eq, ne, gt, gte, lt, lte).
     */
    public static final class NumField {
        private final FieldRef ref;

        private NumField(FieldRef ref) {
            this.ref = ref;
        }

        /**
         * Creates an equality comparison.
         * @param v the value to compare against
         * @return filter node
         */
        public FilterNode eq(long v) {
            return new Comparison(ref, CmpOp.EQ, Value.ofInteger(BigInteger.valueOf(v)));
        }

        /**
         * Creates an inequality comparison.
         * @param v the value to compare against
         * @return filter node
         */
        public FilterNode ne(long v) {
            return new Comparison(ref, CmpOp.NE, Value.ofInteger(BigInteger.valueOf(v)));
        }

        /**
         * Creates a greater-than comparison.
         * @param v the value to compare against
         * @return filter node
         */
        public FilterNode gt(long v) {
            return new Comparison(ref, CmpOp.GT, Value.ofInteger(BigInteger.valueOf(v)));
        }

        /**
         * Creates a greater-than-or-equal comparison.
         * @param v the value to compare against
         * @return filter node
         */
        public FilterNode gte(long v) {
            return new Comparison(ref, CmpOp.GTE, Value.ofInteger(BigInteger.valueOf(v)));
        }

        /**
         * Creates a less-than comparison.
         * @param v the value to compare against
         * @return filter node
         */
        public FilterNode lt(long v) {
            return new Comparison(ref, CmpOp.LT, Value.ofInteger(BigInteger.valueOf(v)));
        }

        /**
         * Creates a less-than-or-equal comparison.
         * @param v the value to compare against
         * @return filter node
         */
        public FilterNode lte(long v) {
            return new Comparison(ref, CmpOp.LTE, Value.ofInteger(BigInteger.valueOf(v)));
        }
    }
}
