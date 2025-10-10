package com.bloxbean.cardano.client.quicktx.filter.ast;

/**
 * Field reference for UTXO output index.
 * This is a numeric field supporting all comparison operations (EQ, NE, GT, GTE, LT, LTE).
 *
 * <p>The output index identifies a specific output within a transaction.
 * Output indices start at 0 and increment sequentially.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Select specific output index
 * FilterNode filter = new Comparison(
 *     OutputIndexField.INSTANCE,
 *     CmpOp.EQ,
 *     Value.ofInteger(BigInteger.ZERO)
 * );
 *
 * // Select first 5 outputs
 * FilterNode filter = new Comparison(
 *     OutputIndexField.INSTANCE,
 *     CmpOp.LT,
 *     Value.ofInteger(BigInteger.valueOf(5))
 * );
 *
 * // Combined with txHash for unique UTXO identification
 * FilterNode filter = new And(Arrays.asList(
 *     new Comparison(TxHashField.INSTANCE, CmpOp.EQ, Value.ofString("abc123...")),
 *     new Comparison(OutputIndexField.INSTANCE, CmpOp.EQ, Value.ofInteger(BigInteger.ZERO))
 * ));
 * }</pre>
 */
public final class OutputIndexField implements FieldRef {
    /**
     * Singleton instance.
     */
    public static final OutputIndexField INSTANCE = new OutputIndexField();

    private OutputIndexField() {
        // Singleton - prevent instantiation
    }

    @Override
    public <T> T accept(FieldRefVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return "OutputIndexField";
    }
}
