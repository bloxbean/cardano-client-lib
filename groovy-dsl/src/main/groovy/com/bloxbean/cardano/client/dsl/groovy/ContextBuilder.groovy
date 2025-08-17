package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.dsl.TxDslBuilder

/**
 * Groovy DSL builder for execution context configuration.
 * Provides natural Groovy syntax for setting transaction execution metadata.
 */
class ContextBuilder {
    private TxDslBuilder txDslBuilder
    
    ContextBuilder(TxDslBuilder txDslBuilder) {
        this.txDslBuilder = txDslBuilder
    }
    
    /**
     * Set the fee payer address
     * Example:
     *   feePayer treasury
     *   feePayer "addr1_treasury..."
     */
    def feePayer(address) {
        txDslBuilder.feePayer(address.toString())
        return this
    }
    
    /**
     * Set the collateral payer address
     * Example:
     *   collateralPayer treasury
     *   collateralPayer "addr1_treasury..."
     */
    def collateralPayer(address) {
        txDslBuilder.collateralPayer(address.toString())
        return this
    }
    
    /**
     * Set the UTXO selection strategy
     * Example:
     *   utxoSelection "LARGEST_FIRST"
     *   utxoSelection strategy
     */
    def utxoSelection(strategy) {
        txDslBuilder.utxoSelectionStrategy(strategy.toString())
        return this
    }
    
    /**
     * Alternative method name for UTXO selection strategy
     * Example:
     *   utxoSelectionStrategy "LARGEST_FIRST"
     */
    def utxoSelectionStrategy(strategy) {
        return utxoSelection(strategy)
    }
    
    /**
     * Set the transaction signer
     * Example:
     *   signer "treasurySigner"
     *   signer treasuryAccount
     */
    def signer(signerKey) {
        txDslBuilder.signer(signerKey.toString())
        return this
    }
    
    /**
     * Support property-style assignment syntax
     * Example:
     *   feePayer = treasury
     *   collateralPayer = treasury
     *   utxoSelection = "LARGEST_FIRST"
     *   signer = treasuryAccount
     */
    void setProperty(String name, value) {
        switch(name) {
            case 'feePayer':
                feePayer(value)
                break
            case 'collateralPayer':
                collateralPayer(value)
                break
            case 'utxoSelection':
                utxoSelection(value)
                break
            case 'utxoSelectionStrategy':
                utxoSelectionStrategy(value)
                break
            case 'signer':
                signer(value)
                break
            default:
                throw new MissingPropertyException("Unknown context property: $name. Valid properties: feePayer, collateralPayer, utxoSelection, signer")
        }
    }
    
    /**
     * Support alternative property access patterns
     */
    def propertyMissing(String name, value) {
        // Delegate to setProperty for consistency
        setProperty(name, value)
    }
    
    /**
     * Handle method calls that might be property access
     */
    def methodMissing(String name, args) {
        if (args.length == 1) {
            // Treat as property setter: methodName(value)
            return setProperty(name, args[0])
        }
        throw new MissingMethodException(name, ContextBuilder, args)
    }
}