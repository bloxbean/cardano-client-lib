package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.quicktx.utxostrategy.LazyUtxoStrategy;

/**
 * Base interface for input-related transaction intentions.
 * 
 * Input intentions are responsible for managing UTXOs that will be consumed
 * by the transaction. They extend TxIntention and require implementation
 * of the utxoStrategy() method to provide UTXO resolution logic.
 * 
 * Examples of input intentions:
 * - CollectFromIntention: Collects specific UTXOs for input
 * - ScriptCollectFromIntention: Collects UTXOs from script addresses with redeemers
 * - ReferenceInputIntention: Adds reference inputs to the transaction
 */
public interface TxInputIntention extends TxIntention {
    
    /**
     * Provide the UTXO strategy for this input intention.
     * This method is required for all input intentions and defines how
     * UTXOs are resolved and selected for the transaction.
     *
     * @return LazyUtxoStrategy that defines how to resolve UTXOs for this input
     */
    LazyUtxoStrategy utxoStrategy();
}