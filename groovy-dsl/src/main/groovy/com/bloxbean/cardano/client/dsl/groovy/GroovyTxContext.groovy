package com.bloxbean.cardano.client.dsl.groovy

import com.bloxbean.cardano.client.common.model.Network
import com.bloxbean.cardano.client.function.TxSigner
import com.bloxbean.cardano.client.function.helper.SignerProviders
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder
import com.bloxbean.cardano.client.quicktx.TxResult

import java.util.concurrent.CompletableFuture

/**
 * Groovy-friendly wrapper for QuickTxBuilder.TxContext.
 * Provides natural Groovy syntax for transaction execution while
 * delegating to the proven V3 TxDslBuilder implementation.
 */
class GroovyTxContext {
    private final QuickTxBuilder.TxContext txContext
    
    GroovyTxContext(QuickTxBuilder.TxContext txContext) {
        this.txContext = txContext
    }
    
    // ========== Core Execution Methods ==========
    
    /**
     * Complete the transaction synchronously
     * Returns: TxResult with transaction hash and status
     */
    TxResult complete() {
        return txContext.complete()
    }
    
    /**
     * Complete the transaction asynchronously and wait for confirmation
     * Returns: CompletableFuture<TxResult>
     */
    CompletableFuture<TxResult> completeAsync() {
        return txContext.completeAndWaitAsync()
    }
    
    /**
     * Complete the transaction and wait (blocks until completion)
     * Returns: TxResult with transaction hash and status
     */
    TxResult completeAndWait() {
        return txContext.completeAndWait()
    }
    
    // ========== Additional Context Configuration ==========
    
    /**
     * Additional fee payer configuration (allows chaining after compose)
     * Example:
     *   compose(...).feePayer(treasury).complete()
     */
    GroovyTxContext feePayer(address) {
        txContext.feePayer(address.toString())
        return this
    }
    
    /**
     * Additional collateral payer configuration
     * Example:
     *   compose(...).collateralPayer(treasury).complete()
     */
    GroovyTxContext collateralPayer(address) {
        txContext.collateralPayer(address.toString())
        return this
    }
    
    /**
     * Add a signer to the transaction
     * Example:
     *   compose(...).withSigner(account.signerProvider()).complete()
     */
    GroovyTxContext withSigner(TxSigner signer) {
        txContext.withSigner(signer)
        return this
    }
    
    /**
     * Add a signer to the transaction (alias for withSigner)
     * Example:
     *   compose(...).signer(account.signerProvider()).complete()
     */
    GroovyTxContext signer(TxSigner signer) {
        return withSigner(signer)
    }
    
    /**
     * Add a signer to the transaction via dynamic object (Groovy style)
     * Example:
     *   compose(...).signer("treasuryAccount").complete()
     *   compose(...).signer(account).complete()
     */
    GroovyTxContext signer(def signerObj) {
        if (signerObj instanceof TxSigner) {
            return withSigner(signerObj)
        } else if (signerObj instanceof String) {
            // For string-based signers, delegate to SignerProviders
            try {
                return withSigner(SignerProviders.signerFrom(signerObj))
            } catch (Exception e) {
                // If SignerProviders can't handle it, throw a meaningful error
                throw new IllegalArgumentException("Unable to resolve signer from string: ${signerObj}. " +
                    "Please provide a TxSigner instance or use withAccount() for account objects.", e)
            }
        } else {
            // Try to treat as account-like object
            return withAccount(signerObj)
        }
    }
    
    /**
     * Convenience method to add signer from account
     * Example:
     *   compose(...).withAccount(account).complete()
     */
    GroovyTxContext withAccount(account) {
        if (account.respondsTo('getSignerProvider')) {
            return withSigner(account.getSignerProvider())
        } else if (account.respondsTo('signerProvider')) {
            return withSigner(account.signerProvider())
        } else {
            throw new IllegalArgumentException("Account must have getSignerProvider() or signerProvider() method")
        }
    }
    
    /**
     * Set network for transaction (note: not supported by underlying QuickTxBuilder.TxContext)
     * Example:
     *   compose(...).withNetwork(Networks.testnet()).complete()
     */
    GroovyTxContext withNetwork(Network network) {
        // QuickTxBuilder.TxContext doesn't support withNetwork
        // This is a no-op for compatibility with DSL expectations
        return this
    }
    
    // ========== Groovy-style Method Support ==========
    
    /**
     * Support dynamic method calls for additional QuickTxBuilder.TxContext methods
     * This allows access to any new methods added to TxContext without updating this wrapper
     */
    def methodMissing(String name, args) {
        // Try to call the method on the underlying txContext
        try {
            def result = txContext."$name"(*args)
            
            // If it returns the same txContext, wrap it and return this for chaining
            if (result == txContext) {
                return this
            }
            // Otherwise return the actual result
            return result
        } catch (MissingMethodException e) {
            throw new MissingMethodException(name, GroovyTxContext, args)
        }
    }
    
    /**
     * Support property-style access for getter methods
     */
    def propertyMissing(String name) {
        // Try to access as property on the underlying txContext
        try {
            return txContext."$name"
        } catch (MissingPropertyException e) {
            throw new MissingPropertyException(name, GroovyTxContext)
        }
    }
    
    /**
     * Support property-style assignment
     */
    def propertyMissing(String name, value) {
        // Support property-style context configuration
        switch(name) {
            case 'feePayer':
                return feePayer(value)
            case 'collateralPayer':
                return collateralPayer(value)
            default:
                // Try to set on underlying txContext using setter method
                try {
                    def setterName = "set${name.capitalize()}"
                    txContext."$setterName"(value)
                    return this
                } catch (MissingMethodException e) {
                    // Fallback to direct property assignment
                    try {
                        txContext."$name" = value
                        return this
                    } catch (MissingPropertyException pe) {
                        throw new MissingPropertyException(name, GroovyTxContext)
                    }
                }
        }
    }
    
    // ========== Access to Underlying Context ==========
    
    /**
     * Get the underlying QuickTxBuilder.TxContext for advanced usage
     * Example:
     *   def context = compose(...).unwrap()
     *   // Use Java APIs directly
     */
    QuickTxBuilder.TxContext unwrap() {
        return txContext
    }
    
    /**
     * String representation for debugging
     */
    @Override
    String toString() {
        return "GroovyTxContext[${txContext.toString()}]"
    }
}