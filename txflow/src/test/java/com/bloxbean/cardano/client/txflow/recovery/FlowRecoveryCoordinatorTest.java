package com.bloxbean.cardano.client.txflow.recovery;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import com.bloxbean.cardano.client.txflow.store.SignedPayloadVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowRecoveryCoordinatorTest {
    @Mock private ChainDataSupplier chain;
    @Mock private TransactionProcessor processor;
    private byte[] cbor;
    private SignedPayload payload;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        cbor = new Transaction().serialize();
        payload = new SignedPayload.InlineCbor(cbor, SignedPayloadVerifier.sha256(cbor),
                TransactionUtil.getTxHash(cbor));
    }

    @Test
    void observedHashIsReconciledWithoutResubmission() throws Exception {
        when(chain.getTransactionInfo(payload.transactionHash())).thenReturn(Optional.of(
                TransactionInfo.builder().txHash(payload.transactionHash()).blockHeight(100L).build()));

        FlowRecoveryResult result = coordinator().recover(request(50, 100));

        assertEquals(AttemptState.IN_BLOCK, result.state());
        assertFalse(result.identicalPayloadResubmitted());
        assertEquals(100L, result.inclusion().blockHeight());
    }

    @Test
    void unknownHashResubmitsIdenticalVerifiedBytesWithinValidityWindow() throws Exception {
        when(chain.getTransactionInfo(payload.transactionHash())).thenReturn(Optional.empty());
        when(processor.submitTransaction(aryEq(cbor))).thenReturn(Result.success(payload.transactionHash()));

        FlowRecoveryResult result = coordinator().recover(request(50, 100));

        assertEquals(AttemptState.SUBMITTED, result.state());
        assertTrue(result.identicalPayloadResubmitted());
        verify(processor).submitTransaction(aryEq(cbor));
    }

    @Test
    void expiredValidityWindowRequiresRecoveryInsteadOfRebuild() throws Exception {
        when(chain.getTransactionInfo(payload.transactionHash())).thenReturn(Optional.empty());

        FlowRecoveryResult result = coordinator().recover(request(95, 100));

        assertEquals(AttemptState.RECOVERY_REQUIRED, result.state());
        assertEquals("TXFLOW_VALIDITY_WINDOW_EXPIRED", result.error().code());
    }

    @Test
    void payloadDigestMismatchIsRejectedByCcl() throws Exception {
        when(chain.getTransactionInfo(payload.transactionHash())).thenReturn(Optional.empty());
        SignedPayload corrupt = new SignedPayload.InlineCbor(cbor, "00", payload.transactionHash());
        FlowRecoveryResult result = coordinator().recover(new FlowRecoveryRequest(
                attempt(corrupt, 100L), 50, 5, null));
        assertEquals(AttemptState.RECOVERY_REQUIRED, result.state());
    }

    private FlowRecoveryCoordinator coordinator() {
        return new FlowRecoveryCoordinator(chain, processor);
    }

    private FlowRecoveryRequest request(long currentSlot, long validTo) {
        return new FlowRecoveryRequest(attempt(payload, validTo), currentSlot, 5, null);
    }

    private FlowAttemptSnapshot attempt(SignedPayload signedPayload, Long validTo) {
        return new FlowAttemptSnapshot("step", 1, AttemptState.SUBMITTING, signedPayload,
                0L, validTo, List.of(), List.of(), Instant.EPOCH, null);
    }
}
