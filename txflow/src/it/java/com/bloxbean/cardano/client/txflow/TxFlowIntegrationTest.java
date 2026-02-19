package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.txflow.exec.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutor;
import com.bloxbean.cardano.client.txflow.exec.FlowHandle;
import com.bloxbean.cardano.client.txflow.exec.FlowListener;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TxFlow using Yaci DevKit.
 *
 * Prerequisites:
 * - Yaci DevKit running at http://localhost:8080/api/v1/
 * - Run with: ./gradlew :txflow:integrationTest -Dyaci.integration.test=true
 *
 * Yaci DevKit provides 10 pre-funded addresses (index 0-9) from the default mnemonic.
 * Each address has sufficient ADA for testing.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TxFlowIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private FlowExecutor flowExecutor;
    private DefaultSignerRegistry signerRegistry;

    // Pre-funded accounts from Yaci DevKit
    private Account account0; // Index 0 - Primary sender
    private Account account1; // Index 1 - Primary receiver
    private Account account2; // Index 2 - Secondary account

    @BeforeEach
    void setUp() {
        System.out.println("\n=== TxFlow Integration Test Setup ===");

        // Initialize backend service
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");

        // Create accounts from default mnemonic at different indices
        account0 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 0);
        account1 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 111);
        account2 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 2);

        System.out.println("Using pre-funded Yaci DevKit accounts:");
        System.out.println("  Account 0: " + account0.baseAddress());
        System.out.println("  Account 1: " + account1.baseAddress());
        System.out.println("  Account 2: " + account2.baseAddress());

        // Set up signer registry
        signerRegistry = new DefaultSignerRegistry()
                .addAccount("account://account0", account0)
                .addAccount("account://account1", account1)
                .addAccount("account://account2", account2);

        // Create FlowExecutor
        flowExecutor = FlowExecutor.create(backendService)
                .withSignerRegistry(signerRegistry)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(60)).build());

        // Verify accounts have funds
        try {
            List<Utxo> utxos0 = backendService.getUtxoService()
                    .getUtxos(account0.baseAddress(), 100, 1)
                    .getValue();
            System.out.println("  Account 0 UTXOs: " + utxos0.size());

            List<Utxo> utxos1 = backendService.getUtxoService()
                    .getUtxos(account1.baseAddress(), 100, 1)
                    .getValue();
            System.out.println("  Account 1 UTXOs: " + utxos1.size());
        } catch (Exception e) {
            System.err.println("Warning: Could not verify account balances: " + e.getMessage());
        }

        System.out.println("Setup complete!\n");
    }

    @Test
    @Order(1)
    void testSingleStepFlow() throws Exception {
        System.out.println("=== Test 1: Single Step Flow ===");

        // Create a simple single-step flow using withTxContext
        TxFlow flow = TxFlow.builder("single-step-flow")
                .withDescription("Single step payment test")
                .addStep(FlowStep.builder("payment")
                        .withDescription("Send 2.5 ADA")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2.5))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        System.out.println("Flow: " + flow.getId());
        System.out.println("  From: " + account0.baseAddress());
        System.out.println("  To: " + account1.baseAddress());

        // Validate flow
        TxFlow.ValidationResult validation = flow.validate();
        assertTrue(validation.isValid(), "Flow validation should pass");

        // Execute
        FlowResult result = flowExecutor.executeSync(flow);

        // Verify results
        assertTrue(result.isSuccessful(), "Flow should complete successfully");
        assertEquals(1, result.getCompletedStepCount());
        assertEquals(1, result.getTransactionHashes().size());

        String txHash = result.getTransactionHashes().get(0);
        System.out.println("Transaction confirmed: " + txHash);
        System.out.println("Duration: " + result.getDuration());

        System.out.println("\n=== Test 1 completed successfully! ===\n");
    }

    @Test
    @Order(2)
    void testTwoStepChainWithDependency() throws Exception {
        System.out.println("=== Test 2: Two Step Chain with Dependency ===");

        // Create a two-step flow where step 2 depends on step 1
        // Each step has its own signer since different accounts are sending
        TxFlow flow = TxFlow.builder("two-step-chain")
                .withDescription("Two step payment chain")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (3 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(3))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Account1 -> Account2 (1.5 ADA)")
                        .dependsOn("step1", SelectionStrategy.ALL)
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(1.5))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        System.out.println("Flow: " + flow.getId());
        System.out.println("  Step 1: Account0 -> Account1 (3 ADA)");
        System.out.println("  Step 2: Account1 -> Account2 (1.5 ADA) - depends on step1");

        // Track progress
        AtomicInteger completedSteps = new AtomicInteger(0);

        FlowListener listener = new FlowListener() {
            @Override
            public void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
                System.out.println("Starting step " + (stepIndex + 1) + "/" + totalSteps + ": " + step.getId());
            }

            @Override
            public void onStepCompleted(FlowStep step, FlowStepResult result) {
                completedSteps.incrementAndGet();
                System.out.println("Completed step: " + step.getId() + " - Tx: " + result.getTransactionHash());
            }

            @Override
            public void onStepFailed(FlowStep step, FlowStepResult result) {
                System.err.println("Step FAILED: " + step.getId());
                if (result.getError() != null) {
                    result.getError().printStackTrace();
                }
            }

            @Override
            public void onFlowCompleted(TxFlow flow, FlowResult result) {
                System.out.println("Flow completed! Duration: " + result.getDuration());
            }

            @Override
            public void onFlowFailed(TxFlow flow, FlowResult result) {
                System.err.println("Flow FAILED: " + flow.getId());
                if (result.getError() != null) {
                    result.getError().printStackTrace();
                }
            }
        };

        // Execute with listener
        FlowResult result = FlowExecutor.create(backendService)
                .withListener(listener)
                .executeSync(flow);

        // Verify results
        assertTrue(result.isSuccessful(), "Flow should complete successfully");
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, completedSteps.get());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("\nTransaction hashes:");
        for (int i = 0; i < result.getTransactionHashes().size(); i++) {
            System.out.println("  Step " + (i + 1) + ": " + result.getTransactionHashes().get(i));
        }

        System.out.println("\n=== Test 2 completed successfully! ===\n");
    }

    @Test
    @Order(3)
    void testThreeStepChainWithChainedDependencies() throws Exception {
        System.out.println("=== Test 3: Three Step Chain with Chained Dependencies ===");

        // Create a three-step flow: A -> B -> C -> A (circular payment chain)
        // Each step has its own signer since different accounts are sending
        TxFlow flow = TxFlow.builder("three-step-chain")
                .withDescription("Three step circular payment chain")
                .addStep(FlowStep.builder("transfer-to-b")
                        .withDescription("Account0 -> Account1 (5 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(15))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("transfer-to-c")
                        .withDescription("Account1 -> Account2 (2 ADA)")
                        .dependsOn("transfer-to-b")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(13.5))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .addStep(FlowStep.builder("return-to-a")
                        .withDescription("Account2 -> Account0 (1.5 ADA)")
                        .dependsOn("transfer-to-c")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account0.baseAddress(), Amount.ada(12))
                                        .from(account2.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account2)))
                        .build())
                .build();

        System.out.println("Flow: " + flow.getId());
        System.out.println("  Step 1: Account0 -> Account1 (5 ADA)");
        System.out.println("  Step 2: Account1 -> Account2 (2 ADA) - depends on step1");
        System.out.println("  Step 3: Account2 -> Account0 (1.5 ADA) - depends on step2");

        // Validate
        TxFlow.ValidationResult validation = flow.validate();
        assertTrue(validation.isValid(), "Flow validation should pass: " + validation.getErrors());

        // Execute
        FlowResult result = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .withChainingMode(ChainingMode.BATCH)
                .executeSync(flow);

        // Verify
        assertTrue(result.isSuccessful(), "Flow should complete successfully: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(3, result.getCompletedStepCount());
        assertEquals(3, result.getTransactionHashes().size());

        System.out.println("\nTransaction hashes:");
        for (int i = 0; i < result.getTransactionHashes().size(); i++) {
            System.out.println("  Step " + (i + 1) + ": " + result.getTransactionHashes().get(i));
        }

        System.out.println("\n=== Test 3 completed successfully! ===\n");
    }

    @Test
    @Order(4)
    void testAsyncExecution() throws Exception {
        System.out.println("=== Test 4: Async Execution with FlowHandle ===");

        TxFlow flow = TxFlow.builder("async-flow")
                .withDescription("Async execution test")
                .addStep(FlowStep.builder("async-payment")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(1.5))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        System.out.println("Starting async execution...");

        FlowHandle handle = FlowExecutor.create(backendService)
                .execute(flow);

        // Check initial status
        System.out.println("Initial status: " + handle.getStatus());
        assertFalse(handle.isDone());

        // Wait for completion
        FlowResult result = handle.await(Duration.ofSeconds(120));

        assertTrue(handle.isDone());
        assertTrue(result.isSuccessful());
        assertEquals(FlowStatus.COMPLETED, handle.getStatus());

        System.out.println("Final status: " + handle.getStatus());
        System.out.println("Completed steps: " + handle.getCompletedStepCount() + "/" + handle.getTotalStepCount());

        System.out.println("\n=== Test 4 completed successfully! ===\n");
    }

    @Test
    @Order(5)
    void testFlowWithVariables() throws Exception {
        System.out.println("=== Test 5: Flow with Variables ===");

        // Create a flow with variables
        TxFlow flow = TxFlow.builder("variable-flow")
                .withDescription("Flow with variable substitution")
                .addVariable("sender", account0.baseAddress())
                .addVariable("receiver", account1.baseAddress())
                .addVariable("amount", 2_000_000L) // 2 ADA in lovelace
                .addStep(FlowStep.builder("variable-payment")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        System.out.println("Flow variables:");
        flow.getVariables().forEach((k, v) -> System.out.println("  " + k + ": " + v));

        FlowResult result = FlowExecutor.create(backendService)
                .executeSync(flow);

        assertTrue(result.isSuccessful());
        System.out.println("Transaction: " + result.getTransactionHashes().get(0));

        System.out.println("\n=== Test 5 completed successfully! ===\n");
    }

    @Test
    @Order(6)
    void testFlowYamlRoundTrip() throws Exception {
        System.out.println("=== Test 6: Flow YAML Round Trip ===");

        // Create a flow using withTxContext (note: YAML round-trip will not preserve factory functions)
        // This test focuses on flow structure preservation
        TxFlow originalFlow = TxFlow.builder("yaml-roundtrip")
                .withDescription("YAML serialization test")
                .addVariable("amount", 1_500_000L)
                .addStep(FlowStep.builder("step1")
                        .withDescription("First payment")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(1.5))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Second payment")
                        .dependsOn("step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(1))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        // Serialize to YAML
        String yaml = originalFlow.toYaml();
        System.out.println("Serialized YAML:");
        System.out.println(yaml);

        // Deserialize back
        TxFlow restoredFlow = TxFlow.fromYaml(yaml);

        // Verify structure preserved
        assertEquals(originalFlow.getId(), restoredFlow.getId());
        assertEquals(originalFlow.getDescription(), restoredFlow.getDescription());
        assertEquals(originalFlow.getSteps().size(), restoredFlow.getSteps().size());

        // Verify dependencies preserved
        assertTrue(restoredFlow.getStep("step2").get().hasDependencies());
        assertEquals("step1", restoredFlow.getStep("step2").get().getDependencyStepIds().get(0));

        System.out.println("\nYAML round trip successful!");
        System.out.println("  ID preserved: " + restoredFlow.getId());
        System.out.println("  Steps preserved: " + restoredFlow.getSteps().size());
        System.out.println("  Dependencies preserved: " + restoredFlow.getStep("step2").get().hasDependencies());

        System.out.println("\n=== Test 6 completed successfully! ===\n");
    }

    @Test
    @Order(7)
    void testIndexedUtxoSelection() throws Exception {
        System.out.println("=== Test 7: Indexed UTXO Selection ===");

        // Step 1: Create transaction with multiple outputs
        // Step 2: Use specific output from step 1
        TxFlow flow = TxFlow.builder("indexed-selection")
                .withDescription("Test indexed UTXO selection")
                .addStep(FlowStep.builder("multi-output")
                        .withDescription("Create multiple outputs")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(3))
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("use-second")
                        .withDescription("Use second output")
                        .dependsOnIndex("multi-output", 1) // Use the 2 ADA output
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(1.5))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        System.out.println("Flow: " + flow.getId());
        System.out.println("  Step 1: Create outputs of 3 ADA and 2 ADA");
        System.out.println("  Step 2: Use output at index 1 (2 ADA)");

        // Execute
        FlowResult result = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful());
        assertEquals(2, result.getCompletedStepCount());

        System.out.println("\n=== Test 7 completed successfully! ===\n");
    }

    @Test
    @Order(8)
    void testChangeOutputSelection() throws Exception {
        System.out.println("=== Test 8: Change Output Selection ===");

        // Step 1: Create transaction, step 2 uses change output
        TxFlow flow = TxFlow.builder("change-selection")
                .withDescription("Test change output selection")
                .addStep(FlowStep.builder("create-change")
                        .withDescription("Create transaction with change")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("use-change")
                        .withDescription("Use change output")
                        .dependsOn("create-change")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(1))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        System.out.println("Flow: " + flow.getId());
        System.out.println("  Step 1: Send 2 ADA (creates change)");
        System.out.println("  Step 2: Use change output from step 1");

        FlowResult result = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        assertTrue(result.isSuccessful());
        assertEquals(2, result.getCompletedStepCount());

        System.out.println("\n=== Test 8 completed successfully! ===\n");
    }

    @Test
    @Order(9)
    void testPipelinedChainingMode() throws Exception {
        System.out.println("=== Test 9: Pipelined Chaining Mode (True Transaction Chaining) ===");

        // Create a two-step flow to test PIPELINED mode
        // In PIPELINED mode, both transactions are submitted without waiting for confirmations
        // and can potentially land in the same block
        TxFlow flow = TxFlow.builder("pipelined-chain")
                .withDescription("Test pipelined execution for true transaction chaining")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (2 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Account1 -> Account2 (1 ADA) - uses step1 output")
                        .dependsOn("step1", SelectionStrategy.ALL)
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(1))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        System.out.println("Flow: " + flow.getId());
        System.out.println("  Mode: PIPELINED (true transaction chaining)");
        System.out.println("  Step 1: Account0 -> Account1 (2 ADA)");
        System.out.println("  Step 2: Account1 -> Account2 (1 ADA) - depends on step1");

        // Track timing to see the difference from SEQUENTIAL mode
        long startTime = System.currentTimeMillis();

        // Execute with PIPELINED mode
        FlowResult result = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.PIPELINED)
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        long endTime = System.currentTimeMillis();

        // Verify results
        assertTrue(result.isSuccessful(), "Flow should complete successfully: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("\nTransaction hashes:");
        for (int i = 0; i < result.getTransactionHashes().size(); i++) {
            System.out.println("  Step " + (i + 1) + ": " + result.getTransactionHashes().get(i));
        }
        System.out.println("Execution time: " + (endTime - startTime) + " ms");
        System.out.println("(In PIPELINED mode, transactions can be in the same block!)");

        System.out.println("\n=== Test 9 completed successfully! ===\n");
    }

    @Test
    @Order(10)
    void testErrorPropagation() throws Exception {
        System.out.println("=== Test 10: Error Propagation ===");

        // Create a flow that will fail - send from account with no funds
        // Use a fresh account with no UTXOs (high index that won't have pre-funded balance)
        Account emptyAccount = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 999);

        System.out.println("Empty account (no UTXOs): " + emptyAccount.baseAddress());

        TxFlow flow = TxFlow.builder("error-test")
                .withDescription("Test error propagation")
                .addStep(FlowStep.builder("failing-step")
                        .withDescription("This step should fail due to no UTXOs")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(100))
                                        .from(emptyAccount.baseAddress()))  // No UTXOs - will fail
                                .withSigner(SignerProviders.signerFrom(emptyAccount)))
                        .build())
                .build();

        System.out.println("Executing flow that should fail...");

        FlowResult result = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(flow);

        // Verify flow failed
        assertFalse(result.isSuccessful(), "Flow should not be successful");
        assertTrue(result.isFailed(), "Flow should be marked as failed");
        assertEquals(FlowStatus.FAILED, result.getStatus(), "Flow status should be FAILED");

        // Verify error is accessible at flow level
        assertNotNull(result.getError(), "Flow error should not be null");
        System.out.println("Flow error: " + result.getError().getMessage());

        // Verify failed step is accessible
        Optional<FlowStepResult> failedStep = result.getFailedStep();
        assertTrue(failedStep.isPresent(), "Failed step should be present");
        assertEquals("failing-step", failedStep.get().getStepId(), "Failed step should have correct ID");

        // Verify error is accessible at step level
        assertNotNull(failedStep.get().getError(), "Step error should not be null");
        System.out.println("Step error: " + failedStep.get().getError().getMessage());

        // Verify step status is FAILED
        assertEquals(FlowStatus.FAILED, failedStep.get().getStatus(), "Step status should be FAILED");
        assertFalse(failedStep.get().isSuccessful(), "Step should not be successful");

        // Verify error message is meaningful (not empty/generic)
        String errorMessage = result.getError().getMessage();
        assertTrue(errorMessage != null && !errorMessage.isEmpty(),
                "Error message should be meaningful");

        // Verify completed step count is 0
        assertEquals(0, result.getCompletedStepCount(), "No steps should have completed");

        System.out.println("\nError propagation verified:");
        System.out.println("  Flow error: " + result.getError().getClass().getSimpleName() + ": " + result.getError().getMessage());
        System.out.println("  Step error: " + failedStep.get().getError().getClass().getSimpleName() + ": " + failedStep.get().getError().getMessage());

        System.out.println("\n=== Test 10 completed successfully! ===\n");
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
