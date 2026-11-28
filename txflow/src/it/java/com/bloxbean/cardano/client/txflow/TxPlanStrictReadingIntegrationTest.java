package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end checks on Yaci DevKit verifying strict TxPlan reading through {@link FlowEngine}:
 * Java-built plans and valid portable {@code v1} flows execute cleanly, while misspelled intent
 * fields are rejected before submission.
 * <p>
 * Run with Yaci DevKit on :8080 (admin :10000):
 * {@code ./gradlew :txflow:integrationTest --tests TxPlanStrictReadingIntegrationTest}
 */
public class TxPlanStrictReadingIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private DefaultSignerRegistry signerRegistry;
    private ExecutorService executorService;
    private Account sender;   // index 310, apart from the other TxFlow integration tests
    private Account receiver; // index 311

    @BeforeEach
    void setUp() throws Exception {
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");
        executorService = Executors.newFixedThreadPool(2);
        sender = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 310);
        receiver = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 311);
        assertTrue(YaciDevKitUtil.topup(sender.baseAddress(), 1000), "Failed to topup sender");
        Thread.sleep(2000);
        signerRegistry = new DefaultSignerRegistry().addAccount("account://sender", sender);
    }

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void javaBuiltPlanWithNumberLikeMetadataRunsThroughFlowEngine() throws Exception {
        CBORMetadata metadata = new CBORMetadata()
                .put(BigInteger.valueOf(1001), "1_000")
                .put(BigInteger.valueOf(1002), "20000000.0");
        TxPlan plan = TxPlan.from(new Tx()
                        .payToAddress(receiver.baseAddress(), Amount.ada(2))
                        .attachMetadata(metadata)
                        .fromRef("account://sender"))
                .withSigner("account://sender");

        TxFlow flow = TxFlow.builder("java-built-strict-reading")
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .addStep(FlowStep.builder("payment").withTxPlan(plan).build())
                .build();
        FlowExecutionResult result = engine().start(FlowExecutionRequest.builder(flow)
                        .executionId("java-built-strict-reading-it")
                        .build())
                .await();

        assertTrue(result.isSuccessful(), () -> "flow failed: "
                + (result.error() != null ? result.error().message() : "no error"));
        assertEquals(1, result.attempts().size());
        String txHash = result.attempts().get(0).signedPayload().transactionHash();
        assertTrue(backendService.getTransactionService().getTransactionUtxos(txHash).getValue().getOutputs().stream()
                .anyMatch(output -> output.getAddress().equals(receiver.baseAddress())), "receiver was paid");
    }

    @Test
    void portableV1FlowRunsAndMisspelledFieldIsRejectedBeforeSubmission() throws Exception {
        String yaml = """
                api_version: txflow.cardano-client.dev/v1
                kind: TxFlow
                metadata: {name: portable-strict-reading}
                spec:
                  network: preview
                  parameters:
                    beneficiary: {type: address, required: true}
                  execution:
                    confirmation: {min_confirmations: 1, check_interval: 1s, timeout: 2m}
                  steps:
                    - id: payment
                      transaction:
                        tx:
                          from_ref: account://sender
                          intents:
                            - type: payment
                              address: '${{ inputs.beneficiary }}'
                              amounts:
                                - {unit: lovelace, quantity: 3000000}
                        context:
                          signers:
                            - {ref: account://sender, scope: payment}
                """;
        TxFlow validFlow = TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();
        FlowExecutionResult result = engine().start(FlowExecutionRequest.builder(validFlow)
                        .executionId("portable-strict-reading-it")
                        .bindings(FlowBindings.builder().put("beneficiary", receiver.baseAddress()).build())
                        .build())
                .await();

        assertTrue(result.isSuccessful(), () -> "flow failed: "
                + (result.error() != null ? result.error().message() : "no error"));
        String txHash = result.attempts().get(0).signedPayload().transactionHash();
        assertTrue(backendService.getTransactionService().getTransactionUtxos(txHash).getValue().getOutputs().stream()
                .anyMatch(output -> output.getAddress().equals(receiver.baseAddress())), "receiver was paid");

        TxFlow misspelledFlow = TxFlowCodec.standard()
                .parse(yaml.replace("address:", "adress:"), FlowParseOptions.serverDefaults())
                .requireFlow();
        FlowExecutionResult rejected = engine().start(FlowExecutionRequest.builder(misspelledFlow)
                        .executionId("portable-strict-reading-rejected-it")
                        .bindings(FlowBindings.builder().put("beneficiary", receiver.baseAddress()).build())
                        .build())
                .await();

        assertFalse(rejected.isSuccessful());
        assertTrue(rejected.attempts().isEmpty(), "misspelled intent must fail before submission");
        assertTrue(rejected.error() != null && rejected.error().message().contains("adress"),
                () -> "expected misspelled field name in error, got: " + rejected.error());
    }

    private FlowEngine engine() {
        return FlowEngine.builder(backendService)
                .executor(executorService)
                .maintenanceExecutor(executorService)
                .signerRegistry(signerRegistry)
                .store(new InMemoryFlowExecutionStore())
                .build();
    }
}
