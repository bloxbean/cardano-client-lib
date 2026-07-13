package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.TransactionInfo;

import java.util.Objects;
import java.util.Optional;

/**
 * Decorators for explicitly attaching transaction-observation guarantees to a
 * {@link ChainDataSupplier}.
 *
 * <p>These declarations belong in trusted backend wiring, not portable flow
 * input. The decorator delegates data access unchanged and only exposes
 * {@link TransactionObservationCapabilities} to the reconciliation runtime.
 * Incorrectly declaring authoritative absence can cause a missing transaction
 * to be treated as rolled back, so callers must base the declaration on the
 * deployed index's consistency contract.</p>
 */
public final class ObservationCapabilities {
    private ObservationCapabilities() {
    }

    /**
     * Declares that empty transaction lookups are authoritative relative to
     * the delegate's reported chain tip.
     *
     * @param delegate backend supplier to decorate
     * @return a delegating supplier with authoritative-absence capability
     */
    public static ChainDataSupplier withAuthoritativeAbsence(ChainDataSupplier delegate) {
        return new CapableChainDataSupplier(delegate, true, false);
    }

    /**
     * Declares the exact observation capabilities of a backend deployment.
     *
     * @param delegate backend supplier to decorate
     * @param authoritativeAbsence whether an empty lookup is authoritative at a
     *                             sufficiently advanced chain point
     * @param mempoolObservation whether transaction lookup includes mempool state
     * @return a delegating supplier exposing the declared capabilities
     */
    public static ChainDataSupplier withCapabilities(ChainDataSupplier delegate,
                                                     boolean authoritativeAbsence,
                                                     boolean mempoolObservation) {
        return new CapableChainDataSupplier(delegate, authoritativeAbsence, mempoolObservation);
    }

    private static final class CapableChainDataSupplier
            implements ChainDataSupplier, TransactionObservationCapabilities {
        private final ChainDataSupplier delegate;
        private final boolean authoritativeAbsence;
        private final boolean mempoolObservation;

        private CapableChainDataSupplier(ChainDataSupplier delegate,
                                         boolean authoritativeAbsence,
                                         boolean mempoolObservation) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.authoritativeAbsence = authoritativeAbsence;
            this.mempoolObservation = mempoolObservation;
        }

        @Override
        public long getChainTipHeight() throws ApiException {
            return delegate.getChainTipHeight();
        }

        @Override
        public Optional<TransactionInfo> getTransactionInfo(String txHash) throws ApiException {
            return delegate.getTransactionInfo(txHash);
        }

        @Override
        public boolean supportsAuthoritativeAbsence() {
            return authoritativeAbsence;
        }

        @Override
        public boolean supportsMempoolObservation() {
            return mempoolObservation;
        }
    }
}
