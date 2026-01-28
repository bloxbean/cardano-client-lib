package com.bloxbean.cardano.client.quicktx.utxostrategy;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Strategy that selects the first UTXO matching a predicate from a script address.
 */
public class SingleUtxoPredicateStrategy implements LazyUtxoStrategy {
    private final String scriptAddress;
    private final Predicate<Utxo> predicate;
    private final PlutusData redeemer;
    private final PlutusData datum;

    public SingleUtxoPredicateStrategy(String scriptAddress, Predicate<Utxo> predicate, PlutusData redeemer, PlutusData datum) {
        this.scriptAddress = scriptAddress;
        this.predicate = predicate;
        this.redeemer = redeemer;
        this.datum = datum;
    }

    @Override
    public List<Utxo> resolve(UtxoSupplier supplier) {
        return supplier.getAll(scriptAddress).stream()
                .filter(predicate)
                .limit(1)  // Single UTXO
                .collect(Collectors.toList());
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
        return false; // Predicate-based strategies cannot be serialized
    }
}
