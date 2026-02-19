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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for PIPELINED and BATCH chaining modes.
 * <p>
 * These tests verify that PIPELINED and BATCH modes work correctly for:
 * - Basic multi-step flows
 * - Variable substitution
 * - Async execution with FlowHandle
 * - Same-block transaction inclusion
 * <p>
 * Prerequisites:
 * - Yaci DevKit running at http://localhost:8080/api/v1/
 * - Run with: ./gradlew :txflow:integrationTest --tests ChainingModeIntegrationTest -Dyaci.integration.test=true
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ChainingModeIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private DefaultSignerRegistry signerRegistry;

    // Pre-funded accounts from Yaci DevKit
    private Account account0; // Index 0 - Primary sender
    private Account account1; // Index 1 - Primary receiver
    private Account account2; // Index 2 - Secondary account

    @BeforeEach
    void setUp() throws Exception {
        System.out.println("\n=== Chaining Mode Integration Test Setup ===");

        // Initialize backend service
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");

        // Create accounts from default mnemonic at different indices
        // Using unique indices to avoid conflicts with other tests
        account0 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 200);
        account1 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 201);
        account2 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 202);

        System.out.println("Using test accounts:");
        System.out.println("  Account 0: " + account0.baseAddress());
        System.out.println("  Account 1: " + account1.baseAddress());
        System.out.println("  Account 2: " + account2.baseAddress());

        // Top up all accounts using Yaci DevKit admin API
        System.out.println("Topping up accounts...");
        assertTrue(YaciDevKitUtil.topup(account0.baseAddress(), 10000), "Failed to topup account0");
        assertTrue(YaciDevKitUtil.topup(account1.baseAddress(), 10000), "Failed to topup account1");
        assertTrue(YaciDevKitUtil.topup(account2.baseAddress(), 10000), "Failed to topup account2");

        // Wait for topup transactions to be available
        Thread.sleep(2000);

        // Set up signer registry
        signerRegistry = new DefaultSignerRegistry()
                .addAccount("account://account0", account0)
                .addAccount("account://account1", account1)
                .addAccount("account://account2", account2);

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

            List<Utxo> utxos2 = backendService.getUtxoService()
                    .getUtxos(account2.baseAddress(), 100, 1)
                    .getValue();
            System.out.println("  Account 2 UTXOs: " + utxos2.size());
        } catch (Exception e) {
            System.err.println("Warning: Could not verify account balances: " + e.getMessage());
        }

        System.out.println("Setup complete!\n");
    }

    // ==================== PIPELINED Mode Tests ====================

    @Test
    @Order(1)
    void pipelined_twoStepFlow_success() throws Exception {
        System.out.println("=== Test 1: PIPELINED - Two Step Flow ===");

        TxFlow flow = TxFlow.builder("pipelined-two-step")
                .withDescription("PIPELINED mode: Two step payment chain")
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
        System.out.println("  Mode: PIPELINED");

        FlowResult result = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.PIPELINED)
                .withListener(new LoggingFlowListener())
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "PIPELINED flow should complete successfully: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("\nTransaction hashes:");
        result.getTransactionHashes().forEach(hash -> System.out.println("  " + hash));

        System.out.println("\n=== Test 1 completed successfully! ===\n");
    }

    @Test
    @Order(2)
    void pipelined_threeStepFlow_success() throws Exception {
        System.out.println("=== Test 2: PIPELINED - Three Step Flow ===");

        TxFlow flow = TxFlow.builder("pipelined-three-step")
                .withDescription("PIPELINED mode: Three step circular payment chain")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (5 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(5))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Account1 -> Account2 (3 ADA)")
                        .dependsOn("step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(3))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .addStep(FlowStep.builder("step3")
                        .withDescription("Account2 -> Account0 (1.5 ADA)")
                        .dependsOn("step2")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account0.baseAddress(), Amount.ada(1.5))
                                        .from(account2.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account2)))
                        .build())
                .build();

        System.out.println("Flow: " + flow.getId());
        System.out.println("  Mode: PIPELINED");
        System.out.println("  Steps: 3");

        FlowResult result = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.PIPELINED)
                .withListener(new LoggingFlowListener())
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "PIPELINED 3-step flow should complete: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(3, result.getCompletedStepCount());
        assertEquals(3, result.getTransactionHashes().size());

        System.out.println("\nTransaction hashes:");
        result.getTransactionHashes().forEach(hash -> System.out.println("  " + hash));

        System.out.println("\n=== Test 2 completed successfully! ===\n");
    }

    @Test
    @Order(3)
    void pipelined_withVariables_success() throws Exception {
        System.out.println("=== Test 3: PIPELINED - With Variables ===");

        TxFlow flow = TxFlow.builder("pipelined-variables")
                .withDescription("PIPELINED mode with variables")
                .addVariable("sender", account0.baseAddress())
                .addVariable("receiver", account1.baseAddress())
                .addVariable("amount", 1_500_000L) // 1.5 ADA in lovelace
                .addStep(FlowStep.builder("variable-payment")
                        .withDescription("Payment using flow variables")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(1.5))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        System.out.println("Flow variables:");
        flow.getVariables().forEach((k, v) -> System.out.println("  " + k + ": " + v));

        FlowResult result = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.PIPELINED)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "PIPELINED flow with variables should succeed");
        assertEquals(1, result.getCompletedStepCount());

        System.out.println("Transaction: " + result.getTransactionHashes().get(0));
        System.out.println("\n=== Test 3 completed successfully! ===\n");
    }

    @Test
    @Order(4)
    void pipelined_asyncExecution_tracksProgress() throws Exception {
        System.out.println("=== Test 4: PIPELINED - Async Execution ===");

        TxFlow flow = TxFlow.builder("pipelined-async")
                .withDescription("PIPELINED async execution test")
                .addStep(FlowStep.builder("async-step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(1.2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("async-step2")
                        .dependsOn("async-step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(0.8))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        System.out.println("Starting async execution...");

        FlowHandle handle = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.PIPELINED)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .execute(flow);

        // Check initial state
        System.out.println("Initial status: " + handle.getStatus());
        System.out.println("Total steps: " + handle.getTotalStepCount());
        assertFalse(handle.isDone());

        // Wait for completion
        FlowResult result = handle.await(Duration.ofSeconds(120));

        // Verify final state
        assertTrue(handle.isDone());
        assertTrue(result.isSuccessful());
        assertEquals(FlowStatus.COMPLETED, handle.getStatus());
        assertEquals(2, handle.getCompletedStepCount());

        System.out.println("Final status: " + handle.getStatus());
        System.out.println("Completed steps: " + handle.getCompletedStepCount() + "/" + handle.getTotalStepCount());

        System.out.println("\n=== Test 4 completed successfully! ===\n");
    }

    // ==================== BATCH Mode Tests ====================

    @Test
    @Order(5)
    void batch_twoStepFlow_success() throws Exception {
        System.out.println("=== Test 5: BATCH - Two Step Flow ===");

        TxFlow flow = TxFlow.builder("batch-two-step")
                .withDescription("BATCH mode: Two step payment chain")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (2.5 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2.5))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Account1 -> Account2 (1.5 ADA) - uses step1 output")
                        .dependsOn("step1", SelectionStrategy.ALL)
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(1.5))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        System.out.println("Flow: " + flow.getId());
        System.out.println("  Mode: BATCH (build all, then submit all)");

        FlowResult result = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.BATCH)
                .withListener(new LoggingFlowListener())
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "BATCH flow should complete successfully: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(2, result.getCompletedStepCount());
        assertEquals(2, result.getTransactionHashes().size());

        System.out.println("\nTransaction hashes:");
        result.getTransactionHashes().forEach(hash -> System.out.println("  " + hash));

        System.out.println("\n=== Test 5 completed successfully! ===\n");
    }

    @Test
    @Order(6)
    void batch_threeStepFlow_success() throws Exception {
        System.out.println("=== Test 6: BATCH - Three Step Flow ===");

        TxFlow flow = TxFlow.builder("batch-three-step")
                .withDescription("BATCH mode: Three step circular payment chain")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (6 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(6))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Account1 -> Account2 (4 ADA)")
                        .dependsOn("step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(4))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .addStep(FlowStep.builder("step3")
                        .withDescription("Account2 -> Account0 (2 ADA)")
                        .dependsOn("step2")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account0.baseAddress(), Amount.ada(2))
                                        .from(account2.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account2)))
                        .build())
                .build();

        System.out.println("Flow: " + flow.getId());
        System.out.println("  Mode: BATCH");
        System.out.println("  Steps: 3");

        FlowResult result = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.BATCH)
                .withListener(new LoggingFlowListener())
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "BATCH 3-step flow should complete: " +
                (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(3, result.getCompletedStepCount());
        assertEquals(3, result.getTransactionHashes().size());

        System.out.println("\nTransaction hashes:");
        result.getTransactionHashes().forEach(hash -> System.out.println("  " + hash));

        System.out.println("\n=== Test 6 completed successfully! ===\n");
    }

    @Test
    @Order(7)
    void batch_withVariables_success() throws Exception {
        System.out.println("=== Test 7: BATCH - With Variables ===");

        TxFlow flow = TxFlow.builder("batch-variables")
                .withDescription("BATCH mode with variables")
                .addVariable("sender", account0.baseAddress())
                .addVariable("receiver", account1.baseAddress())
                .addVariable("amount", 2_000_000L) // 2 ADA
                .addStep(FlowStep.builder("variable-payment")
                        .withDescription("Payment using flow variables")
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
                .withChainingMode(ChainingMode.BATCH)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "BATCH flow with variables should succeed");
        assertEquals(1, result.getCompletedStepCount());

        System.out.println("Transaction: " + result.getTransactionHashes().get(0));
        System.out.println("\n=== Test 7 completed successfully! ===\n");
    }

    @Test
    @Order(8)
    void batch_asyncExecution_tracksProgress() throws Exception {
        System.out.println("=== Test 8: BATCH - Async Execution ===");

        TxFlow flow = TxFlow.builder("batch-async")
                .withDescription("BATCH async execution test")
                .addStep(FlowStep.builder("async-step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(1.3))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("async-step2")
                        .dependsOn("async-step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(0.9))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        System.out.println("Starting async BATCH execution...");

        FlowHandle handle = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.BATCH)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .execute(flow);

        // Check initial state
        System.out.println("Initial status: " + handle.getStatus());
        System.out.println("Total steps: " + handle.getTotalStepCount());
        assertFalse(handle.isDone());

        // Wait for completion
        FlowResult result = handle.await(Duration.ofSeconds(120));

        // Verify final state
        assertTrue(handle.isDone());
        assertTrue(result.isSuccessful());
        assertEquals(FlowStatus.COMPLETED, handle.getStatus());
        assertEquals(2, handle.getCompletedStepCount());

        System.out.println("Final status: " + handle.getStatus());
        System.out.println("Completed steps: " + handle.getCompletedStepCount() + "/" + handle.getTotalStepCount());

        System.out.println("\n=== Test 8 completed successfully! ===\n");
    }

    @Test
    @Order(9)
    void batch_errorDuringBuild_failsGracefully() throws Exception {
        System.out.println("=== Test 9: BATCH - Error During Build ===");

        // Use an account with no UTXOs (high index)
        Account emptyAccount = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 999);
        System.out.println("Empty account (no UTXOs): " + emptyAccount.baseAddress());

        TxFlow flow = TxFlow.builder("batch-error-test")
                .withDescription("BATCH error handling test")
                .addStep(FlowStep.builder("failing-step")
                        .withDescription("This step should fail during build phase")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(100))
                                        .from(emptyAccount.baseAddress())) // No UTXOs
                                .withSigner(SignerProviders.signerFrom(emptyAccount)))
                        .build())
                .build();

        System.out.println("Executing BATCH flow that should fail...");

        FlowResult result = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.BATCH)
                .withListener(new LoggingFlowListener())
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        // Verify flow failed gracefully
        assertFalse(result.isSuccessful(), "Flow should not be successful");
        assertTrue(result.isFailed(), "Flow should be marked as failed");
        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertNotNull(result.getError(), "Flow error should not be null");

        System.out.println("Flow error: " + result.getError().getMessage());
        System.out.println("\n=== Test 9 completed successfully! ===\n");
    }

    // ==================== Cross-Mode Comparison Tests ====================

    @Test
    @Order(10)
    void allModes_sameFlow_produceSameResults() throws Exception {
        System.out.println("=== Test 10: Cross-Mode Comparison ===");

        // Create the same flow for each mode
        java.util.function.Supplier<TxFlow> flowSupplier = () -> TxFlow.builder("cross-mode-" + System.currentTimeMillis())
                .withDescription("Cross-mode comparison test")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (1.1 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(1.1))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        // Execute in SEQUENTIAL mode
        System.out.println("Executing in SEQUENTIAL mode...");
        FlowResult sequentialResult = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.SEQUENTIAL)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flowSupplier.get());

        // Execute in PIPELINED mode
        System.out.println("Executing in PIPELINED mode...");
        FlowResult pipelinedResult = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.PIPELINED)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flowSupplier.get());

        // Execute in BATCH mode
        System.out.println("Executing in BATCH mode...");
        FlowResult batchResult = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.BATCH)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flowSupplier.get());

        // All modes should succeed
        assertTrue(sequentialResult.isSuccessful(), "SEQUENTIAL mode should succeed");
        assertTrue(pipelinedResult.isSuccessful(), "PIPELINED mode should succeed");
        assertTrue(batchResult.isSuccessful(), "BATCH mode should succeed");

        // All modes should produce same step count
        assertEquals(sequentialResult.getCompletedStepCount(), pipelinedResult.getCompletedStepCount());
        assertEquals(pipelinedResult.getCompletedStepCount(), batchResult.getCompletedStepCount());

        // All modes should produce same number of transactions
        assertEquals(sequentialResult.getTransactionHashes().size(), pipelinedResult.getTransactionHashes().size());
        assertEquals(pipelinedResult.getTransactionHashes().size(), batchResult.getTransactionHashes().size());

        System.out.println("\nAll modes produced equivalent results:");
        System.out.println("  SEQUENTIAL: " + sequentialResult.getCompletedStepCount() + " steps");
        System.out.println("  PIPELINED:  " + pipelinedResult.getCompletedStepCount() + " steps");
        System.out.println("  BATCH:      " + batchResult.getCompletedStepCount() + " steps");

        System.out.println("\n=== Test 10 completed successfully! ===\n");
    }

    @Test
    @Order(11)
    void pipelined_withStepListener_tracksAllEvents() throws Exception {
        System.out.println("=== Test 11: PIPELINED - Event Tracking ===");

        AtomicInteger stepStartedCount = new AtomicInteger(0);
        AtomicInteger stepCompletedCount = new AtomicInteger(0);
        AtomicInteger txSubmittedCount = new AtomicInteger(0);
        AtomicInteger txConfirmedCount = new AtomicInteger(0);

        FlowListener trackingListener = new FlowListener() {
            @Override
            public void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
                stepStartedCount.incrementAndGet();
                System.out.println("  [EVENT] Step started: " + step.getId() + " (" + (stepIndex + 1) + "/" + totalSteps + ")");
            }

            @Override
            public void onStepCompleted(FlowStep step, FlowStepResult result) {
                stepCompletedCount.incrementAndGet();
                System.out.println("  [EVENT] Step completed: " + step.getId());
            }

            @Override
            public void onTransactionSubmitted(FlowStep step, String txHash) {
                txSubmittedCount.incrementAndGet();
                System.out.println("  [EVENT] Transaction submitted: " + txHash.substring(0, 16) + "...");
            }

            @Override
            public void onTransactionConfirmed(FlowStep step, String txHash) {
                txConfirmedCount.incrementAndGet();
                System.out.println("  [EVENT] Transaction confirmed: " + txHash.substring(0, 16) + "...");
            }

            @Override
            public void onFlowCompleted(TxFlow flow, FlowResult result) {
                System.out.println("  [EVENT] Flow completed: " + flow.getId());
            }
        };

        TxFlow flow = TxFlow.builder("pipelined-events")
                .withDescription("Event tracking test")
                .addStep(FlowStep.builder("event-step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(1.4))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("event-step2")
                        .dependsOn("event-step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(1))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        FlowResult result = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.PIPELINED)
                .withListener(trackingListener)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful());

        // Verify all events were fired
        assertEquals(2, stepStartedCount.get(), "All steps should have started");
        assertEquals(2, stepCompletedCount.get(), "All steps should have completed");
        assertEquals(2, txSubmittedCount.get(), "All transactions should have been submitted");
        assertEquals(2, txConfirmedCount.get(), "All transactions should have been confirmed");

        System.out.println("\nEvent counts:");
        System.out.println("  Steps started: " + stepStartedCount.get());
        System.out.println("  Steps completed: " + stepCompletedCount.get());
        System.out.println("  Transactions submitted: " + txSubmittedCount.get());
        System.out.println("  Transactions confirmed: " + txConfirmedCount.get());

        System.out.println("\n=== Test 11 completed successfully! ===\n");
    }

    @Test
    @Order(12)
    void batch_withStepListener_tracksAllEvents() throws Exception {
        System.out.println("=== Test 12: BATCH - Event Tracking ===");

        AtomicInteger stepStartedCount = new AtomicInteger(0);
        AtomicInteger stepCompletedCount = new AtomicInteger(0);
        AtomicInteger txSubmittedCount = new AtomicInteger(0);
        AtomicInteger txConfirmedCount = new AtomicInteger(0);

        FlowListener trackingListener = new FlowListener() {
            @Override
            public void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
                stepStartedCount.incrementAndGet();
                System.out.println("  [EVENT] Step started: " + step.getId());
            }

            @Override
            public void onStepCompleted(FlowStep step, FlowStepResult result) {
                stepCompletedCount.incrementAndGet();
                System.out.println("  [EVENT] Step completed: " + step.getId());
            }

            @Override
            public void onTransactionSubmitted(FlowStep step, String txHash) {
                txSubmittedCount.incrementAndGet();
                System.out.println("  [EVENT] Transaction submitted: " + txHash.substring(0, 16) + "...");
            }

            @Override
            public void onTransactionConfirmed(FlowStep step, String txHash) {
                txConfirmedCount.incrementAndGet();
                System.out.println("  [EVENT] Transaction confirmed: " + txHash.substring(0, 16) + "...");
            }

            @Override
            public void onFlowCompleted(TxFlow flow, FlowResult result) {
                System.out.println("  [EVENT] Flow completed: " + flow.getId());
            }
        };

        TxFlow flow = TxFlow.builder("batch-events")
                .withDescription("BATCH event tracking test")
                .addStep(FlowStep.builder("batch-event-step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(1.5))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("batch-event-step2")
                        .dependsOn("batch-event-step1")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(1.1))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        FlowResult result = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.BATCH)
                .withListener(trackingListener)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .executeSync(flow);

        assertTrue(result.isSuccessful());

        // Verify all events were fired
        assertEquals(2, stepStartedCount.get(), "All steps should have started");
        assertEquals(2, stepCompletedCount.get(), "All steps should have completed");
        assertEquals(2, txSubmittedCount.get(), "All transactions should have been submitted");
        assertEquals(2, txConfirmedCount.get(), "All transactions should have been confirmed");

        System.out.println("\nEvent counts:");
        System.out.println("  Steps started: " + stepStartedCount.get());
        System.out.println("  Steps completed: " + stepCompletedCount.get());
        System.out.println("  Transactions submitted: " + txSubmittedCount.get());
        System.out.println("  Transactions confirmed: " + txConfirmedCount.get());

        System.out.println("\n=== Test 12 completed successfully! ===\n");
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
