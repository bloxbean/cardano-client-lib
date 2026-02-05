package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutor;
import com.bloxbean.cardano.client.txflow.exec.FlowHandle;
import com.bloxbean.cardano.client.txflow.exec.registry.FlowLifecycleListener;
import com.bloxbean.cardano.client.txflow.exec.registry.InMemoryFlowRegistry;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import org.junit.jupiter.api.*;

import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scalability integration tests for TxFlow using Yaci DevKit.
 * <p>
 * This test demonstrates:
 * <ul>
 *     <li>Top-up of 200+ accounts using regular transactions</li>
 *     <li>Parallel execution of 100+ transaction flows</li>
 *     <li>BATCH mode for efficient same-block transaction chaining</li>
 *     <li>FlowRegistry for tracking status of hundreds of parallel flows</li>
 * </ul>
 * <p>
 * Prerequisites:
 * - Yaci DevKit running at http://localhost:8080/api/v1/
 * - Run with: ./gradlew :txflow:integrationTest -Dyaci.integration.test=true --tests TxFlowScalabilityIntegrationTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TxFlowScalabilityIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    // Number of accounts to create and test with
    private static final int TOTAL_ACCOUNTS = 200;
    private static final int NUM_PARALLEL_FLOWS = 100;

    // Amount to top-up each account with (5 ADA)
    private static final long TOPUP_AMOUNT_LOVELACE = 5_000_000L;
    // Transfer amount in flows (1 ADA)
    private static final double TRANSFER_AMOUNT_ADA = 1.0;

    private BFBackendService backendService;
    private QuickTxBuilder quickTxBuilder;

    // Funder account (index 0 from Yaci DevKit - has lots of ADA)
    private Account funderAccount;

    // Generated accounts for testing
    private List<Account> testAccounts;

    // Flow registry for tracking
    private InMemoryFlowRegistry flowRegistry;

    @BeforeEach
    void setUp() {
        System.out.println("\n=== TxFlow Scalability Integration Test Setup ===");

        // Initialize backend service
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");
//        backendService = new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, "");
        quickTxBuilder = new QuickTxBuilder(backendService);

        // Create funder account from Yaci DevKit's pre-funded account
        funderAccount = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 0);
        System.out.println("Funder account: " + funderAccount.baseAddress());

        // Verify funder has funds
        try {
            List<Utxo> utxos = backendService.getUtxoService()
                    .getUtxos(funderAccount.baseAddress(), 100, 1)
                    .getValue();
            System.out.println("Funder UTXOs: " + utxos.size());
            if (utxos.isEmpty()) {
                fail("Funder account has no UTXOs - Yaci DevKit may not be running");
            }
        } catch (Exception e) {
            fail("Could not connect to Yaci DevKit: " + e.getMessage());
        }

        // Generate test accounts at high indices (to avoid overlap with pre-funded)
        testAccounts = IntStream.range(1000, 1000 + TOTAL_ACCOUNTS)
                .mapToObj(i -> new Account(Networks.testnet(), DEFAULT_MNEMONIC, i))
                .collect(Collectors.toList());
        System.out.println("Generated " + TOTAL_ACCOUNTS + " test accounts (indices 1000-" + (999 + TOTAL_ACCOUNTS) + ")");

        // Initialize flow registry
        flowRegistry = new InMemoryFlowRegistry();

        System.out.println("Setup complete!\n");
    }

    @Test
    @Order(1)
    void testTopUpAccounts() throws Exception {
        System.out.println("=== Test 1: Top Up " + TOTAL_ACCOUNTS + " Accounts ===\n");

        long startTime = System.currentTimeMillis();

        // Top-up accounts in batches to avoid UTxO fragmentation issues
        int batchSize = 200; // Send to 20 accounts per transaction
        int numBatches = (TOTAL_ACCOUNTS + batchSize - 1) / batchSize;

        System.out.println("Top-up strategy: " + numBatches + " batches of " + batchSize + " accounts each");
        System.out.println("Amount per account: " + (TOPUP_AMOUNT_LOVELACE / 1_000_000.0) + " ADA\n");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int batch = 0; batch < numBatches; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min(startIdx + batchSize, TOTAL_ACCOUNTS);

            // Build transaction with multiple outputs
            Tx tx = new Tx();
            for (int i = startIdx; i < endIdx; i++) {
                tx.payToAddress(testAccounts.get(i).baseAddress(), Amount.lovelace(BigInteger.valueOf(TOPUP_AMOUNT_LOVELACE)));
            }
            tx.from(funderAccount.baseAddress());

            try {
                var result = quickTxBuilder.compose(tx)
                        .withSigner(SignerProviders.signerFrom(funderAccount))
                        .completeAndWait(msg -> {});

                if (result.isSuccessful()) {
                    successCount.addAndGet(endIdx - startIdx);
                    String txHash = result.getValue();
                    System.out.println("Batch " + (batch + 1) + "/" + numBatches + " completed: accounts " +
                            startIdx + "-" + (endIdx - 1) + " funded. Tx: " + txHash);

                    // Wait for transaction to be indexed before proceeding to next batch
                    System.out.println("  Waiting for tx to be indexed...");
                    if (!waitForTxOutput(txHash, 0, Duration.ofSeconds(60))) {
                        System.err.println("  Warning: Timeout waiting for tx to be indexed");
                    } else {
                        System.out.println("  Tx confirmed and indexed");
                    }
                } else {
                    failCount.addAndGet(endIdx - startIdx);
                    System.err.println("Batch " + (batch + 1) + " FAILED: " + result.getResponse());
                }
            } catch (Exception e) {
                failCount.addAndGet(endIdx - startIdx);
                System.err.println("Batch " + (batch + 1) + " EXCEPTION: " + e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        System.out.println("\n=== Top-Up Summary ===");
        System.out.println("Total accounts: " + TOTAL_ACCOUNTS);
        System.out.println("Successfully funded: " + successCount.get());
        System.out.println("Failed: " + failCount.get());
        System.out.println("Duration: " + duration + " ms");

        // Verify at least 90% success rate
        assertTrue(successCount.get() >= TOTAL_ACCOUNTS * 0.9,
                "At least 90% of accounts should be funded. Got: " + successCount.get());

        System.out.println("\n=== Test 1 completed! ===\n");
    }

    @Test
    @Order(2)
    void testParallelFlowsWithRegistry() throws Exception {
        System.out.println("=== Test 2: Execute " + NUM_PARALLEL_FLOWS + " Parallel Flows with Registry ===\n");

        // First ensure accounts have funds (run top-up if needed)
        ensureAccountsFunded();

        long startTime = System.currentTimeMillis();

        // Set up lifecycle listener to track progress
        AtomicInteger registeredCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        ConcurrentHashMap<String, Long> completionTimes = new ConcurrentHashMap<>();

        flowRegistry.addLifecycleListener(new FlowLifecycleListener() {
            @Override
            public void onFlowRegistered(String flowId, FlowHandle handle) {
                registeredCount.incrementAndGet();
            }

            @Override
            public void onFlowCompleted(String flowId, FlowHandle handle, FlowResult result) {
                if (result.isSuccessful()) {
                    completedCount.incrementAndGet();
                } else {
                    failedCount.incrementAndGet();
                }
                completionTimes.put(flowId, System.currentTimeMillis());
            }

            @Override
            public void onFlowStatusChanged(String flowId, FlowHandle handle,
                                           FlowStatus oldStatus, FlowStatus newStatus) {
                // Could log status transitions if needed
            }
        });

        // Create flow executor with registry
        FlowExecutor executor = FlowExecutor.create(backendService)
                .withRegistry(flowRegistry)
                .withChainingMode(ChainingMode.BATCH)
                .withConfirmationTimeout(Duration.ofSeconds(120));

        System.out.println("Creating " + NUM_PARALLEL_FLOWS + " circular transfer flows...");
        System.out.println("Each flow: Account[i] -> Account[i+100] -> Account[i] (circular)");
        System.out.println("Mode: BATCH (build all, submit all, wait all)\n");

        // Launch flows in parallel
        List<FlowHandle> handles = new ArrayList<>();
        ExecutorService launchExecutor = Executors.newFixedThreadPool(10);

        List<Future<FlowHandle>> futures = new ArrayList<>();

        for (int i = 0; i < NUM_PARALLEL_FLOWS; i++) {
            final int flowIndex = i;
            Account sender = testAccounts.get(flowIndex);
            Account intermediate = testAccounts.get(flowIndex + 100);

            futures.add(launchExecutor.submit(() -> {
                // Create circular flow: sender -> intermediate -> sender
                TxFlow flow = TxFlow.builder("flow-" + flowIndex)
                        .withDescription("Circular transfer flow " + flowIndex)
                        .addStep(FlowStep.builder("transfer-out")
                                .withDescription("Transfer to intermediate")
                                .withTxContext(builder -> builder
                                        .compose(new Tx()
                                                .payToAddress(intermediate.baseAddress(), Amount.ada(TRANSFER_AMOUNT_ADA))
                                                .from(sender.baseAddress()))
                                        .withSigner(SignerProviders.signerFrom(sender)))
                                .build())
                        .addStep(FlowStep.builder("transfer-back")
                                .withDescription("Transfer back to sender")
                                .dependsOn("transfer-out")
                                .withTxContext(builder -> builder
                                        .compose(new Tx()
                                                .payToAddress(sender.baseAddress(), Amount.ada(TRANSFER_AMOUNT_ADA * 0.8))
                                                .from(intermediate.baseAddress()))
                                        .withSigner(SignerProviders.signerFrom(intermediate)))
                                .build())
                        .build();

                return executor.execute(flow);
            }));
        }

        // Collect all handles
        for (Future<FlowHandle> future : futures) {
            try {
                handles.add(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                System.err.println("Failed to launch flow: " + e.getMessage());
            }
        }
        launchExecutor.shutdown();

        System.out.println("Launched " + handles.size() + " flows");
        System.out.println("Registry size: " + flowRegistry.size());
        System.out.println("Active flows: " + flowRegistry.activeCount());

        // Wait for all flows to complete
        System.out.println("\nWaiting for all flows to complete (timeout: 5 minutes)...");

        long waitStart = System.currentTimeMillis();
        long maxWaitMs = 5 * 60 * 1000; // 5 minutes

        while (flowRegistry.activeCount() > 0 &&
               (System.currentTimeMillis() - waitStart) < maxWaitMs) {

            // Print progress every 10 seconds
            Thread.sleep(10_000);

            int active = flowRegistry.activeCount();
            int completed = completedCount.get();
            int failed = failedCount.get();

            System.out.println("Progress: " + completed + " completed, " +
                    failed + " failed, " + active + " active");
        }

        long totalDuration = System.currentTimeMillis() - startTime;

        // Collect results
        int successfulFlows = 0;
        int failedFlows = 0;
        int totalTransactions = 0;

        for (FlowHandle handle : handles) {
            try {
                if (handle.isDone()) {
                    Optional<FlowResult> resultOpt = handle.getResult();
                    if (resultOpt.isPresent() && resultOpt.get().isSuccessful()) {
                        successfulFlows++;
                        totalTransactions += resultOpt.get().getTransactionHashes().size();
                    } else {
                        failedFlows++;
                    }
                } else {
                    failedFlows++; // Timed out
                }
            } catch (Exception e) {
                failedFlows++;
            }
        }

        // Print summary
        System.out.println("\n========================================");
        System.out.println("       SCALABILITY TEST SUMMARY         ");
        System.out.println("========================================");
        System.out.println("Total flows launched: " + NUM_PARALLEL_FLOWS);
        System.out.println("Successful flows: " + successfulFlows);
        System.out.println("Failed flows: " + failedFlows);
        System.out.println("Total transactions: " + totalTransactions);
        System.out.println("Total duration: " + totalDuration + " ms");
        System.out.println("Avg time per flow: " + (totalDuration / NUM_PARALLEL_FLOWS) + " ms");
        System.out.println("Throughput: " + String.format("%.2f", (totalTransactions * 1000.0 / totalDuration)) + " tx/sec");
        System.out.println("========================================");

        // Registry statistics
        System.out.println("\nRegistry Statistics:");
        System.out.println("  Total registered: " + registeredCount.get());
        System.out.println("  Completed: " + flowRegistry.getFlowsByStatus(FlowStatus.COMPLETED).size());
        System.out.println("  Failed: " + flowRegistry.getFlowsByStatus(FlowStatus.FAILED).size());
        System.out.println("  In Progress: " + flowRegistry.getFlowsByStatus(FlowStatus.IN_PROGRESS).size());

        // Assertions
        assertTrue(successfulFlows >= NUM_PARALLEL_FLOWS * 0.8,
                "At least 80% of flows should succeed. Got: " + successfulFlows + "/" + NUM_PARALLEL_FLOWS);

        System.out.println("\n=== Test 2 completed! ===\n");

        // Cleanup
        flowRegistry.shutdown();
    }

    @Test
    @Order(3)
    void testRegistryStatusTracking() throws Exception {
        System.out.println("=== Test 3: Registry Status Tracking ===\n");

        // Ensure accounts have funds
        ensureAccountsFunded();

        InMemoryFlowRegistry registry = new InMemoryFlowRegistry();

        // Track all status changes
        List<String> statusChanges = Collections.synchronizedList(new ArrayList<>());

        registry.addLifecycleListener(new FlowLifecycleListener() {
            @Override
            public void onFlowRegistered(String flowId, FlowHandle handle) {
                statusChanges.add(flowId + ": REGISTERED");
            }

            @Override
            public void onFlowStatusChanged(String flowId, FlowHandle handle,
                                           FlowStatus oldStatus, FlowStatus newStatus) {
                statusChanges.add(flowId + ": " + oldStatus + " -> " + newStatus);
            }

            @Override
            public void onFlowCompleted(String flowId, FlowHandle handle, FlowResult result) {
                statusChanges.add(flowId + ": COMPLETED (" +
                        (result.isSuccessful() ? "success" : "failed") + ")");
            }
        });

        FlowExecutor executor = FlowExecutor.create(backendService)
                .withRegistry(registry)
                .withChainingMode(ChainingMode.BATCH);

        // Create a small set of flows to track status
        int numFlows = 5;
        List<FlowHandle> handles = new ArrayList<>();

        System.out.println("Launching " + numFlows + " flows for status tracking...\n");

        for (int i = 0; i < numFlows; i++) {
            Account sender = testAccounts.get(i);
            Account receiver = testAccounts.get(i + 100);

            TxFlow flow = TxFlow.builder("status-test-" + i)
                    .addStep(FlowStep.builder("transfer")
                            .withTxContext(builder -> builder
                                    .compose(new Tx()
                                            .payToAddress(receiver.baseAddress(), Amount.ada(0.5))
                                            .from(sender.baseAddress()))
                                    .withSigner(SignerProviders.signerFrom(sender)))
                            .build())
                    .build();

            handles.add(executor.execute(flow));
        }

        // Check registry state immediately
        System.out.println("Immediately after launch:");
        System.out.println("  Registry size: " + registry.size());
        System.out.println("  Active flows: " + registry.activeCount());

        // Wait for completion
        for (FlowHandle handle : handles) {
            handle.await(Duration.ofSeconds(60));
        }

        // Check final state
        System.out.println("\nAfter completion:");
        System.out.println("  Registry size: " + registry.size());
        System.out.println("  Completed: " + registry.getFlowsByStatus(FlowStatus.COMPLETED).size());
        System.out.println("  Active: " + registry.activeCount());

        // Print all status changes
        System.out.println("\nStatus change log:");
        for (String change : statusChanges) {
            System.out.println("  " + change);
        }

        // Verify all flows are in registry
        assertEquals(numFlows, registry.size(), "All flows should be in registry");

        // Verify we can lookup each flow
        for (int i = 0; i < numFlows; i++) {
            assertTrue(registry.contains("status-test-" + i),
                    "Flow status-test-" + i + " should be in registry");

            Optional<FlowHandle> handle = registry.getFlow("status-test-" + i);
            assertTrue(handle.isPresent(), "Should be able to get flow by ID");
            assertTrue(handle.get().isDone(), "Flow should be done");
        }

        System.out.println("\n=== Test 3 completed! ===\n");

        registry.shutdown();
    }

    @Test
    @Order(4)
    void testHighVolumeFlowCreation() throws Exception {
        System.out.println("=== Test 4: High Volume Flow Creation (Stress Test) ===\n");

        // Ensure accounts have funds
        ensureAccountsFunded();

        int numFlows = 50; // Fewer flows but more steps each
        int stepsPerFlow = 3;

        System.out.println("Creating " + numFlows + " flows with " + stepsPerFlow + " steps each");
        System.out.println("Total transactions: " + (numFlows * stepsPerFlow));

        InMemoryFlowRegistry registry = new InMemoryFlowRegistry();
        AtomicInteger completionCount = new AtomicInteger(0);

        registry.addLifecycleListener(new FlowLifecycleListener() {
            @Override
            public void onFlowCompleted(String flowId, FlowHandle handle, FlowResult result) {
                int count = completionCount.incrementAndGet();
                if (count % 10 == 0) {
                    System.out.println("Completed: " + count + "/" + numFlows);
                }
            }
        });

        FlowExecutor executor = FlowExecutor.create(backendService)
                .withRegistry(registry)
                .withChainingMode(ChainingMode.BATCH)
                .withConfirmationTimeout(Duration.ofSeconds(180));

        long startTime = System.currentTimeMillis();

        // Launch all flows
        List<FlowHandle> handles = new ArrayList<>();
        for (int i = 0; i < numFlows; i++) {
            Account a = testAccounts.get(i);
            Account b = testAccounts.get(i + numFlows);
            Account c = testAccounts.get(i + numFlows * 2);

            final int k = i;
            // Three-step circular: A -> B -> C -> A
            TxFlow flow = TxFlow.builder("stress-" + i)
                    .addStep(FlowStep.builder("a-to-b")
                            .withTxContext(builder -> builder
                                    .compose(new Tx()
                                            .payToAddress(b.baseAddress(), Amount.ada(0.5))
                                            .attachMetadata(MessageMetadata.create().add("TxFlow: " + k + "-step1"))
                                            .from(a.baseAddress()))
                                    .withSigner(SignerProviders.signerFrom(a)))
                            .build())
                    .addStep(FlowStep.builder("b-to-c")
                            .dependsOn("a-to-b")
                            .withTxContext(builder -> builder
                                    .compose(new Tx()
                                            .payToAddress(c.baseAddress(), Amount.ada(0.4))
                                            .attachMetadata(MessageMetadata.create().add("TxFlow: " + k + "-step2"))
                                            .from(b.baseAddress()))
                                    .withSigner(SignerProviders.signerFrom(b)))
                            .build())
                    .addStep(FlowStep.builder("c-to-a")
                            .dependsOn("b-to-c")
                            .withTxContext(builder -> builder
                                    .compose(new Tx()
                                            .payToAddress(a.baseAddress(), Amount.ada(0.3))
                                            .attachMetadata(MessageMetadata.create().add("TxFlow: " + k + "-step3"))
                                            .from(c.baseAddress()))
                                    .withSigner(SignerProviders.signerFrom(c)))
                            .build())
                    .build();

            handles.add(executor.execute(flow));
        }

        System.out.println("All flows launched. Waiting for completion...\n");

        // Wait for all with timeout
        int successful = 0;
        int failed = 0;
        int totalTxs = 0;

        for (FlowHandle handle : handles) {
            try {
                FlowResult result = handle.await(Duration.ofSeconds(180));
                if (result.isSuccessful()) {
                    successful++;
                    totalTxs += result.getTransactionHashes().size();
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        System.out.println("\n========================================");
        System.out.println("      STRESS TEST SUMMARY               ");
        System.out.println("========================================");
        System.out.println("Flows: " + numFlows + " x " + stepsPerFlow + " steps");
        System.out.println("Successful: " + successful);
        System.out.println("Failed: " + failed);
        System.out.println("Total transactions: " + totalTxs);
        System.out.println("Duration: " + duration + " ms");
        System.out.println("Throughput: " + String.format("%.2f", (totalTxs * 1000.0 / duration)) + " tx/sec");
        System.out.println("========================================");

        assertTrue(successful >= numFlows * 0.7,
                "At least 70% of flows should succeed. Got: " + successful);

        System.out.println("\n=== Test 4 completed! ===\n");

        registry.shutdown();
    }

    /**
     * Wait for a transaction output to be available via UTxO service.
     * This ensures the transaction is confirmed and indexed before proceeding.
     *
     * @param txHash the transaction hash
     * @param outputIndex the output index to check
     * @param timeout maximum time to wait
     * @return true if output is available, false if timeout
     */
    private boolean waitForTxOutput(String txHash, int outputIndex, Duration timeout) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();
        long pollIntervalMs = 2000; // Poll every 2 seconds

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                var result = backendService.getUtxoService().getTxOutput(txHash, outputIndex);
                if (result.isSuccessful() && result.getValue() != null) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore and retry
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Helper method to ensure test accounts have funds.
     * Checks first few accounts and tops up if needed.
     */
    private void ensureAccountsFunded() throws Exception {
        // Check if first account has funds
        try {
            List<Utxo> utxos = backendService.getUtxoService()
                    .getUtxos(testAccounts.get(0).baseAddress(), 10, 1)
                    .getValue();

            if (utxos == null || utxos.isEmpty()) {
                System.out.println("Test accounts not funded. Running top-up...");
                testTopUpAccounts();
                // Wait for UTXOs to be indexed
                Thread.sleep(5000);
            } else {
                System.out.println("Test accounts already funded (" + utxos.size() + " UTXOs found)");
            }
        } catch (Exception e) {
            System.out.println("Could not check account balance, attempting top-up: " + e.getMessage());
            testTopUpAccounts();
            Thread.sleep(5000);
        }
    }

    @AfterEach
    void tearDown() {
        if (flowRegistry != null) {
            flowRegistry.shutdown();
        }
        System.out.println("Test cleanup completed\n");
    }
}
