package com.bloxbean.cardano.client.quicktx.filter.runtime;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.filter.UtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.runtime.memory.InMemoryUtxoFilterEngine;
import com.bloxbean.cardano.client.quicktx.utxostrategy.LazyUtxoStrategy;

import java.util.List;

/**
 * Lazy strategy that applies a UtxoFilterSpec against UTXOs at a script address.
 * Currently uses the in-memory backend. Future backends can be dispatched via spec.backend().
 */
public class UtxoFilterStrategy implements LazyUtxoStrategy {
    private final String scriptAddress;
    private final UtxoFilterSpec spec;
    private final PlutusData redeemer;
    private final PlutusData datum;

    public UtxoFilterStrategy(String scriptAddress, UtxoFilterSpec spec, PlutusData redeemer, PlutusData datum) {
        this.scriptAddress = scriptAddress;
        this.spec = spec;
        this.redeemer = redeemer;
        this.datum = datum;
    }

    @Override
    public List<Utxo> resolve(UtxoSupplier supplier) {
        List<Utxo> all = supplier.getAll(scriptAddress);
        // For now always use in-memory evaluation
        return InMemoryUtxoFilterEngine.filter(all, spec.root(), spec.selection());
    }

    @Override
    public String getScriptAddress() {
        return scriptAddress;
    }

    @Override
    public PlutusData getRedeemer() {
        return redeemer;
    }

    @Override
    public PlutusData getDatum() {
        return datum;
    }

    @Override
    public boolean isSerializable() {
        return true;
    }
}

