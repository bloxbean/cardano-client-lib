package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.txflow.exec.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutor;
import com.bloxbean.cardano.client.txflow.exec.FlowListener;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TxFlow YAML/TxPlan-based flows using Yaci DevKit.
 * <p>
 * These tests validate the end-to-end execution of flows built using:
 * <ul>
 *     <li>Programmatic TxPlan (builder API with {@code withTxPlan()})</li>
 *     <li>YAML string parsing via {@code TxFlow.fromYaml()}</li>
 *     <li>YAML round-trip (build → serialize → deserialize → execute)</li>
 *     <li>TxPlan steps with different chaining modes (PIPELINED, BATCH)</li>
 *     <li>YAML resource files loaded from classpath</li>
 * </ul>
 * <p>
 * This specifically exercises the {@code step.hasTxPlan()} code path in FlowExecutor
 * which is otherwise untested against a real blockchain.
 * <p>
 * Prerequisites:
 * - Yaci DevKit running at http://localhost:8080/api/v1/
 * - Run with: ./gradlew :txflow:integrationTest --tests TxFlowYamlIntegrationTest -Dyaci.integration.test=true
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TxFlowYamlIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private FlowExecutor flowExecutor;
    private DefaultSignerRegistry signerRegistry;

    // Accounts at indices 300-302 to avoid collision with other tests
    private Account senderAccount;   // Index 300
    private Account receiverAccount; // Index 301
    private Account relayAccount;    // Index 302

    @BeforeEach
    void setUp() throws Exception {
        System.out.println("\n=== TxFlow YAML Integration Test Setup ===");

        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");

        senderAccount = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 300);
        receiverAccount = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 301);
        relayAccount = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 302);

        System.out.println("Using test accounts:");
        System.out.println("  Sender:   " + senderAccount.baseAddress());
        System.out.println("  Receiver: " + receiverAccount.baseAddress());
        System.out.println("  Relay:    " + relayAccount.baseAddress());

        // Top up accounts via Yaci DevKit admin API
        System.out.println("Topping up accounts...");
        assertTrue(YaciDevKitUtil.topup(senderAccount.baseAddress(), 10000), "Failed to topup sender");
        assertTrue(YaciDevKitUtil.topup(receiverAccount.baseAddress(), 10000), "Failed to topup receiver");
        assertTrue(YaciDevKitUtil.topup(relayAccount.baseAddress(), 10000), "Failed to topup relay");

        // Wait for topup transactions to be available
        Thread.sleep(2000);

        // Set up signer registry with account:// references
        signerRegistry = new DefaultSignerRegistry()
                .addAccount("account://sender", senderAccount)
                .addAccount("account://receiver", receiverAccount)
                .addAccount("account://relay", relayAccount);

        // Create FlowExecutor with signer registry
        flowExecutor = FlowExecutor.create(backendService)
                .withSignerRegistry(signerRegistry)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build());

        // Verify accounts have funds
        try {
            List<Utxo> utxos = backendService.getUtxoService()
                    .getUtxos(senderAccount.baseAddress(), 100, 1).getValue();
            System.out.println("  Sender UTXOs: " + utxos.size());
        } catch (Exception e) {
            System.err.println("Warning: Could not verify account balances: " + e.getMessage());
        }

        System.out.println("Setup complete!\n");
    }

    // ==================== Category 1: Programmatic TxPlan (Builder API) ====================

    @Test
    @Order(1)
    void txPlan_singleStepFlow_success() throws Exception {
        System.out.println("=== Test 1: TxPlan - Single Step Flow ===");

        // Build TxPlan from a Tx, using fromRef for signer-registry-based sender resolution
        TxPlan plan = TxPlan.from(
                new Tx()
                        .payToAddress(receiverAccount.baseAddress(), Amount.ada(2.5))
                        .fromRef("account://sender")
        ).withSigner("account://sender");

        TxFlow flow = TxFlow.builder("txplan-single-step")
                .withDescription("Single step payment via TxPlan")
                .addStep(FlowStep.builder("payment")
                        .withDescription("Send 2.5 ADA via TxPlan")
                        .withTxPlan(plan)
                        .build())
                .build();

        // Validate
        TxFlow.ValidationResult validation = flow.validate();
        assertTrue(validation.isValid(), "Flow validation should pass: " + validation.getErrors());

        // Execute
        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "Flow should complete successfully: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(1, result.getCompletedStepCount());
        assertEquals(1, result.getTransactionHashes().size());

        System.out.println("Transaction confirmed: " + result.getTransactionHashes().get(0));
        System.out.println("\n=== Test 1 completed successfully! ===\n");
    }

    @Test
    @Order(2)
    void txPlan_twoStepChainWithDependency_success() throws Exception {
        System.out.println("=== Test 2: TxPlan - Two Step Chain with Dependency ===");

        TxPlan plan1 = TxPlan.from(
                new Tx()
                        .payToAddress(receiverAccount.baseAddress(), Amount.ada(3))
                        .fromRef("account://sender")
        ).withSigner("account://sender");

        TxPlan plan2 = TxPlan.from(
                new Tx()
                        .payToAddress(relayAccount.baseAddress(), Amount.ada(1.5))
                        .fromRef("account://receiver")
        ).withSigner("account://receiver");

        TxFlow flow = TxFlow.builder("txplan-two-step-chain")
                .withDescription("Two step chain via TxPlan with UTXO dependency")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Sender -> Receiver (3 ADA)")
                        .withTxPlan(plan1)
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Receiver -> Relay (1.5 ADA)")
                        .dependsOn("step1", SelectionStrategy.ALL)
                        .withTxPlan(plan2)
                        .build())
                .build();

        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "Flow should complete: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("Tx hashes: " + result.getTransactionHashes());
        System.out.println("\n=== Test 2 completed successfully! ===\n");
    }

    @Test
    @Order(3)
    void txPlan_threeStepChain_success() throws Exception {
        System.out.println("=== Test 3: TxPlan - Three Step Chain ===");

        TxPlan plan1 = TxPlan.from(
                new Tx()
                        .payToAddress(receiverAccount.baseAddress(), Amount.ada(5))
                        .fromRef("account://sender")
        ).withSigner("account://sender");

        TxPlan plan2 = TxPlan.from(
                new Tx()
                        .payToAddress(relayAccount.baseAddress(), Amount.ada(3))
                        .fromRef("account://receiver")
        ).withSigner("account://receiver");

        TxPlan plan3 = TxPlan.from(
                new Tx()
                        .payToAddress(senderAccount.baseAddress(), Amount.ada(1.5))
                        .fromRef("account://relay")
        ).withSigner("account://relay");

        TxFlow flow = TxFlow.builder("txplan-three-step-chain")
                .withDescription("Three step circular chain: sender -> receiver -> relay -> sender")
                .addStep(FlowStep.builder("to-receiver")
                        .withDescription("Sender -> Receiver (5 ADA)")
                        .withTxPlan(plan1)
                        .build())
                .addStep(FlowStep.builder("to-relay")
                        .withDescription("Receiver -> Relay (3 ADA)")
                        .dependsOn("to-receiver")
                        .withTxPlan(plan2)
                        .build())
                .addStep(FlowStep.builder("to-sender")
                        .withDescription("Relay -> Sender (1.5 ADA)")
                        .dependsOn("to-relay")
                        .withTxPlan(plan3)
                        .build())
                .build();

        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "Three-step chain should complete: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(3, result.getCompletedStepCount());
        assertEquals(3, result.getTransactionHashes().size());

        System.out.println("Tx hashes: " + result.getTransactionHashes());
        System.out.println("\n=== Test 3 completed successfully! ===\n");
    }

    // ==================== Category 2: YAML String → TxFlow.fromYaml() → Execute ====================

    @Test
    @Order(4)
    void yamlString_singleStepFlow_success() throws Exception {
        System.out.println("=== Test 4: YAML String - Single Step Flow ===");

        String yaml = """
                version: "1.0"
                flow:
                  id: yaml-single-step
                  description: Single step payment from YAML string
                  steps:
                    - step:
                        id: payment
                        description: Send 2 ADA
                        tx:
                          from_ref: account://sender
                          intents:
                            - type: payment
                              receiver: %s
                              amount:
                                lovelace: 2000000
                        context:
                          signers:
                            - ref: account://sender
                              scope: payment
                """.formatted(receiverAccount.baseAddress());

        System.out.println("YAML:\n" + yaml);

        TxFlow flow = TxFlow.fromYaml(yaml);

        assertEquals("yaml-single-step", flow.getId());
        assertEquals(1, flow.getSteps().size());
        assertTrue(flow.getSteps().get(0).hasTxPlan(), "Step should have TxPlan from YAML");

        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "YAML flow should succeed: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(1, result.getCompletedStepCount());

        System.out.println("Transaction: " + result.getTransactionHashes().get(0));
        System.out.println("\n=== Test 4 completed successfully! ===\n");
    }

    @Test
    @Order(5)
    void yamlString_twoStepWithVariables_success() throws Exception {
        System.out.println("=== Test 5: YAML String - Two Step with Variables ===");

        String yaml = """
                version: "1.0"
                flow:
                  id: yaml-two-step-vars
                  description: Two step flow with variable substitution
                  variables:
                    receiver_addr: %s
                    relay_addr: %s
                  steps:
                    - step:
                        id: step1
                        description: Sender to Receiver
                        tx:
                          from_ref: account://sender
                          intents:
                            - type: payment
                              receiver: ${receiver_addr}
                              amount:
                                lovelace: 3000000
                        context:
                          signers:
                            - ref: account://sender
                              scope: payment
                    - step:
                        id: step2
                        description: Receiver to Relay
                        depends_on:
                          - from_step: step1
                            strategy: all
                        tx:
                          from_ref: account://receiver
                          intents:
                            - type: payment
                              receiver: ${relay_addr}
                              amount:
                                lovelace: 1500000
                        context:
                          signers:
                            - ref: account://receiver
                              scope: payment
                """.formatted(receiverAccount.baseAddress(), relayAccount.baseAddress());

        TxFlow flow = TxFlow.fromYaml(yaml);

        assertEquals("yaml-two-step-vars", flow.getId());
        assertEquals(2, flow.getSteps().size());
        assertTrue(flow.getSteps().get(1).hasDependencies());

        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "YAML flow with variables should succeed: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());

        System.out.println("Tx hashes: " + result.getTransactionHashes());
        System.out.println("\n=== Test 5 completed successfully! ===\n");
    }

    @Test
    @Order(6)
    void yamlString_flowLevelVariablesInheritedBySteps_success() throws Exception {
        System.out.println("=== Test 6: YAML String - Flow-Level Variables Inherited by Steps ===");

        // Flow-level variables should be merged into step TxPlans at execution time
        String yaml = """
                version: "1.0"
                flow:
                  id: yaml-flow-vars
                  description: Flow-level variables inherited by steps
                  variables:
                    receiver_addr: %s
                    payment_amount: 2500000
                  steps:
                    - step:
                        id: payment
                        description: Payment using flow-level variables
                        tx:
                          from_ref: account://sender
                          intents:
                            - type: payment
                              receiver: ${receiver_addr}
                              amount:
                                lovelace: ${payment_amount}
                        context:
                          signers:
                            - ref: account://sender
                              scope: payment
                """.formatted(receiverAccount.baseAddress());

        TxFlow flow = TxFlow.fromYaml(yaml);

        // Verify variables are set on the flow
        assertEquals(receiverAccount.baseAddress(), flow.getVariables().get("receiver_addr"));

        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "Flow-level variables should be inherited: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(1, result.getCompletedStepCount());

        System.out.println("Transaction: " + result.getTransactionHashes().get(0));
        System.out.println("\n=== Test 6 completed successfully! ===\n");
    }

    // ==================== Category 3: YAML Round-Trip → Execute ====================

    @Test
    @Order(7)
    void yamlRoundTrip_serializeDeserializeExecute_success() throws Exception {
        System.out.println("=== Test 7: YAML Round-Trip → Execute ===");

        // Build a flow using TxPlan (which CAN be serialized, unlike factory functions)
        TxPlan plan1 = TxPlan.from(
                new Tx()
                        .payToAddress(receiverAccount.baseAddress(), Amount.ada(2))
                        .fromRef("account://sender")
        ).withSigner("account://sender");

        TxPlan plan2 = TxPlan.from(
                new Tx()
                        .payToAddress(relayAccount.baseAddress(), Amount.ada(1))
                        .fromRef("account://receiver")
        ).withSigner("account://receiver");

        TxFlow originalFlow = TxFlow.builder("roundtrip-flow")
                .withDescription("YAML round-trip test")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Sender -> Receiver")
                        .withTxPlan(plan1)
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Receiver -> Relay")
                        .dependsOn("step1")
                        .withTxPlan(plan2)
                        .build())
                .build();

        // Serialize to YAML
        String yaml = originalFlow.toYaml();
        System.out.println("Serialized YAML:\n" + yaml);

        // Deserialize back
        TxFlow restoredFlow = TxFlow.fromYaml(yaml);

        // Verify structure preserved
        assertEquals(originalFlow.getId(), restoredFlow.getId());
        assertEquals(originalFlow.getSteps().size(), restoredFlow.getSteps().size());
        assertTrue(restoredFlow.getSteps().get(0).hasTxPlan(), "Step 1 should have TxPlan after round-trip");
        assertTrue(restoredFlow.getSteps().get(1).hasTxPlan(), "Step 2 should have TxPlan after round-trip");
        assertTrue(restoredFlow.getSteps().get(1).hasDependencies(), "Step 2 should preserve dependency");

        // Execute the restored flow on blockchain
        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(restoredFlow);

        assertTrue(result.isSuccessful(), "Round-trip flow should execute on blockchain: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());

        System.out.println("Tx hashes: " + result.getTransactionHashes());
        System.out.println("\n=== Test 7 completed successfully! ===\n");
    }

    // ==================== Category 4: Chaining Modes with TxPlan ====================

    @Test
    @Order(8)
    void txPlan_pipelinedMode_success() throws Exception {
        System.out.println("=== Test 8: TxPlan - PIPELINED Mode ===");

        TxPlan plan1 = TxPlan.from(
                new Tx()
                        .payToAddress(receiverAccount.baseAddress(), Amount.ada(2))
                        .fromRef("account://sender")
        ).withSigner("account://sender");

        TxPlan plan2 = TxPlan.from(
                new Tx()
                        .payToAddress(relayAccount.baseAddress(), Amount.ada(1))
                        .fromRef("account://receiver")
        ).withSigner("account://receiver");

        TxFlow flow = TxFlow.builder("txplan-pipelined")
                .withDescription("TxPlan steps in PIPELINED mode")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Sender -> Receiver")
                        .withTxPlan(plan1)
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Receiver -> Relay")
                        .dependsOn("step1", SelectionStrategy.ALL)
                        .withTxPlan(plan2)
                        .build())
                .build();

        FlowResult result = FlowExecutor.create(backendService)
                .withSignerRegistry(signerRegistry)
                .withChainingMode(ChainingMode.PIPELINED)
                .withListener(new LoggingFlowListener())
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "PIPELINED TxPlan flow should succeed: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("Tx hashes: " + result.getTransactionHashes());
        System.out.println("\n=== Test 8 completed successfully! ===\n");
    }

    @Test
    @Order(9)
    void txPlan_batchMode_success() throws Exception {
        System.out.println("=== Test 9: TxPlan - BATCH Mode ===");

        TxPlan plan1 = TxPlan.from(
                new Tx()
                        .payToAddress(receiverAccount.baseAddress(), Amount.ada(2.5))
                        .fromRef("account://sender")
        ).withSigner("account://sender");

        TxPlan plan2 = TxPlan.from(
                new Tx()
                        .payToAddress(relayAccount.baseAddress(), Amount.ada(1.5))
                        .fromRef("account://receiver")
        ).withSigner("account://receiver");

        TxFlow flow = TxFlow.builder("txplan-batch")
                .withDescription("TxPlan steps in BATCH mode")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Sender -> Receiver")
                        .withTxPlan(plan1)
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Receiver -> Relay")
                        .dependsOn("step1", SelectionStrategy.ALL)
                        .withTxPlan(plan2)
                        .build())
                .build();

        FlowResult result = FlowExecutor.create(backendService)
                .withSignerRegistry(signerRegistry)
                .withChainingMode(ChainingMode.BATCH)
                .withListener(new LoggingFlowListener())
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "BATCH TxPlan flow should succeed: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("Tx hashes: " + result.getTransactionHashes());
        System.out.println("\n=== Test 9 completed successfully! ===\n");
    }

    // ==================== Category 5: YAML Resource Files ====================

    @Test
    @Order(10)
    void yamlResourceFile_loadAndExecute_success() throws Exception {
        System.out.println("=== Test 10: YAML Resource File - Load and Execute ===");

        // Load YAML from classpath resource
        String yaml = loadResource("/flows/two-step-chain.yaml");
        assertNotNull(yaml, "Resource file should be loadable");
        assertFalse(yaml.isEmpty(), "Resource file should not be empty");

        System.out.println("Loaded YAML from classpath:\n" + yaml);

        // Inject actual addresses into variable placeholders
        yaml = yaml.replace("${receiver_addr}", receiverAccount.baseAddress())
                    .replace("${relay_addr}", relayAccount.baseAddress());

        TxFlow flow = TxFlow.fromYaml(yaml);

        assertEquals("two-step-chain", flow.getId());
        assertEquals(2, flow.getSteps().size());
        assertTrue(flow.getSteps().get(0).hasTxPlan(), "Step 1 should have TxPlan");
        assertTrue(flow.getSteps().get(1).hasTxPlan(), "Step 2 should have TxPlan");
        assertTrue(flow.getSteps().get(1).hasDependencies(), "Step 2 should depend on step1");

        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "Resource file flow should execute: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("Tx hashes: " + result.getTransactionHashes());
        System.out.println("\n=== Test 10 completed successfully! ===\n");
    }

    // ==================== Category 6: UTXO Selection DSL ====================

    @Test
    @Order(11)
    void txPlan_indexedUtxoSelection_success() throws Exception {
        System.out.println("=== Test 11: TxPlan - Indexed UTXO Selection ===");

        // Step 1: Sender sends two separate payments to Receiver (3 ADA + 2 ADA)
        // This creates two distinct outputs at the receiver address
        TxPlan plan1 = TxPlan.from(
                new Tx()
                        .payToAddress(receiverAccount.baseAddress(), Amount.ada(3))
                        .payToAddress(receiverAccount.baseAddress(), Amount.ada(2))
                        .fromRef("account://sender")
        ).withSigner("account://sender");

        // Step 2: Receiver sends 1.5 ADA to Relay, using only the 2nd output (index 1 = 2 ADA)
        TxPlan plan2 = TxPlan.from(
                new Tx()
                        .payToAddress(relayAccount.baseAddress(), Amount.ada(1.5))
                        .fromRef("account://receiver")
        ).withSigner("account://receiver");

        TxFlow flow = TxFlow.builder("txplan-indexed-utxo")
                .withDescription("Indexed UTXO selection via TxPlan")
                .addStep(FlowStep.builder("multi-output")
                        .withDescription("Sender -> Receiver (3 ADA + 2 ADA as two outputs)")
                        .withTxPlan(plan1)
                        .build())
                .addStep(FlowStep.builder("spend-indexed")
                        .withDescription("Receiver -> Relay (1.5 ADA) using output at index 1")
                        .dependsOnIndex("multi-output", 1)
                        .withTxPlan(plan2)
                        .build())
                .build();

        TxFlow.ValidationResult validation = flow.validate();
        assertTrue(validation.isValid(), "Flow validation should pass: " + validation.getErrors());

        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "Indexed UTXO selection flow should succeed: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("Tx hashes: " + result.getTransactionHashes());
        System.out.println("\n=== Test 11 completed successfully! ===\n");
    }

    @Test
    @Order(12)
    void yamlString_indexedUtxoSelection_success() throws Exception {
        System.out.println("=== Test 12: YAML String - Indexed UTXO Selection ===");

        String yaml = """
                version: "1.0"
                flow:
                  id: yaml-indexed-utxo
                  description: Indexed UTXO selection from YAML
                  steps:
                    - step:
                        id: multi-output
                        description: Sender sends two outputs to Receiver
                        tx:
                          from_ref: account://sender
                          intents:
                            - type: payment
                              address: %s
                              amounts:
                                - unit: lovelace
                                  quantity: 3000000
                            - type: payment
                              address: %s
                              amounts:
                                - unit: lovelace
                                  quantity: 2000000
                        context:
                          signers:
                            - ref: account://sender
                              scope: payment
                    - step:
                        id: spend-indexed
                        description: Receiver spends output at index 1
                        depends_on:
                          - from_step: multi-output
                            strategy: index
                            utxo_index: 1
                        tx:
                          from_ref: account://receiver
                          intents:
                            - type: payment
                              address: %s
                              amounts:
                                - unit: lovelace
                                  quantity: 1500000
                        context:
                          signers:
                            - ref: account://receiver
                              scope: payment
                """.formatted(
                        receiverAccount.baseAddress(),
                        receiverAccount.baseAddress(),
                        relayAccount.baseAddress());

        System.out.println("YAML:\n" + yaml);

        TxFlow flow = TxFlow.fromYaml(yaml);

        assertEquals("yaml-indexed-utxo", flow.getId());
        assertEquals(2, flow.getSteps().size());
        assertTrue(flow.getSteps().get(1).hasDependencies(), "Step 2 should have dependencies");

        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "YAML indexed selection should succeed: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("Tx hashes: " + result.getTransactionHashes());
        System.out.println("\n=== Test 12 completed successfully! ===\n");
    }

    @Test
    @Order(13)
    void yamlString_changeOutputDependency_success() throws Exception {
        System.out.println("=== Test 13: YAML String - Change Output Dependency ===");

        // Step 1: Sender sends 2 ADA to Receiver. This creates a change output back to Sender.
        // Step 2: Sender sends 1 ADA to Relay. With strategy: all, step1's change output
        //         (at sender's address) is available as a pending UTXO for step2.
        String yaml = """
                version: "1.0"
                flow:
                  id: yaml-change-output
                  description: Change output dependency resolution
                  steps:
                    - step:
                        id: initial-send
                        description: Sender sends 2 ADA to Receiver (generates change back to Sender)
                        tx:
                          from_ref: account://sender
                          intents:
                            - type: payment
                              address: %s
                              amounts:
                                - unit: lovelace
                                  quantity: 2000000
                        context:
                          signers:
                            - ref: account://sender
                              scope: payment
                    - step:
                        id: spend-change
                        description: Sender sends 1 ADA to Relay using change from step1
                        depends_on:
                          - from_step: initial-send
                            strategy: all
                        tx:
                          from_ref: account://sender
                          intents:
                            - type: payment
                              address: %s
                              amounts:
                                - unit: lovelace
                                  quantity: 1000000
                        context:
                          signers:
                            - ref: account://sender
                              scope: payment
                """.formatted(
                        receiverAccount.baseAddress(),
                        relayAccount.baseAddress());

        System.out.println("YAML:\n" + yaml);

        TxFlow flow = TxFlow.fromYaml(yaml);

        assertEquals("yaml-change-output", flow.getId());
        assertEquals(2, flow.getSteps().size());
        assertTrue(flow.getSteps().get(1).hasDependencies(), "Step 2 should depend on step 1");

        FlowResult result = flowExecutor
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "Change output dependency should succeed: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("Tx hashes: " + result.getTransactionHashes());
        System.out.println("\n=== Test 13 completed successfully! ===\n");
    }

    // ==================== Helpers ====================

    private String loadResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AfterEach
    void tearDown() {
        System.out.println("Test cleanup completed\n");
    }

    /**
     * Simple logging listener for tests.
     */
    static class LoggingFlowListener implements FlowListener {
        @Override
        public void onFlowStarted(TxFlow flow) {
            System.out.println("Flow started: " + flow.getId());
        }

        @Override
        public void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
            System.out.println("  Step " + (stepIndex + 1) + "/" + totalSteps + " started: " + step.getId());
        }

        @Override
        public void onTransactionSubmitted(FlowStep step, String txHash) {
            System.out.println("  Transaction submitted: " + txHash);
        }

        @Override
        public void onTransactionConfirmed(FlowStep step, String txHash) {
            System.out.println("  Transaction confirmed: " + txHash);
        }

        @Override
        public void onStepCompleted(FlowStep step, FlowStepResult result) {
            System.out.println("  Step completed: " + step.getId() + " (" + result.getStatus() + ")");
        }

        @Override
        public void onStepFailed(FlowStep step, FlowStepResult result) {
            System.out.println("  Step FAILED: " + step.getId());
            if (result.getError() != null) {
                System.out.println("    Error: " + result.getError().getMessage());
            }
        }

        @Override
        public void onFlowCompleted(TxFlow flow, FlowResult result) {
            System.out.println("Flow completed: " + flow.getId() + " - " + result.getCompletedStepCount() + " steps");
        }

        @Override
        public void onFlowFailed(TxFlow flow, FlowResult result) {
            System.out.println("Flow FAILED: " + flow.getId());
            if (result.getError() != null) {
                System.out.println("  Error: " + result.getError().getMessage());
            }
        }
    }
}
