package com.bloxbean.cardano.client.quicktx.utxostrategy;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;

import java.util.ArrayList;
import java.util.List;

//TODO Create a LazyUtxoStrategy implementation from a list of TransactionInput, PlutusData redeemer, PlutusData datum
public class FixedUtxoRefStrategy implements LazyUtxoStrategy {
    private final List<TransactionInput> transactionInputs;
    private final PlutusData redeemer;
    private final PlutusData datum;

    public FixedUtxoRefStrategy(List<TransactionInput> transactionInputs, PlutusData redeemer, PlutusData datum) {
        this.transactionInputs = transactionInputs;
        this.redeemer = redeemer;
        this.datum = datum;
    }

    @Override
    public List<Utxo> resolve(UtxoSupplier supplier) {
        List<Utxo> utxos = new ArrayList<>();
        for (TransactionInput txInput : transactionInputs) {
            Utxo utxo = supplier.getTxOutput(txInput.getTransactionId(), txInput.getIndex())
                    .orElseThrow(() -> new TxBuildException("UTxO not found for input: " + txInput));
            if (utxo != null) {
                utxos.add(utxo);
            } else {
                throw new TxBuildException("UTxO not found for input: " + txInput);
            }
        }
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
