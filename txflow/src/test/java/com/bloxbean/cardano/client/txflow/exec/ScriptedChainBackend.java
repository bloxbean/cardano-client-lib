package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.TransactionInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/** Deterministic chain observation fixture shared by rollback and recovery tests. */
final class ScriptedChainBackend implements ChainDataSupplier, TransactionObservationCapabilities {
    private final Deque<Observation> observations = new ArrayDeque<>();
    private final boolean authoritativeAbsence;
    private Observation current;

    ScriptedChainBackend() {
        this(true);
    }

    ScriptedChainBackend(boolean authoritativeAbsence) {
        this.authoritativeAbsence = authoritativeAbsence;
    }

    ScriptedChainBackend then(Observation... scriptedObservations) {
        observations.addAll(Arrays.asList(scriptedObservations));
        return this;
    }

    @Override
    public long getChainTipHeight() throws ApiException {
        current = observations.pollFirst();
        if (current == null) {
            throw new ApiException("Chain observation script exhausted");
        }
        if (current.errorMessage != null) {
            throw new ApiException(current.errorMessage);
        }
        return current.tipHeight;
    }

    @Override
    public Optional<TransactionInfo> getTransactionInfo(String txHash) throws ApiException {
        if (current == null) {
            throw new ApiException("Transaction queried before chain tip");
        }
        Observation observation = current;
        current = null;
        if (observation.errorMessage != null) {
            throw new ApiException(observation.errorMessage);
        }
        if (observation.transactionInfo.isEmpty()) {
            return Optional.empty();
        }
        TransactionInfo source = observation.transactionInfo.get();
        return Optional.of(TransactionInfo.builder()
                .txHash(Objects.requireNonNullElse(source.getTxHash(), txHash))
                .blockHeight(source.getBlockHeight())
                .blockHash(source.getBlockHash())
                .blockTime(source.getBlockTime())
                .slot(source.getSlot())
                .build());
    }

    @Override
    public boolean supportsAuthoritativeAbsence() {
        return authoritativeAbsence;
    }

    static final class Observation {
        private final long tipHeight;
        private final Optional<TransactionInfo> transactionInfo;
        private final String errorMessage;

        private Observation(long tipHeight, Optional<TransactionInfo> transactionInfo, String errorMessage) {
            this.tipHeight = tipHeight;
            this.transactionInfo = transactionInfo;
            this.errorMessage = errorMessage;
        }

        static Observation absent(long tipHeight) {
            return new Observation(tipHeight, Optional.empty(), null);
        }

        static Observation included(long tipHeight, long blockHeight, String blockHash) {
            return new Observation(tipHeight, Optional.of(TransactionInfo.builder()
                    .blockHeight(blockHeight)
                    .blockHash(blockHash)
                    .build()), null);
        }

        static Observation failure(String message) {
            return new Observation(0, Optional.empty(), message);
        }
    }
}
