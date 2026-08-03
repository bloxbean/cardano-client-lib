package com.bloxbean.cardano.client.txflow.recovery;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.txflow.exec.FlowError;
import com.bloxbean.cardano.client.txflow.exec.FlowErrorCategory;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import com.bloxbean.cardano.client.txflow.store.SignedPayloadVerifier;
import com.bloxbean.cardano.client.txflow.store.InclusionRecord;

import java.time.Clock;
import java.util.Objects;

/**
 * Reconciles one persisted, uncertain transaction attempt without creating a
 * new transaction.
 *
 * <p>Recovery first queries the persisted transaction hash. If it is already
 * observed, the result resumes from that observation. If it is not observed
 * and its recorded validity interval remains safely open, the coordinator
 * verifies the persisted signed payload and resubmits those identical bytes.
 * Missing, corrupt, expired, or unsuccessfully submitted payloads yield
 * {@link AttemptState#RECOVERY_REQUIRED}; they never authorize an implicit
 * rebuild.</p>
 *
 * <p>This class performs attempt-level reconciliation. {@code FlowEngine}
 * surrounds it with execution lookup, durable leases, events, and snapshot
 * updates when recovery is requested by execution identity.</p>
 */
public final class FlowRecoveryCoordinator {
    private final ChainDataSupplier chainDataSupplier;
    private final TransactionProcessor transactionProcessor;
    private final Clock clock;

    /**
     * Creates a coordinator using the system UTC clock for inclusion timestamps.
     *
     * @param chainDataSupplier backend used to observe the persisted hash
     * @param transactionProcessor backend used to resubmit verified signed bytes
     */
    public FlowRecoveryCoordinator(ChainDataSupplier chainDataSupplier,
                                   TransactionProcessor transactionProcessor) {
        this(chainDataSupplier, transactionProcessor, Clock.systemUTC());
    }

    /**
     * Creates a coordinator with an injectable clock.
     *
     * @param chainDataSupplier backend used to observe the persisted hash
     * @param transactionProcessor backend used to resubmit verified signed bytes
     * @param clock clock used for reconstructed inclusion records
     */
    public FlowRecoveryCoordinator(ChainDataSupplier chainDataSupplier,
                                   TransactionProcessor transactionProcessor,
                                   Clock clock) {
        this.chainDataSupplier = Objects.requireNonNull(chainDataSupplier, "chainDataSupplier");
        this.transactionProcessor = Objects.requireNonNull(transactionProcessor, "transactionProcessor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Reconciles the resolved attempt in a recovery request.
     *
     * @param request request whose {@link FlowRecoveryRequest#attempt()} is non-null
     * @return observed, resubmitted, or recovery-required result
     */
    public FlowRecoveryResult recover(FlowRecoveryRequest request) {
        FlowAttemptSnapshot attempt = request.attempt();
        SignedPayload payload = attempt.signedPayload();
        if (payload == null) {
            return recoveryRequired(null, "TXFLOW_SIGNED_PAYLOAD_MISSING",
                    "No signed payload was persisted for uncertain submission");
        }
        try {
            var observed = chainDataSupplier.getTransactionInfo(payload.transactionHash());
            if (observed.isPresent()) {
                var transaction = observed.get();
                AttemptState state = transaction.getBlockHeight() != null
                        ? AttemptState.IN_BLOCK : AttemptState.SUBMITTED;
                InclusionRecord inclusion = transaction.getBlockHeight() != null
                        ? new InclusionRecord(transaction.getBlockHeight(), transaction.getBlockHash(),
                        transaction.getSlot() != null ? transaction.getSlot() : 0,
                        clock.instant(), false) : null;
                return new FlowRecoveryResult(state, payload.transactionHash(), false, null,
                        inclusion);
            }

            if (attempt.validToSlot() != null
                    && request.currentSlot() + request.resubmitSafetyMargin() >= attempt.validToSlot()) {
                return recoveryRequired(payload.transactionHash(), "TXFLOW_VALIDITY_WINDOW_EXPIRED",
                        "Identical payload is at or beyond its safe resubmission window");
            }
            byte[] signedCbor = SignedPayloadVerifier.resolveAndVerify(payload, request.payloadResolver());
            var submission = transactionProcessor.submitTransaction(signedCbor);
            if (!submission.isSuccessful()) {
                return recoveryRequired(payload.transactionHash(), "TXFLOW_IDENTICAL_RESUBMISSION_FAILED",
                        submission.getResponse());
            }
            return new FlowRecoveryResult(AttemptState.SUBMITTED,
                    payload.transactionHash(), true, null);
        } catch (Exception e) {
            return recoveryRequired(payload.transactionHash(), "TXFLOW_RECOVERY_OBSERVATION_FAILED",
                    e.getMessage());
        }
    }

    private FlowRecoveryResult recoveryRequired(String hash, String code, String message) {
        return new FlowRecoveryResult(AttemptState.RECOVERY_REQUIRED, hash, false,
                new FlowError(code, FlowErrorCategory.RECOVERY, message, null, false));
    }
}
