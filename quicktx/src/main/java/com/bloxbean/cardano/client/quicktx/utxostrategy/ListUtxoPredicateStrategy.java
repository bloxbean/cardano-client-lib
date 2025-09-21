package com.bloxbean.cardano.client.quicktx.utxostrategy;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.List;
import java.util.function.Predicate;

/**
 * Strategy that applies a predicate to the complete UTXO list from a script address for complex selections.
 */
public class ListUtxoPredicateStrategy implements LazyUtxoStrategy {
    private final String scriptAddress;
    private final Predicate<List<Utxo>> predicate;
    private final PlutusData redeemer;
    private final PlutusData datum;

    public ListUtxoPredicateStrategy(String scriptAddress, Predicate<List<Utxo>> predicate, PlutusData redeemer, PlutusData datum) {
        this.scriptAddress = scriptAddress;
        this.predicate = predicate;
        this.redeemer = redeemer;
        this.datum = datum;
    }

    @Override
    public List<Utxo> resolve(UtxoSupplier supplier) {
        List<Utxo> allUtxos = supplier.getAll(scriptAddress);
        return predicate.test(allUtxos) ? allUtxos : List.of();
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
}
