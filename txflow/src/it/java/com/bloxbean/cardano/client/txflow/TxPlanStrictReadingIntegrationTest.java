package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.DefaultChainDataSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutor;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end checks on Yaci DevKit that strict TxPlan reading does not get in the way of TxFlow: plans built in Java are
 * copied as before, and a portable template that writes a whole number as a float compiles and runs.
 * <p>
 * Run with Yaci DevKit on :8080 (admin :10000):
 * {@code ./gradlew :txflow:integrationTest --tests TxPlanStrictReadingIntegrationTest}
 */
public class TxPlanStrictReadingIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private DefaultSignerRegistry signerRegistry;
    private Account sender;   // index 310, apart from the other TxFlow integration tests
    private Account receiver; // index 311

    @BeforeEach
    void setUp() throws Exception {
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");
        sender = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 310);
        receiver = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 311);
        assertTrue(YaciDevKitUtil.topup(sender.baseAddress(), 1000), "Failed to topup sender");
        Thread.sleep(2000);
        signerRegistry = new DefaultSignerRegistry().addAccount("account://sender", sender);
    }

    @Test
    void javaBuiltPlanWithATemplateValuedVariableRuns() {
        TxPlan plan = TxPlan.from(new Tx()
                        .payToAddress(receiver.baseAddress(), Amount.ada(2))
                        .fromRef("account://sender"))
                .withSigner("account://sender")
                .addVariable("receiver", receiver.baseAddress())
                .addVariable("note", "${receiver}");
        // A document with this variable is rejected; TxFlow copies the in-memory plan without that authoring rule.
        assertThrows(IllegalArgumentException.class, () -> TxPlan.from(plan.toYaml()));

        TxFlow flow = TxFlow.builder("java-built-template-variable")
                .addStep(FlowStep.builder("payment").withTxPlan(plan).build())
                .build();
        FlowResult result = FlowExecutor.create(backendService)
                .withSignerRegistry(signerRegistry)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), () -> "flow failed: "
                + (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(1, result.getTransactionHashes().size());
    }

    @Test
    void portableTemplateWithAWholeNumberFloatCompilesAndRuns() {
        String yaml = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: whole-number-float}
                spec:
                  network: preview
                  parameters:
                    beneficiary: {type: address, required: true}
                  execution:
                    confirmation: {min_confirmations: 1, check_interval: 1s, timeout: 2m}
                    rollback:
                      action: FAIL
                      monitoring_horizon: UNTIL_FLOW_TERMINAL
                      minimum_consistent_absence_observations: 2
                  steps:
                    - id: payment
                      transaction:
                        tx:
                          from_ref: account://sender
                          intents:
                            - type: payment
                              address: '${{ inputs.beneficiary }}'
                              amounts:
                                - {unit: lovelace, quantity: 2000000.0}
                        context:
                          signers:
                            - {ref: account://sender, scope: payment}
                """;
        TxFlow definition = TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();
        FlowEngine engine = FlowEngine.builder(
                        new DefaultUtxoSupplier(backendService.getUtxoService()),
                        new DefaultProtocolParamsSupplier(backendService.getEpochService()),
                        new DefaultTransactionProcessor(backendService.getTransactionService()),
                        new DefaultChainDataSupplier(backendService))
                .executor(Runnable::run)
                .maintenanceExecutor(Runnable::run)
                .signerRegistry(signerRegistry)
                .store(new InMemoryFlowExecutionStore())
                .build();

        FlowExecutionResult result = engine.start(FlowExecutionRequest.builder(definition)
                        .executionId("whole-number-float-execution")
                        .bindings(FlowBindings.builder().put("beneficiary", receiver.baseAddress()).build())
                        .build())
                .await();

        assertTrue(result.isSuccessful(), () -> result.error() != null ? result.error().message() : "execution failed");
        assertEquals(1, result.steps().size());
    }
}
