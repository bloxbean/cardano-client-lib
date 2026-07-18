package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.YaciDevKitUtil;
import com.bloxbean.cardano.client.txflow.exec.ConfirmationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for TxFlowStream using Yaci DevKit.
 *
 * Prerequisites:
 * - Yaci DevKit running at http://localhost:8080/api/v1/
 * - Run with: ./gradlew :txflow:integrationTest --tests TxFlowStreamIntegrationTest -Dyaci.integration.test=true
 */
class TxFlowStreamIntegrationTest {
    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private Account sender1;
    private Account sender2;
    private Account receiver;

    @BeforeEach
    void setUp() throws Exception {
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");

        sender1 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 700);
        sender2 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 701);
        receiver = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 702);

        assertTrue(YaciDevKitUtil.topup(sender1.baseAddress(), 1000), "Failed to topup sender1");
        assertTrue(YaciDevKitUtil.topup(sender2.baseAddress(), 1000), "Failed to topup sender2");
        Thread.sleep(2000);
    }

    @Test
    void streamExecutesSubmittedFlowStepItems() throws Exception {
        try (TxFlowStream stream = TxFlowStream.builder("stream-it", backendService)
                .withWindow(WindowPolicy.countOrTime(2, Duration.ofSeconds(20)))
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .withMaxBufferSize(10)
                .build()) {
            stream.start();

            TxStreamReceipt first = stream.submit(TxWorkItem.fromFlowStep("payment-1",
                    paymentStep("payment-step-1", sender1, Amount.ada(1.5))));
            TxStreamReceipt second = stream.submit(TxWorkItem.fromFlowStep("payment-2",
                    paymentStep("payment-step-2", sender2, Amount.ada(1.25))));

            TxStreamItemResult firstResult = first.await(Duration.ofSeconds(180));
            TxStreamItemResult secondResult = second.await(Duration.ofSeconds(180));
            stream.drain();

            assertEquals(TxStreamItemStatus.CONFIRMED, firstResult.getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED, secondResult.getStatus());
            assertNotNull(firstResult.getTransactionHash());
            assertNotNull(secondResult.getTransactionHash());
            assertEquals(firstResult.getBatchId(), secondResult.getBatchId());

            TxStreamBatchResult batch = stream.getBatchStatus(firstResult.getBatchId()).orElseThrow();
            assertEquals(TxStreamBatchStatus.COMPLETED, batch.getStatus());
            assertEquals(2, batch.getItemIds().size());

            TxStreamStats stats = stream.getStats();
            assertEquals(2, stats.getAcceptedItemCount());
            assertEquals(2, stats.getConfirmedItemCount());
            assertEquals(1, stats.getGeneratedFlowCount());
        }
    }

    private FlowStep paymentStep(String stepId, Account sender, Amount amount) {
        return FlowStep.builder(stepId)
                .withTxContext(builder -> builder
                        .compose(new Tx()
                                .payToAddress(receiver.baseAddress(), amount)
                                .from(sender.baseAddress()))
                        .withSigner(SignerProviders.signerFrom(sender)))
                .build();
    }
}
