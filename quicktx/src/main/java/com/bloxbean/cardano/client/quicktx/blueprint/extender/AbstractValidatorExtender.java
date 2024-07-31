package com.bloxbean.cardano.client.quicktx.blueprint.extender;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.util.Tuple;

import java.util.Objects;

public abstract class AbstractValidatorExtender<T> implements ValidatorExtender {
    protected UtxoSupplier utxoSupplier;
    protected ProtocolParamsSupplier protocolParamsSupplier;
    protected TransactionProcessor transactionProcessor;
    protected TransactionEvaluator txEvaluator;
    protected Tuple<String, Integer> referenceTxInput;

    public T withUtxoSupplier(UtxoSupplier utxoSupplier) {
        this.utxoSupplier = utxoSupplier;
        return (T) this;
    }

    public T withProtocolParamsSupplier(ProtocolParamsSupplier protocolParamsSupplier) {
        this.protocolParamsSupplier = protocolParamsSupplier;
        return (T) this;
    }

    public T withTransactionProcessor(TransactionProcessor transactionProcessor) {
        this.transactionProcessor = transactionProcessor;
        return (T) this;
    }

    public T withBackendService(BackendService backendService) {
        if (Objects.isNull(utxoSupplier) && Objects.isNull(protocolParamsSupplier) && Objects.isNull(transactionProcessor)) {
            this.utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
            this.protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
            this.transactionProcessor = new DefaultTransactionProcessor(backendService.getTransactionService());
        } else {
            throw new IllegalArgumentException("UtxoSupplier, ProtocolParamsSupplier and TransactionProcessor are already set. Cannot set backendService.");
        }

        return (T) this;
    }

    public T withTransactionEvaluator(TransactionEvaluator txEvaluator) {
        this.txEvaluator = txEvaluator;
        return (T) this;
    }

    public T withReferenceTxInput(String txHash, int index) {
        this.referenceTxInput = new Tuple<>(txHash, index);
        return (T) this;
    }

    @Override
    public UtxoSupplier getUtxoSupplier() {
        return utxoSupplier;
    }

    @Override
    public ProtocolParamsSupplier getProtocolParamsSupplier() {
        return protocolParamsSupplier;
    }

    @Override
    public TransactionProcessor getTransactionProcessor() {
        return transactionProcessor;
    }

    @Override
    public TransactionEvaluator getTransactionEvaluator() {
        return txEvaluator;
    }

    public Tuple<String, Integer> getReferenceTxInput() {
        return referenceTxInput;
    }

}
