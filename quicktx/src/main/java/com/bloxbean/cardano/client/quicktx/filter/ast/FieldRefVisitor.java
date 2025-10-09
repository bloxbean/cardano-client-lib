package com.bloxbean.cardano.client.quicktx.filter.ast;

/**
 * Visitor interface for processing field references in filter comparisons.
 * Implementations extract or compile field access logic for different backends.
 *
 * <p>Each visit method corresponds to a specific UTxO field type:
 * <ul>
 *   <li>String fields: address, dataHash, inlineDatum, referenceScriptHash, txHash</li>
 *   <li>Numeric fields: amountQuantity (for specific asset units), outputIndex</li>
 * </ul>
 *
 * @param <T> the type produced by this visitor (e.g., Function, SqlColumn)
 * @see FieldRef
 */
public interface FieldRefVisitor<T> {
    /**
     * Visits an address field reference.
     *
     * @param f the address field (singleton instance)
     * @return the compiled representation for accessing the address field
     */
    T visit(AddressField f);

    /**
     * Visits a data hash field reference.
     *
     * @param f the data hash field (singleton instance)
     * @return the compiled representation for accessing the dataHash field
     */
    T visit(DataHashField f);

    /**
     * Visits an inline datum field reference.
     *
     * @param f the inline datum field (singleton instance)
     * @return the compiled representation for accessing the inlineDatum field
     */
    T visit(InlineDatumField f);

    /**
     * Visits an amount quantity field reference for a specific asset unit.
     *
     * @param f the amount quantity field containing the unit identifier
     * @return the compiled representation for accessing the quantity of the specified unit
     */
    T visit(AmountQuantityField f);

    /**
     * Visits a reference script hash field reference.
     *
     * @param f the reference script hash field (singleton instance)
     * @return the compiled representation for accessing the referenceScriptHash field
     */
    T visit(ReferenceScriptHashField f);

    /**
     * Visits a transaction hash field reference.
     *
     * @param f the transaction hash field (singleton instance)
     * @return the compiled representation for accessing the txHash field
     */
    T visit(TxHashField f);

    /**
     * Visits an output index field reference.
     *
     * @param f the output index field (singleton instance)
     * @return the compiled representation for accessing the outputIndex field
     */
    T visit(OutputIndexField f);
}

