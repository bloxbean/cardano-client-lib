package com.bloxbean.cardano.client.quicktx.filter.ast;

/**
 * Field reference for UTXO reference script hash.
 * This is a string field supporting EQ/NE operations only.
 *
 * <p>Reference scripts are a Plutus V2+ feature that allows scripts
 * to be attached to UTXOs and referenced without including them in
 * the transaction, reducing transaction size and fees.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Find any UTXO with a reference script
 * FilterNode filter = new Comparison(
 *     ReferenceScriptHashField.INSTANCE,
 *     CmpOp.NE,
 *     Value.nullValue()
 * );
 *
 * // Find UTXO with specific reference script
 * FilterNode filter = new Comparison(
 *     ReferenceScriptHashField.INSTANCE,
 *     CmpOp.EQ,
 *     Value.ofString("abc123...")
 * );
 * }</pre>
 */
public final class ReferenceScriptHashField implements FieldRef {
    /**
     * Singleton instance.
     */
    public static final ReferenceScriptHashField INSTANCE = new ReferenceScriptHashField();

    private ReferenceScriptHashField() {
        // Singleton - prevent instantiation
    }

    @Override
    public <T> T accept(FieldRefVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return "ReferenceScriptHashField";
    }
}
