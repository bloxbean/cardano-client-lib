package com.bloxbean.cardano.client.quicktx.utxostrategy;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.util.List;

//TODO Create a LazyUtxoStrategy implementation from a list of TransactionInput, PlutusData redeemer, PlutusData datum
public class FixedUtxoStrategy implements LazyUtxoStrategy {
    private final List<Utxo> utxos;
    private final PlutusData redeemer;
    private final PlutusData datum;

    public FixedUtxoStrategy(List<Utxo> utxos, PlutusData redeemer, PlutusData datum) {
        this.utxos = utxos;
        this.redeemer = redeemer;
        this.datum = datum;
    }

    @Override
    public List<Utxo> resolve(UtxoSupplier supplier) {
        return utxos;
    }

    @Override
    public String getScriptAddress() {
        return null;
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
