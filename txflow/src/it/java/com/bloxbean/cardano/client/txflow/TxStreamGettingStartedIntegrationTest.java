package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.stream.TxFlowStream;
import com.bloxbean.cardano.client.txflow.stream.TxStreamCancelledException;
import com.bloxbean.cardano.client.txflow.stream.TxStreamException;
import com.bloxbean.cardano.client.txflow.stream.TxStreamFailedException;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemResult;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemStatus;
import com.bloxbean.cardano.client.txflow.stream.TxStreamUncertainException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live Yaci DevKit proof for the ADR 0005 beginner sample and its adjacent
 * uncertainty-first recovery recipe.
 */
class TxStreamGettingStartedIntegrationTest {
    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test "
            + "test test test test test test test test test test test test test sauce";
    private static final String SENDER_REF = "account://txstream-getting-started-sender";
    private static final Duration RECEIPT_TIMEOUT = Duration.ofMinutes(2);

    private static BackendService backend;
    private static Account sender;
    private static Account receiver;

    @BeforeAll
    static void fundAccounts() {
        backend = new BFBackendService(YACI_BASE_URL, "dummy-project-id");
        sender = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 930);
        receiver = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 931);
        assertTrue(YaciDevKitUtil.topupAndWait(sender.baseAddress(), 100, 2_000),
                "Yaci DevKit must fund the beginner sender account");
    }

    @Test
    void beginnerPaymentConfirmsWithManagedRuntimeDefaults() {
        try (FlowRuntime runtime = FlowRuntime.builder(backend)
                .account(SENDER_REF, sender)
                .build();
             TxFlowStream stream = runtime.open("getting-started")) {
            TxPlan plan = TxPlan.from(new Tx()
                            .payToAddress(receiver.baseAddress(), Amount.ada(2))
                            .fromRef(SENDER_REF))
                    .withSigner(SENDER_REF);

            TxStreamItemResult result = stream.submit("getting-started-order-0042", plan)
                    .awaitConfirmed(RECEIPT_TIMEOUT);

            assertEquals(TxStreamItemStatus.CONFIRMED, result.getStatus());
            assertNotNull(result.getTransactionHash());
        }
    }

    @Test
    void rejectedPlanLeavesNoStateAndCorrectedSameIdConfirmsLive() {
        try (FlowRuntime runtime = FlowRuntime.builder(backend)
                .account(SENDER_REF, sender)
                .build();
             TxFlowStream stream = runtime.open("getting-started-rejection")) {
            TxPlan missingFunding = TxPlan.from(new Tx()
                            .payToAddress(receiver.baseAddress(), Amount.ada(1)))
                    .withSigner(SENDER_REF);

            TxStreamException rejected = assertThrows(TxStreamException.class,
                    () -> stream.submit("getting-started-corrected-order", missingFunding));
            assertEquals("TXSTREAM_LANE_UNDERIVABLE", rejected.getCode());
            assertTrue(stream.getItemStatus("getting-started-corrected-order").isEmpty());

            TxStreamItemResult corrected = submitWithRecovery(
                    stream, "getting-started-corrected-order", paymentPlan());
            assertEquals(TxStreamItemStatus.CONFIRMED, corrected.getStatus());
        }
    }

    private static TxPlan paymentPlan() {
        return TxPlan.from(new Tx()
                        .payToAddress(receiver.baseAddress(), Amount.ada(2))
                        .fromRef(SENDER_REF))
                .withSigner(SENDER_REF);
    }

    /** Same uncertainty-first recipe compiled next to the documentation sample. */
    private static TxStreamItemResult submitWithRecovery(TxFlowStream stream,
                                                          String itemId,
                                                          TxPlan plan) {
        try {
            return stream.submit(itemId, plan).awaitConfirmed(RECEIPT_TIMEOUT);
        } catch (TxStreamUncertainException uncertain) {
            // DO NOT RESUBMIT: reconcile the known item/transaction until resolved.
            return stream.awaitResolution(uncertain.itemId(),
                    Duration.ofMinutes(2), Duration.ofSeconds(1));
        } catch (TxStreamCancelledException cancelled) {
            throw cancelled;
        } catch (TxStreamFailedException failed) {
            throw failed;
        }
    }
}
