package com.bloxbean.cardano.client.quicktx.utxostrategy;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.List;

/**
 * Interface for lazy UTXO resolution strategies.
 * Implementations define how UTXOs are selected from a script address during execution.
 */
public interface LazyUtxoStrategy {
    /**
     * Resolve UTXOs from the supplier based on the strategy.
     *
     * @param supplier UTXO supplier
     * @return list of selected UTXOs
     */
    List<Utxo> resolve(UtxoSupplier supplier);

    /**
     * Get the script address to query UTXOs from.
     *
     * @return script address
     */
    String getScriptAddress();

    /**
     * Get the redeemer data for this strategy.
     *
     * @return redeemer data
     */
    PlutusData getRedeemer();

    /**
     * Get the datum for this strategy.
     *
     * @return datum
     */
    PlutusData getDatum();

    /**
     * Check if this strategy can be serialized to YAML.
     * Predicate-based strategies cannot be serialized.
     *
     * @return true if serializable, false if runtime-only
     */
    boolean isSerializable();
}
