package com.bloxbean.cardano.client.quicktx.filter.ast;

/**
 * Represents a field reference in UTxO filter comparisons.
 * Each field corresponds to a property of a UTxO that can be filtered.
 *
 * <p>Supported field types:
 * <ul>
 *   <li>{@link AddressField} - The address of the UTxO</li>
 *   <li>{@link DataHashField} - The data hash attached to the UTxO</li>
 *   <li>{@link InlineDatumField} - The inline datum (hex) of the UTxO</li>
 *   <li>{@link AmountQuantityField} - The quantity of a specific asset unit</li>
 * </ul>
 *
 * <p>Type constraints:
 * <ul>
 *   <li>String fields (address, dataHash, inlineDatum) support only EQ/NE operations</li>
 *   <li>Numeric fields (amountQuantity) support all comparison operations</li>
 * </ul>
 *
 * @see FieldRefVisitor
 * @see Comparison
 */
public interface FieldRef {
    /**
     * Accepts a visitor that processes this field reference according to its type.
     *
     * @param visitor the visitor that will process this field reference
     * @param <T> the return type of the visitor's processing
     * @return the result of the visitor's processing of this field
     */
    <T> T accept(FieldRefVisitor<T> visitor);
}

