package com.bloxbean.cardano.client.quicktx.blueprint.extender;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.util.Tuple;

import java.util.Objects;

public interface ValidatorExtender {
    UtxoSupplier getUtxoSupplier();
    ProtocolParamsSupplier getProtocolParamsSupplier();
    TransactionProcessor getTransactionProcessor();
    TransactionEvaluator getTransactionEvaluator();

    Tuple<String, Integer> getReferenceTxInput();

    String getScriptAddress();
    PlutusScript getPlutusScript();

    default void requireSuppliersNullCheck() {
        Objects.requireNonNull(getUtxoSupplier(), "UtxoSupplier is required");
        Objects.requireNonNull(getProtocolParamsSupplier(), "ProtocolParamsSupplier is required");
        Objects.requireNonNull(getTransactionProcessor(), "TransactionProcessor is required");
    }
}
