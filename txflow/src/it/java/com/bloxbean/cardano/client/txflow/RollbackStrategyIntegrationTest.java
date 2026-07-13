package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.api.DefaultChainDataSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.exec.*;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for rollback handling strategies using Yaci DevKit.
 * <p>
 * These tests verify that the FlowExecutor correctly handles chain rollbacks
 * according to the configured {@link RollbackStrategy}:
 * <ul>
 *     <li>FAIL_IMMEDIATELY - Flow fails when rollback is detected</li>
 *     <li>NOTIFY_ONLY - Listener is notified but flow continues waiting</li>
 *     <li>REBUILD_FROM_FAILED - Only the rolled-back step is rebuilt</li>
 *     <li>REBUILD_ENTIRE_FLOW - The entire flow is restarted</li>
 * </ul>
 * <p>
 * Prerequisites:
 * <ul>
 *     <li>Yaci DevKit running at http://localhost:8080/api/v1/</li>
 *     <li>Yaci DevKit Admin API at http://localhost:10000/</li>
 *     <li>Run with: ./gradlew :txflow:integrationTest --tests "RollbackStrategyIntegrationTest" -Dyaci.integration.test=true</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RollbackStrategyIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private ExecutorService asyncExecutor;

    // Pre-funded accounts from Yaci DevKit
    private Account account0; // Index 0 - Primary sender
    private Account account1; // Index 111 - Primary receiver
    private Account account2; // Index 2 - Secondary account

    @BeforeAll
    void initBackend() {
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");
    }

    @BeforeEach
    void setUp() {
        System.out.println("\n=== Rollback Strategy Integration Test Setup ===");

        asyncExecutor = Executors.newCachedThreadPool();

        // Reset devnet to clean state before each test
        System.out.println("Resetting devnet to clean state...");
        assertTrue(RollbackTestHelper.resetDevnet(), "Should reset devnet");
        assertTrue(RollbackTestHelper.waitForNodeReady(backendService, 30),
                "Node should be ready after reset");

        // Wait for a couple blocks to be produced after reset
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create accounts from default mnemonic at different indices
        account0 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 0);
        account1 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 111);
        account2 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 2);

        System.out.println("Using pre-funded Yaci DevKit accounts:");
        System.out.println("  Account 0: " + account0.baseAddress());
        System.out.println("  Account 1: " + account1.baseAddress());
        System.out.println("  Account 2: " + account2.baseAddress());

        // Verify accounts have funds
        try {
            List<Utxo> utxos0 = backendService.getUtxoService()
                    .getUtxos(account0.baseAddress(), 100, 1)
                    .getValue();
            System.out.println("  Account 0 UTXOs: " + utxos0.size());
        } catch (Exception e) {
            System.err.println("Warning: Could not verify account balances: " + e.getMessage());
        }

        System.out.println("Setup complete!\n");
    }

    private FlowExecutor createYaciExecutor() {
        return FlowExecutor.create(
                new DefaultUtxoSupplier(backendService.getUtxoService()),
                new DefaultProtocolParamsSupplier(backendService.getEpochService()),
                new DefaultTransactionProcessor(backendService.getTransactionService()),
                ObservationCapabilities.withAuthoritativeAbsence(
                        new DefaultChainDataSupplier(backendService)))
                .withExecutor(asyncExecutor);
    }

    private FlowResult awaitFlowResult(Future<FlowResult> future, Duration timeout)
            throws Exception {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutFailure) {
            future.cancel(true);
            throw new AssertionError(
                    "Flow did not reach its required terminal outcome within " + timeout,
                    timeoutFailure);
        }
    }

    private void shutdownExecutor(ExecutorService executorService) throws InterruptedException {
        executorService.shutdownNow();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS),
                "Application-owned test executor should terminate cleanly");
    }

    private String errorMessage(FlowResult result) {
        return result.getError() != null ? result.getError().getMessage() : "none";
    }

    /**
     * Verify that a transaction exists on chain.
     * Retries a few times to allow for indexer lag.
     *
     * @param txHash the transaction hash to verify
     * @return true if transaction exists on chain, false otherwise
     */
    private boolean verifyTransactionOnChain(String txHash) {
        int maxAttempts = 10;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var result = backendService.getTransactionService().getTransaction(txHash);
                boolean exists = result.isSuccessful() && result.getValue() != null;
                if (exists) {
                    System.out.println("  Transaction " + txHash + " found on chain (attempt " + attempt + ")");
                    return true;
                }
                if (attempt < maxAttempts) {
                    System.out.println("  Transaction " + txHash + " not found yet (attempt " + attempt + "), retrying...");
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                System.out.println("  Error checking transaction (attempt " + attempt + "): " + e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        System.out.println("  Transaction " + txHash + " NOT found on chain after " + maxAttempts + " attempts");
        return false;
    }

    /**
     * Test that confirmation tracking works correctly without rollback.
     * This verifies the basic confirmation tracking functionality.
     * Run this first to ensure basic flow execution works.
     */
    @Test
    @Order(1)
    void testConfirmationTrackingWithoutRollback() throws Exception {
        System.out.println("=== Test 1: Confirmation Tracking Without Rollback ===");

        RollbackListenerTracker listener = new RollbackListenerTracker();
        AtomicInteger inBlockCallbacks = new AtomicInteger(0);
        AtomicInteger depthChangedCallbacks = new AtomicInteger(0);

        FlowListener trackingListener = new FlowListener() {
            @Override
            public void onTransactionInBlock(FlowStep step, String transactionHash, long blockHeight) {
                inBlockCallbacks.incrementAndGet();
                System.out.println("  Transaction in block " + blockHeight + ": " + transactionHash);
            }

            @Override
            public void onConfirmationDepthChanged(FlowStep step, String transactionHash, int depth, ConfirmationStatus status) {
                depthChangedCallbacks.incrementAndGet();
                System.out.println("  Depth changed: " + depth + " (" + status + ")");
            }

            @Override
            public void onTransactionConfirmed(FlowStep step, String transactionHash) {
                System.out.println("  Transaction confirmed: " + transactionHash);
            }

            @Override
            public void onTransactionRolledBack(FlowStep step, String transactionHash, long previousBlockHeight) {
                listener.onTransactionRolledBack(step, transactionHash, previousBlockHeight);
            }

            @Override
            public void onFlowCompleted(TxFlow flow, FlowResult result) {
                System.out.println("  Flow completed: " + flow.getId());
            }

            @Override
            public void onFlowFailed(TxFlow flow, FlowResult result) {
                System.out.println("  Flow failed: " + flow.getId() +
                    (result.getError() != null ? " - " + result.getError().getMessage() : ""));
            }
        };

        // Use simple confirmation config without complex tracking
        ConfirmationConfig simpleConfig = ConfirmationConfig.builder()
                .minConfirmations(1)  // Very low threshold

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(2))
                .build();

        TxFlow flow = TxFlow.builder("tracking-test")
                .withDescription("Test confirmation tracking")
                .addStep(FlowStep.builder("payment")
                        .withDescription("Send 2 ADA")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowExecutor executor = createYaciExecutor()
                .withConfirmationConfig(simpleConfig)
                .withRollbackStrategy(RollbackStrategy.FAIL_IMMEDIATELY)
                .withListener(trackingListener);

        FlowResult result = executor.executeSync(flow);

        System.out.println("Flow result: " + result.getStatus());
        System.out.println("In-block callbacks: " + inBlockCallbacks.get());
        System.out.println("Depth changed callbacks: " + depthChangedCallbacks.get());
        System.out.println("Rollback callbacks: " + listener.getRolledBackTxHashes().size());

        assertTrue(result.isSuccessful(), "Flow should complete successfully. Error: " +
                (result.getError() != null ? result.getError().getMessage() : "none"));
        assertEquals(0, listener.getRolledBackTxHashes().size(),
                "No rollback should be detected without rollback trigger");

        // Verify transaction exists on chain
        assertFalse(result.getTransactionHashes().isEmpty(), "Flow should have transaction hash");
        String txHash = result.getTransactionHashes().get(0);
        assertTrue(verifyTransactionOnChain(txHash), "Transaction should exist on chain");

        System.out.println("\n=== Test 1 completed successfully! ===\n");
    }

    /**
     * Test FAIL_IMMEDIATELY strategy - flow should fail when rollback is detected.
     */
    @Test
    @Order(2)
    void testFailImmediatelyStrategy() throws Exception {
        System.out.println("=== Test 2: FAIL_IMMEDIATELY Strategy ===");

        RollbackListenerTracker listener = new RollbackListenerTracker();

        // Use higher confirmation threshold so we can trigger rollback during wait
        ConfirmationConfig rollbackConfig = ConfirmationConfig.builder()
                .minConfirmations(5)   // Higher threshold to give time for rollback

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(2))
                .maxRollbackRetries(3)
                .waitForBackendAfterRollback(true)        // Enable for Yaci DevKit tests
                .postRollbackWaitAttempts(30)             // Full wait
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))  // UTXO sync delay
                .build();

        // Create a single-step flow
        TxFlow flow = TxFlow.builder("fail-immediately-test")
                .withDescription("Test FAIL_IMMEDIATELY rollback strategy")
                .addStep(FlowStep.builder("payment")
                        .withDescription("Send 2 ADA")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        // Execute flow with FAIL_IMMEDIATELY strategy
        FlowExecutor executor = createYaciExecutor()
                .withConfirmationConfig(rollbackConfig)
                .withRollbackStrategy(RollbackStrategy.FAIL_IMMEDIATELY)
                .withListener(listener);

        // Take snapshot before any transactions
        assertTrue(RollbackTestHelper.takeDbSnapshot(), "Should take DB snapshot");

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<FlowResult> future = executorService.submit(() -> executor.executeSync(flow));
        try {
            assertTrue(listener.awaitFirstInclusion(Duration.ofSeconds(30)),
                    "Transaction should enter a block before rollback");

            System.out.println("Triggering rollback...");
            assertTrue(RollbackTestHelper.rollbackToSnapshot(), "Should rollback to snapshot");
            assertTrue(RollbackTestHelper.waitForNodeReady(backendService, 30),
                    "Node should be ready after rollback");

            FlowResult result = awaitFlowResult(future, Duration.ofSeconds(90));
            System.out.println("Flow result: " + result.getStatus());
            System.out.println("Rollback callbacks: " + listener.getRolledBackTxHashes().size());

            assertTrue(result.isFailed(),
                    "FAIL_IMMEDIATELY must fail after an observed rollback");
            assertFalse(listener.getRolledBackTxHashes().isEmpty(),
                    "FAIL_IMMEDIATELY must report the rollback");
            assertInstanceOf(RollbackException.class, result.getError(),
                    "Rollback must remain a typed failure");
        } finally {
            future.cancel(true);
            shutdownExecutor(executorService);
        }

        System.out.println("\n=== Test 2 completed successfully! ===\n");
    }

    /**
     * Test REBUILD_FROM_FAILED strategy with a single step.
     * The step should be automatically rebuilt after rollback.
     */
    @Test
    @Order(3)
    void testRebuildFromFailedStrategy_SingleStep() throws Exception {
        System.out.println("=== Test 3: REBUILD_FROM_FAILED Strategy (Single Step) ===");

        AtomicBoolean txSubmitted = new AtomicBoolean(false);
        RollbackListenerTracker listener = new RollbackListenerTracker() {
            @Override
            public void onTransactionSubmitted(FlowStep step, String transactionHash) {
                super.onTransactionSubmitted(step, transactionHash);
                txSubmitted.set(true);
            }
        };

        // Use high confirmation threshold so we have time to trigger rollback before completion
        // Devnet blocks are ~1 second, so 30 confirmations = ~30 seconds
        ConfirmationConfig rollbackConfig = ConfirmationConfig.builder()
                .minConfirmations(30)  // High threshold - flow won't complete quickly

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(3))
                .maxRollbackRetries(3)
                .waitForBackendAfterRollback(true)        // Enable for Yaci DevKit tests
                .postRollbackWaitAttempts(30)             // Full wait
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))  // UTXO sync delay
                .build();

        // Create a single-step flow
        TxFlow flow = TxFlow.builder("rebuild-single-step-test")
                .withDescription("Test REBUILD_FROM_FAILED with single step")
                .addStep(FlowStep.builder("payment")
                        .withDescription("Send 2 ADA")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        // PIPELINED recovery restarts its scheduling pass after reconciling the rolled-back attempt.
        FlowExecutor executor = createYaciExecutor()
                .withChainingMode(ChainingMode.PIPELINED)
                .withConfirmationConfig(rollbackConfig)
                .withRollbackStrategy(RollbackStrategy.REBUILD_FROM_FAILED)
                .withListener(listener);

        // Take snapshot before any transactions
        assertTrue(RollbackTestHelper.takeDbSnapshot(), "Should take DB snapshot");

        // Execute in background
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<FlowResult> future = executorService.submit(() -> executor.executeSync(flow));

        try {
            assertTrue(txSubmitted.get() || listener.awaitFirstSubmission(Duration.ofSeconds(30)),
                    "Transaction should have been submitted");
            assertTrue(listener.awaitFirstInclusion(Duration.ofSeconds(30)),
                    "Transaction should enter a block before rollback");

            System.out.println("Triggering rollback...");
            assertTrue(RollbackTestHelper.rollbackToSnapshot(), "Should rollback to snapshot");
            assertTrue(RollbackTestHelper.waitForNodeReady(backendService, 30),
                    "Node should be ready after rollback");

            FlowResult result = awaitFlowResult(future, Duration.ofSeconds(180));

            System.out.println("Flow result: " + result.getStatus());
            System.out.println("Flow restart attempts: " + listener.getRestartAttempts());
            System.out.println("Rollback notifications: " + listener.getRolledBackTxHashes().size());

            assertTrue(result.isSuccessful(),
                    "PIPELINED recovery must finish after rebuilding invalidated work: "
                            + errorMessage(result));
            assertFalse(listener.getRolledBackTxHashes().isEmpty(),
                    "Rollback must be observed before recovery");
            assertTrue(listener.getRestartAttempts() > 0,
                    "PIPELINED recovery must restart its scheduling pass");
            assertFalse(result.getTransactionHashes().isEmpty(),
                    "Recovered flow should have a final transaction hash");
            String finalTxHash = result.getTransactionHashes()
                    .get(result.getTransactionHashes().size() - 1);
            assertTrue(verifyTransactionOnChain(finalTxHash),
                    "Rebuilt transaction should exist on chain");
        } finally {
            future.cancel(true);
            shutdownExecutor(executorService);
        }

        System.out.println("\n=== Test 3 completed successfully! ===\n");
    }

    /**
     * Test REBUILD_ENTIRE_FLOW strategy.
     * The entire flow should be restarted when rollback is detected.
     */
    @Test
    @Order(4)
    void testRebuildEntireFlowStrategy() throws Exception {
        System.out.println("=== Test 4: REBUILD_ENTIRE_FLOW Strategy ===");

        AtomicBoolean txSubmitted = new AtomicBoolean(false);
        RollbackListenerTracker listener = new RollbackListenerTracker() {
            @Override
            public void onTransactionSubmitted(FlowStep step, String transactionHash) {
                super.onTransactionSubmitted(step, transactionHash);
                txSubmitted.set(true);
            }
        };

        // Use high confirmation threshold so we have time to trigger rollback before completion
        ConfirmationConfig rollbackConfig = ConfirmationConfig.builder()
                .minConfirmations(30)  // High threshold - flow won't complete quickly

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(3))
                .maxRollbackRetries(3)
                .waitForBackendAfterRollback(true)        // Enable for Yaci DevKit tests
                .postRollbackWaitAttempts(30)             // Full wait
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))  // UTXO sync delay
                .build();

        // Create a single-step flow (multi-step would need more careful UTXO handling after reset)
        TxFlow flow = TxFlow.builder("rebuild-entire-flow-test")
                .withDescription("Test REBUILD_ENTIRE_FLOW strategy")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (2 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        // Use PIPELINED mode which properly uses ConfirmationTracker for rollback detection
        FlowExecutor executor = createYaciExecutor()
                .withChainingMode(ChainingMode.PIPELINED)
                .withConfirmationConfig(rollbackConfig)
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .withListener(listener);

        // Take snapshot before any transactions
        assertTrue(RollbackTestHelper.takeDbSnapshot(), "Should take DB snapshot");

        // Execute in background
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<FlowResult> future = executorService.submit(() -> executor.executeSync(flow));

        try {
            assertTrue(txSubmitted.get() || listener.awaitFirstSubmission(Duration.ofSeconds(30)),
                    "Transaction should have been submitted");
            assertTrue(listener.awaitFirstInclusion(Duration.ofSeconds(30)),
                    "Transaction should enter a block before rollback");

            System.out.println("Triggering rollback...");
            assertTrue(RollbackTestHelper.rollbackToSnapshot(), "Should rollback to snapshot");
            assertTrue(RollbackTestHelper.waitForNodeReady(backendService, 30),
                    "Node should be ready after rollback");

            FlowResult result = awaitFlowResult(future, Duration.ofSeconds(180));

            System.out.println("Flow result: " + result.getStatus());
            System.out.println("Flow restart attempts: " + listener.getRestartAttempts());
            System.out.println("Rollback notifications: " + listener.getRolledBackTxHashes().size());

            assertTrue(result.isSuccessful(),
                    "REBUILD_ENTIRE_FLOW must complete after restart: " + errorMessage(result));
            assertFalse(listener.getRolledBackTxHashes().isEmpty(),
                    "Rollback must be observed before restart");
            assertTrue(listener.getRestartAttempts() > 0,
                    "REBUILD_ENTIRE_FLOW must restart the flow");
            assertFalse(result.getTransactionHashes().isEmpty(),
                    "Restarted flow should have a final transaction hash");
            String finalTxHash = result.getTransactionHashes()
                    .get(result.getTransactionHashes().size() - 1);
            assertTrue(verifyTransactionOnChain(finalTxHash),
                    "Final transaction should exist on chain after flow restart");
        } finally {
            future.cancel(true);
            shutdownExecutor(executorService);
        }

        System.out.println("\n=== Test 4 completed successfully! ===\n");
    }

    /**
     * Test max rollback retries exceeded.
     * Flow should fail after exceeding the configured max retry limit.
     */
    @Test
    @Order(5)
    void testMaxRollbackRetriesExceeded() throws Exception {
        System.out.println("=== Test 5: Max Rollback Retries Exceeded ===");

        RollbackListenerTracker listener = new RollbackListenerTracker();

        // Zero recovery cycles makes the first observed rollback exhaust the budget.
        ConfirmationConfig limitedRetryConfig = ConfirmationConfig.builder()
                .minConfirmations(30)

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(2))
                .maxRollbackRetries(0)
                .waitForBackendAfterRollback(true)        // Enable for Yaci DevKit tests
                .postRollbackWaitAttempts(30)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
                .build();

        TxFlow flow = TxFlow.builder("max-retries-test")
                .withDescription("Test max rollback retries")
                .addStep(FlowStep.builder("payment")
                        .withDescription("Send 2 ADA")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowExecutor executor = createYaciExecutor()
                .withChainingMode(ChainingMode.PIPELINED)
                .withConfirmationConfig(limitedRetryConfig)
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .withListener(listener);

        assertTrue(RollbackTestHelper.takeDbSnapshot(), "Should take DB snapshot");

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<FlowResult> future = executorService.submit(() -> executor.executeSync(flow));
        try {
            assertTrue(listener.awaitFirstInclusion(Duration.ofSeconds(30)),
                    "Transaction should enter a block before rollback");
            assertTrue(RollbackTestHelper.rollbackToSnapshot(), "Should rollback to snapshot");
            assertTrue(RollbackTestHelper.waitForNodeReady(backendService, 30),
                    "Node should be ready after rollback");

            FlowResult result = awaitFlowResult(future, Duration.ofSeconds(90));
            assertTrue(result.isFailed(),
                    "Exhausted rollback budget must fail the flow");
            assertFalse(listener.getRolledBackTxHashes().isEmpty(),
                    "Budget exhaustion must follow an observed rollback");
            assertEquals(0, listener.getRestartAttempts(),
                    "No restart is allowed when maxRollbackRetries is zero");
            assertNotNull(result.getError(), "Budget exhaustion must expose an error");
            assertTrue(result.getError().getMessage().contains("restart limit"),
                    "Failure should identify rollback restart-budget exhaustion");
        } finally {
            future.cancel(true);
            shutdownExecutor(executorService);
        }

        System.out.println("\n=== Test 5 completed successfully! ===\n");
    }

    /**
     * Test NOTIFY_ONLY strategy.
     * Listener should be notified of rollback but flow should continue waiting.
     */
    @Test
    @Order(6)
    void testNotifyOnlyStrategy() throws Exception {
        System.out.println("=== Test 6: NOTIFY_ONLY Strategy ===");

        RollbackListenerTracker listener = new RollbackListenerTracker();

        // Use a shorter timeout for this test
        ConfirmationConfig shortTimeoutConfig = ConfirmationConfig.builder()
                .minConfirmations(5)

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(30))  // Short timeout
                .maxRollbackRetries(3)
                .waitForBackendAfterRollback(true)        // Enable for Yaci DevKit tests
                .postRollbackWaitAttempts(30)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
                .build();

        TxFlow flow = TxFlow.builder("notify-only-test")
                .withDescription("Test NOTIFY_ONLY rollback strategy")
                .addStep(FlowStep.builder("payment")
                        .withDescription("Send 2 ADA")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowExecutor executor = createYaciExecutor()
                .withConfirmationConfig(shortTimeoutConfig)
                .withRollbackStrategy(RollbackStrategy.NOTIFY_ONLY)
                .withListener(listener);

        // Take snapshot before any transactions
        assertTrue(RollbackTestHelper.takeDbSnapshot(), "Should take DB snapshot");

        // Execute in background
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<FlowResult> future = executorService.submit(() -> executor.executeSync(flow));

        try {
            assertTrue(listener.awaitFirstInclusion(Duration.ofSeconds(30)),
                    "Transaction should enter a block before rollback");

            System.out.println("Triggering rollback...");
            assertTrue(RollbackTestHelper.rollbackToSnapshot(), "Should rollback to snapshot");
            assertTrue(RollbackTestHelper.waitForNodeReady(backendService, 30),
                    "Node should be ready after rollback");

            FlowResult result = awaitFlowResult(future, Duration.ofSeconds(60));
            System.out.println("Flow result: " + result.getStatus());
            System.out.println("Rollback notifications: " + listener.getRolledBackTxHashes().size());

            assertTrue(result.isFailed(),
                    "Exhausted NOTIFY_ONLY reconciliation must end in a typed failure");
            assertFalse(listener.getRolledBackTxHashes().isEmpty(),
                    "NOTIFY_ONLY must notify the listener of rollback");
            assertInstanceOf(RollbackException.class, result.getError(),
                    "Rollback must not be converted to a confirmation timeout");
        } finally {
            future.cancel(true);
            shutdownExecutor(executorService);
        }

        System.out.println("\n=== Test 6 completed successfully! ===\n");
    }

    /**
     * Test multi-step flow without rollback to verify basic chaining works.
     */
    @Test
    @Order(7)
    void testMultiStepFlowWithoutRollback() throws Exception {
        System.out.println("=== Test 7: Multi-Step Flow Without Rollback ===");

        RollbackListenerTracker listener = new RollbackListenerTracker();

        ConfirmationConfig simpleConfig = ConfirmationConfig.builder()
                .minConfirmations(1)

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(2))
                .waitForBackendAfterRollback(true)        // Enable for Yaci DevKit tests
                .postRollbackWaitAttempts(30)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
                .build();

        // Create a two-step flow
        TxFlow flow = TxFlow.builder("multi-step-test")
                .withDescription("Test multi-step flow")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (3 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(3))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Account1 -> Account2 (1 ADA)")
                        .dependsOn("step1", SelectionStrategy.ALL)
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(1))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        FlowExecutor executor = createYaciExecutor()
                .withConfirmationConfig(simpleConfig)
                .withRollbackStrategy(RollbackStrategy.REBUILD_FROM_FAILED)
                .withListener(listener);

        // Execute flow (no rollback trigger)
        FlowResult result = executor.executeSync(flow);

        System.out.println("Flow result: " + result.getStatus());
        System.out.println("Completed steps: " + result.getCompletedStepCount());

        assertTrue(result.isSuccessful(), "Multi-step flow should complete successfully. Error: " +
                (result.getError() != null ? result.getError().getMessage() : "none"));
        assertEquals(2, result.getCompletedStepCount(), "Both steps should complete");
        assertEquals(0, listener.getRebuildAttempts(), "No rebuilds should occur");

        // Verify all transactions exist on chain
        assertEquals(2, result.getTransactionHashes().size(), "Should have 2 transaction hashes");
        for (String txHash : result.getTransactionHashes()) {
            assertTrue(verifyTransactionOnChain(txHash), "Transaction " + txHash + " should exist on chain");
        }

        System.out.println("\n=== Test 7 completed successfully! ===\n");
    }

    /**
     * Test BATCH mode with REBUILD_ENTIRE_FLOW strategy.
     * When a rollback is detected, all transactions in the batch should be
     * rebuilt and resubmitted.
     */
    @Test
    @Order(8)
    void testBatchModeRollback_rebuildEntireFlow() throws Exception {
        System.out.println("=== Test 8: BATCH Mode + REBUILD_ENTIRE_FLOW ===");

        AtomicBoolean txSubmitted = new AtomicBoolean(false);
        RollbackListenerTracker listener = new RollbackListenerTracker() {
            @Override
            public void onTransactionSubmitted(FlowStep step, String transactionHash) {
                super.onTransactionSubmitted(step, transactionHash);
                txSubmitted.set(true);
            }
        };

        ConfirmationConfig rollbackConfig = ConfirmationConfig.builder()
                .minConfirmations(30)

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(3))
                .maxRollbackRetries(3)
                .waitForBackendAfterRollback(true)
                .postRollbackWaitAttempts(30)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
                .build();

        TxFlow flow = TxFlow.builder("batch-rollback-test")
                .withDescription("Test BATCH mode with REBUILD_ENTIRE_FLOW")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (2 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowExecutor executor = createYaciExecutor()
                .withChainingMode(ChainingMode.BATCH)
                .withConfirmationConfig(rollbackConfig)
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .withListener(listener);

        assertTrue(RollbackTestHelper.takeDbSnapshot(), "Should take DB snapshot");

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<FlowResult> future = executorService.submit(() -> executor.executeSync(flow));

        try {
            assertTrue(txSubmitted.get() || listener.awaitFirstSubmission(Duration.ofSeconds(30)),
                    "Transaction should have been submitted");
            assertTrue(listener.awaitFirstInclusion(Duration.ofSeconds(30)),
                    "Transaction should enter a block before rollback");

            System.out.println("Triggering rollback...");
            assertTrue(RollbackTestHelper.rollbackToSnapshot(), "Should rollback to snapshot");
            assertTrue(RollbackTestHelper.waitForNodeReady(backendService, 30),
                    "Node should be ready after rollback");

            FlowResult result = awaitFlowResult(future, Duration.ofSeconds(180));
            System.out.println("Flow result: " + result.getStatus());
            System.out.println("Flow restart attempts: " + listener.getRestartAttempts());
            System.out.println("Rollback notifications: " + listener.getRolledBackTxHashes().size());

            assertTrue(result.isSuccessful(),
                    "BATCH REBUILD_ENTIRE_FLOW must complete after restart: " + errorMessage(result));
            assertFalse(listener.getRolledBackTxHashes().isEmpty(),
                    "BATCH mode must observe the rollback");
            assertTrue(listener.getRestartAttempts() > 0,
                    "BATCH REBUILD_ENTIRE_FLOW must restart the scheduling pass");
            assertFalse(result.getTransactionHashes().isEmpty(),
                    "Restarted BATCH flow should have a final transaction hash");
            String finalTxHash = result.getTransactionHashes()
                    .get(result.getTransactionHashes().size() - 1);
            assertTrue(verifyTransactionOnChain(finalTxHash),
                    "Final transaction should exist on chain after BATCH restart");
        } finally {
            future.cancel(true);
            shutdownExecutor(executorService);
        }

        System.out.println("\n=== Test 8 completed successfully! ===\n");
    }

    /**
     * Test SEQUENTIAL mode with REBUILD_ENTIRE_FLOW strategy.
     * SEQUENTIAL uses completeAndWait() and then ConfirmationTracker for
     * additional confirmation depth tracking.
     */
    @Test
    @Order(9)
    void testSequentialModeRollback_rebuildEntireFlow() throws Exception {
        System.out.println("=== Test 9: SEQUENTIAL Mode + REBUILD_ENTIRE_FLOW ===");

        AtomicBoolean txSubmitted = new AtomicBoolean(false);
        RollbackListenerTracker listener = new RollbackListenerTracker() {
            @Override
            public void onTransactionSubmitted(FlowStep step, String transactionHash) {
                super.onTransactionSubmitted(step, transactionHash);
                txSubmitted.set(true);
            }
        };

        ConfirmationConfig rollbackConfig = ConfirmationConfig.builder()
                .minConfirmations(30)

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(3))
                .maxRollbackRetries(3)
                .waitForBackendAfterRollback(true)
                .postRollbackWaitAttempts(30)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
                .build();

        TxFlow flow = TxFlow.builder("sequential-rollback-test")
                .withDescription("Test SEQUENTIAL mode with REBUILD_ENTIRE_FLOW")
                .addStep(FlowStep.builder("payment")
                        .withDescription("Send 2 ADA")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        // Explicitly use SEQUENTIAL mode (the default)
        FlowExecutor executor = createYaciExecutor()
                .withChainingMode(ChainingMode.SEQUENTIAL)
                .withConfirmationConfig(rollbackConfig)
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .withListener(listener);

        assertTrue(RollbackTestHelper.takeDbSnapshot(), "Should take DB snapshot");

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<FlowResult> future = executorService.submit(() -> executor.executeSync(flow));

        try {
            assertTrue(txSubmitted.get() || listener.awaitFirstSubmission(Duration.ofSeconds(30)),
                    "Transaction should have been submitted");
            assertTrue(listener.awaitFirstInclusion(Duration.ofSeconds(30)),
                    "Transaction should enter a block before rollback");

            System.out.println("Triggering rollback...");
            assertTrue(RollbackTestHelper.rollbackToSnapshot(), "Should rollback to snapshot");
            assertTrue(RollbackTestHelper.waitForNodeReady(backendService, 30),
                    "Node should be ready after rollback");

            FlowResult result = awaitFlowResult(future, Duration.ofSeconds(180));
            System.out.println("Flow result: " + result.getStatus());
            System.out.println("Flow restart attempts: " + listener.getRestartAttempts());
            System.out.println("Rollback notifications: " + listener.getRolledBackTxHashes().size());

            assertTrue(result.isSuccessful(),
                    "SEQUENTIAL REBUILD_ENTIRE_FLOW must complete after restart: "
                            + errorMessage(result));
            assertFalse(listener.getRolledBackTxHashes().isEmpty(),
                    "SEQUENTIAL mode must observe the rollback");
            assertTrue(listener.getRestartAttempts() > 0,
                    "SEQUENTIAL REBUILD_ENTIRE_FLOW must restart the flow");
            assertFalse(result.getTransactionHashes().isEmpty(),
                    "Restarted flow should expose its final transaction hash");
        } finally {
            future.cancel(true);
            shutdownExecutor(executorService);
        }

        System.out.println("\n=== Test 9 completed successfully! ===\n");
    }

    /**
     * Test FlowHandle cancellation stops flow execution.
     * <p>
     * Uses SEQUENTIAL mode with high minConfirmations so the flow spends a long
     * time waiting for step 1's confirmation. We cancel during that wait.
     * After step 1 eventually confirms, the loop checks isCancelled() before
     * step 2 and returns a CANCELLED result.
     */
    @Test
    @Order(10)
    void testCancellation_stopsExecution() throws Exception {
        System.out.println("=== Test 10: FlowHandle Cancellation ===");

        AtomicBoolean txSubmitted = new AtomicBoolean(false);
        AtomicBoolean secondStepStarted = new AtomicBoolean(false);
        RollbackListenerTracker listener = new RollbackListenerTracker() {
            @Override
            public void onTransactionSubmitted(FlowStep step, String transactionHash) {
                super.onTransactionSubmitted(step, transactionHash);
                if ("step1".equals(step.getId())) {
                    txSubmitted.set(true);
                }
            }

            @Override
            public void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
                super.onStepStarted(step, stepIndex, totalSteps);
                if ("step2".equals(step.getId())) {
                    secondStepStarted.set(true);
                }
            }
        };

        // Use high minConfirmations so step 1's confirmation wait is long (~20s),
        // giving us a reliable window to cancel before step 2 starts.
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(20)

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(2))
                .build();

        // Create a multi-step flow — step 2 should never execute if cancellation works
        TxFlow flow = TxFlow.builder("cancellation-test")
                .withDescription("Test FlowHandle cancellation")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (2 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Account0 -> Account2 (2 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowExecutor executor = createYaciExecutor()
                .withConfirmationConfig(config)
                .withRollbackStrategy(RollbackStrategy.FAIL_IMMEDIATELY)
                .withListener(listener);

        // Execute async to get FlowHandle
        FlowHandle handle = executor.execute(flow);

        // Wait for first tx submission (not completion — that takes ~20s with high confirmations)
        System.out.println("Waiting for first transaction to be submitted...");
        int waitCount = 0;
        while (!txSubmitted.get() && waitCount < 30) {
            Thread.sleep(500);
            waitCount++;
        }
        assertTrue(txSubmitted.get(), "First transaction should have been submitted");

        // Cancel during step 1's confirmation wait
        System.out.println("Cancelling flow during confirmation wait...");
        handle.cancel();
        assertEquals(FlowStatus.CANCELLED, handle.getStatus(),
                "Handle status should be CANCELLED immediately after cancel()");

        // Wait for flow to finish — the executor thread is still in waitForConfirmation
        // for step 1. After that returns, it will check isCancelled() before step 2
        // and return a CANCELLED result.
        System.out.println("Waiting for flow to finish...");
        try {
            handle.await(Duration.ofSeconds(60));
            fail("Cancelled FlowHandle should complete with CancellationException");
        } catch (CancellationException expected) {
            // FlowHandle.cancel() cancels its result future by contract.
        }

        assertEquals(FlowStatus.CANCELLED, handle.getStatus());
        assertFalse(secondStepStarted.get(),
                "Step 2 should not have started after cancellation");

        System.out.println("\n=== Test 10 completed! ===\n");
    }

    /**
     * Test that a rolled-back producer invalidates its actual consumer closure.
     */
    @Test
    @Order(11)
    void testInvalidatedConsumerClosure_rebuildFromFailed() throws Exception {
        System.out.println("=== Test 11: Invalidated Consumer Closure ===");

        AtomicBoolean txSubmitted = new AtomicBoolean(false);
        RollbackListenerTracker listener = new RollbackListenerTracker() {
            @Override
            public void onTransactionSubmitted(FlowStep step, String transactionHash) {
                super.onTransactionSubmitted(step, transactionHash);
                txSubmitted.set(true);
            }
        };

        ConfirmationConfig rollbackConfig = ConfirmationConfig.builder()
                .minConfirmations(30)

                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(3))
                .maxRollbackRetries(3)
                .waitForBackendAfterRollback(true)
                .postRollbackWaitAttempts(30)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
                .build();

        // Step 2 consumes step 1's actual output, so rollback invalidates both attempts.
        TxFlow flow = TxFlow.builder("auto-escalation-test")
                .withDescription("Test invalidated consumer closure recovery")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Account0 -> Account1 (3 ADA)")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(3))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withDescription("Account1 -> Account2 (1 ADA)")
                        .dependsOn("step1", SelectionStrategy.ALL)
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.ada(1))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        // PIPELINED mode restarts its scheduling pass for the invalidated closure.
        FlowExecutor executor = createYaciExecutor()
                .withChainingMode(ChainingMode.PIPELINED)
                .withConfirmationConfig(rollbackConfig)
                .withRollbackStrategy(RollbackStrategy.REBUILD_FROM_FAILED)
                .withListener(listener);

        assertTrue(RollbackTestHelper.takeDbSnapshot(), "Should take DB snapshot");

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<FlowResult> future = executorService.submit(() -> executor.executeSync(flow));

        try {
            assertTrue(txSubmitted.get() || listener.awaitFirstSubmission(Duration.ofSeconds(30)),
                    "Transaction should have been submitted");
            assertTrue(listener.awaitFirstInclusion(Duration.ofSeconds(30)),
                    "Transaction should enter a block before rollback");

            System.out.println("Triggering rollback...");
            assertTrue(RollbackTestHelper.rollbackToSnapshot(), "Should rollback to snapshot");
            assertTrue(RollbackTestHelper.waitForNodeReady(backendService, 30),
                    "Node should be ready after rollback");

            FlowResult result = awaitFlowResult(future, Duration.ofSeconds(180));
            System.out.println("Flow result: " + result.getStatus());
            System.out.println("Flow restart attempts: " + listener.getRestartAttempts());
            System.out.println("Rollback notifications: " + listener.getRolledBackTxHashes().size());

            assertTrue(result.isSuccessful(),
                    "Invalidated dependent closure must complete after restart: "
                            + errorMessage(result));
            assertFalse(listener.getRolledBackTxHashes().isEmpty(),
                    "Producer rollback must be observed");
            assertTrue(listener.getRestartAttempts() > 0,
                    "A rolled-back producer with a dependent must restart the scheduling pass");
            assertEquals(2, result.getCompletedStepCount(),
                    "Both producer and dependent must complete after recovery");
        } finally {
            future.cancel(true);
            shutdownExecutor(executorService);
        }

        System.out.println("\n=== Test 11 completed successfully! ===\n");
    }

    /**
     * The convenience backend adapter does not claim authoritative transaction absence.
     * After a previously included transaction disappears it must report suspicion, keep
     * reconciliation bounded, and never authorize a rebuild from that ambiguous observation.
     */
    @Test
    @Order(12)
    void testDefaultBackendPath_ambiguousAbsenceIsSuspectedAndBounded() throws Exception {
        System.out.println("=== Test 12: Default Backend Ambiguous Absence ===");

        RollbackListenerTracker listener = new RollbackListenerTracker();
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(30)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(20))
                .build();
        TxFlow flow = TxFlow.builder("default-backend-ambiguous-rollback")
                .addStep(FlowStep.builder("payment")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(2))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        // Deliberately use the convenience adapter without authoritative-absence wrapping.
        FlowExecutor executor = FlowExecutor.create(backendService)
                .withConfirmationConfig(config)
                .withRollbackStrategy(RollbackStrategy.FAIL_IMMEDIATELY)
                .withListener(listener);

        assertTrue(RollbackTestHelper.takeDbSnapshot(), "Should take DB snapshot");
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<FlowResult> future = executorService.submit(() -> executor.executeSync(flow));
        try {
            assertTrue(listener.awaitFirstInclusion(Duration.ofSeconds(30)),
                    "Transaction should enter a block before rollback");

            assertTrue(RollbackTestHelper.rollbackToSnapshot(), "Should rollback to snapshot");
            assertTrue(RollbackTestHelper.waitForNodeReady(backendService, 30),
                    "Node should be ready after rollback");

            FlowResult result = awaitFlowResult(future, Duration.ofSeconds(35));
            assertNotNull(result.getError(), "Ambiguous absence must expose its terminal cause");
            assertAll(
                    () -> assertTrue(result.isFailed(),
                            "Ambiguous absence must terminate without rebuilding"),
                    () -> assertEquals("ReconciliationUncertainException",
                            result.getError().getClass().getSimpleName(),
                            "Ambiguous absence must retain the typed reconciliation outcome"),
                    () -> assertFalse(listener.getSuspectedRollbackTxHashes().isEmpty(),
                            "Default backend path must emit suspected rollback"),
                    () -> assertTrue(listener.getRolledBackTxHashes().isEmpty(),
                            "Ambiguous absence must not be reported as authoritative rollback"),
                    () -> assertEquals(0, listener.getRestartAttempts(),
                            "Ambiguous absence must not restart the flow"));
        } finally {
            future.cancel(true);
            shutdownExecutor(executorService);
        }

        System.out.println("\n=== Test 12 completed successfully! ===\n");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (asyncExecutor != null) {
            asyncExecutor.shutdownNow();
            assertTrue(asyncExecutor.awaitTermination(10, TimeUnit.SECONDS),
                    "TxFlow async executor should terminate cleanly");
        }
        System.out.println("Test cleanup completed\n");
    }

    /**
     * Test listener that tracks all rollback-related callbacks.
     * Used to verify that the correct callbacks are invoked during rollback handling.
     */
    static class RollbackListenerTracker implements FlowListener {
        private final List<String> rolledBackTxHashes = new CopyOnWriteArrayList<>();
        private final List<String> suspectedRollbackTxHashes = new CopyOnWriteArrayList<>();
        private final List<String> rebuiltSteps = new CopyOnWriteArrayList<>();
        private final List<String> restartedFlows = new CopyOnWriteArrayList<>();
        private final AtomicInteger rebuildAttempts = new AtomicInteger(0);
        private final AtomicInteger restartAttempts = new AtomicInteger(0);
        private final AtomicBoolean flowCompleted = new AtomicBoolean(false);
        private final AtomicBoolean flowFailed = new AtomicBoolean(false);
        private final CountDownLatch firstSubmission = new CountDownLatch(1);
        private final CountDownLatch firstInclusion = new CountDownLatch(1);

        @Override
        public void onTransactionRolledBack(FlowStep step, String transactionHash, long previousBlockHeight) {
            System.out.println("  [CALLBACK] onTransactionRolledBack: " + transactionHash +
                    " (was in block " + previousBlockHeight + ")");
            rolledBackTxHashes.add(transactionHash);
        }

        @Override
        public void onTransactionRollbackSuspected(FlowStep step, String transactionHash,
                                                   long previousBlockHeight) {
            System.out.println("  [CALLBACK] onTransactionRollbackSuspected: " + transactionHash
                    + " (last seen in block " + previousBlockHeight + ")");
            suspectedRollbackTxHashes.add(transactionHash);
        }

        @Override
        public void onStepRebuilding(FlowStep step, int attemptNumber, int maxAttempts, String reason) {
            System.out.println("  [CALLBACK] onStepRebuilding: " + step.getId() +
                    " (attempt " + attemptNumber + "/" + maxAttempts + ") - " + reason);
            rebuildAttempts.incrementAndGet();
            rebuiltSteps.add(step.getId());
        }

        @Override
        public void onFlowRestarting(TxFlow flow, int attemptNumber, int maxAttempts, String reason) {
            System.out.println("  [CALLBACK] onFlowRestarting: " + flow.getId() +
                    " (attempt " + attemptNumber + "/" + maxAttempts + ") - " + reason);
            restartAttempts.incrementAndGet();
            restartedFlows.add(flow.getId());
        }

        @Override
        public void onFlowCompleted(TxFlow flow, FlowResult result) {
            System.out.println("  [CALLBACK] onFlowCompleted: " + flow.getId());
            flowCompleted.set(true);
        }

        @Override
        public void onFlowFailed(TxFlow flow, FlowResult result) {
            System.out.println("  [CALLBACK] onFlowFailed: " + flow.getId() +
                    (result.getError() != null ? " - " + result.getError().getMessage() : ""));
            flowFailed.set(true);
        }

        @Override
        public void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
            System.out.println("  [CALLBACK] onStepStarted: " + step.getId() +
                    " (" + (stepIndex + 1) + "/" + totalSteps + ")");
        }

        @Override
        public void onStepCompleted(FlowStep step, FlowStepResult result) {
            System.out.println("  [CALLBACK] onStepCompleted: " + step.getId() +
                    " - Tx: " + result.getTransactionHash());
        }

        @Override
        public void onStepFailed(FlowStep step, FlowStepResult result) {
            System.out.println("  [CALLBACK] onStepFailed: " + step.getId() +
                    (result.getError() != null ? " - " + result.getError().getMessage() : ""));
        }

        @Override
        public void onTransactionSubmitted(FlowStep step, String transactionHash) {
            System.out.println("  [CALLBACK] onTransactionSubmitted: " + transactionHash);
            firstSubmission.countDown();
        }

        @Override
        public void onTransactionInBlock(FlowStep step, String transactionHash, long blockHeight) {
            System.out.println("  [CALLBACK] onTransactionInBlock: " + transactionHash
                    + " at " + blockHeight);
            firstInclusion.countDown();
        }

        @Override
        public void onTransactionConfirmed(FlowStep step, String transactionHash) {
            System.out.println("  [CALLBACK] onTransactionConfirmed: " + transactionHash);
        }

        // Getters for test assertions
        public List<String> getRolledBackTxHashes() {
            return new ArrayList<>(rolledBackTxHashes);
        }

        public List<String> getSuspectedRollbackTxHashes() {
            return new ArrayList<>(suspectedRollbackTxHashes);
        }

        public List<String> getRebuiltSteps() {
            return new ArrayList<>(rebuiltSteps);
        }

        public List<String> getRestartedFlows() {
            return new ArrayList<>(restartedFlows);
        }

        public int getRebuildAttempts() {
            return rebuildAttempts.get();
        }

        public int getRestartAttempts() {
            return restartAttempts.get();
        }

        public boolean isFlowCompleted() {
            return flowCompleted.get();
        }

        public boolean isFlowFailed() {
            return flowFailed.get();
        }

        boolean awaitFirstSubmission(Duration timeout) throws InterruptedException {
            return firstSubmission.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        boolean awaitFirstInclusion(Duration timeout) throws InterruptedException {
            return firstInclusion.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
