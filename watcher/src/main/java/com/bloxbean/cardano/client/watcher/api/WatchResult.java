package com.bloxbean.cardano.client.watcher.api;

import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.Collections;
import java.util.List;

/**
 * Result of a watched transaction operation.
 */
public class WatchResult {
    
    private final Result<Transaction> transactionResult;
    private final List<Utxo> outputUtxos;
    private final WatchMetadata metadata;
    
    private WatchResult(Result<Transaction> transactionResult, List<Utxo> outputUtxos, WatchMetadata metadata) {
        this.transactionResult = transactionResult;
        this.outputUtxos = outputUtxos != null ? List.copyOf(outputUtxos) : Collections.emptyList();
        this.metadata = metadata;
    }
    
    public Result<Transaction> getTransactionResult() {
        return transactionResult;
    }
    
    public List<Utxo> getOutputUtxos() {
        return outputUtxos;
    }
    
    public WatchMetadata getMetadata() {
        return metadata;
    }
    
    public boolean isSuccess() {
        return transactionResult.isSuccessful();
    }
    
    public static WatchResult success(Transaction transaction, List<Utxo> outputs) {
        return new WatchResult(Result.success("Transaction confirmed").withValue(transaction), outputs, null);
    }
    
    public static WatchResult success(Transaction transaction, List<Utxo> outputs, WatchMetadata metadata) {
        return new WatchResult(Result.success("Transaction confirmed").withValue(transaction), outputs, metadata);
    }
    
    public static WatchResult failure(String error) {
        return new WatchResult(Result.error(error), Collections.emptyList(), null);
    }
    
    public static WatchResult failure(Exception error) {
        return new WatchResult(Result.error(error.getMessage()), Collections.emptyList(), null);
    }
}