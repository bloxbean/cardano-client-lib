package com.bloxbean.cardano.client.quicktx.filter.ast;

/**
 * Field reference for UTXO transaction hash.
 * This is a string field supporting EQ/NE operations only.
 *
 * <p>The transaction hash uniquely identifies the transaction that created this UTXO.
 * Combined with outputIndex, it forms a unique identifier for any UTXO.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Select specific transaction's outputs
 * FilterNode filter = new Comparison(
 *     TxHashField.INSTANCE,
 *     CmpOp.EQ,
 *     Value.ofString("abc123...")
 * );
 *
 * // Exclude specific transaction
 * FilterNode filter = new Comparison(
 *     TxHashField.INSTANCE,
 *     CmpOp.NE,
 *     Value.ofString("def456...")
 * );
 * }</pre>
 */
public final class TxHashField implements FieldRef {
    /**
     * Singleton instance.
     */
    public static final TxHashField INSTANCE = new TxHashField();

    private TxHashField() {
        // Singleton - prevent instantiation
    }

    @Override
    public <T> T accept(FieldRefVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return "TxHashField";
    }
}
