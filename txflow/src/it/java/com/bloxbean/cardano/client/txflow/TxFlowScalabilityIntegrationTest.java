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
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionHandle;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Scalability integration tests for {@link FlowEngine} using Yaci DevKit.
 *
 * <p>Demonstrates:
 * <ul>
 *     <li>Top-up of 200+ accounts using regular transactions</li>
 *     <li>Parallel execution of 100+ transaction flows via {@link FlowEngine}</li>
 *     <li>BATCH mode for efficient same-block transaction chaining</li>
 *     <li>Tracking executions via {@link FlowExecutionHandle} and {@link InMemoryFlowExecutionStore}</li>
 * </ul>
 *
 * <p>Prerequisites:
 * - Yaci DevKit running at http://localhost:8080/api/v1/
 * - Run with: ./gradlew :txflow:integrationTest --tests TxFlowScalabilityIntegrationTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TxFlowScalabilityIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TxFlowScalabilityIntegrationTest.class);

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private static final int TOTAL_ACCOUNTS = 200;
    private static final int NUM_PARALLEL_FLOWS = 100;
    private static final int MAX_CONCURRENT_FLOWS = 16;

    private static final long TOPUP_AMOUNT_LOVELACE = 5_000_000L;
    private static final double TRANSFER_AMOUNT_ADA = 1.0;

    private BFBackendService backendService;
    private QuickTxBuilder quickTxBuilder;
    private ExecutorService asyncExecutor;
    private ExecutorService maintenanceExecutor;
    private DefaultSignerRegistry signerRegistry;

    private Account funderAccount;
    private List<Account> testAccounts;

    @BeforeEach
    void setUp() {
        log.info("=== TxFlow Scalability Integration Test Setup ===");

        asyncExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_FLOWS);
        maintenanceExecutor = Executors.newFixedThreadPool(4);
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");
        quickTxBuilder = new QuickTxBuilder(backendService);

        funderAccount = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 0);
        log.info("Funder account: {}", funderAccount.baseAddress());

        try {
            List<Utxo> utxos = backendService.getUtxoService()
                    .getUtxos(funderAccount.baseAddress(), 100, 1)
                    .getValue();
            log.info("Funder UTXOs: {}", utxos.size());
            if (utxos.isEmpty()) {
                fail("Funder account has no UTXOs - Yaci DevKit may not be running");
            }
        } catch (Exception e) {
            fail("Could not connect to Yaci DevKit: " + e.getMessage());
        }

        testAccounts = IntStream.range(1000, 1000 + TOTAL_ACCOUNTS)
                .mapToObj(i -> new Account(Networks.testnet(), DEFAULT_MNEMONIC, i))
                .collect(Collectors.toList());

        signerRegistry = new DefaultSignerRegistry();
        for (int i = 0; i < testAccounts.size(); i++) {
            signerRegistry.addAccount(accountRef(i), testAccounts.get(i));
        }

        log.info("Generated {} test accounts (indices 1000-{})", TOTAL_ACCOUNTS, 999 + TOTAL_ACCOUNTS);
    }

    private static String accountRef(int index) {
        return "account://test-" + index;
    }

    @Test
    @Order(1)
    void testTopUpAccounts() throws Exception {
        log.info("=== Test 1: Top Up {} Accounts ===", TOTAL_ACCOUNTS);

        long startTime = System.currentTimeMillis();
        int batchSize = 200;
        int numBatches = (TOTAL_ACCOUNTS + batchSize - 1) / batchSize;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int batch = 0; batch < numBatches; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min(startIdx + batchSize, TOTAL_ACCOUNTS);

            Tx tx = new Tx();
            for (int i = startIdx; i < endIdx; i++) {
                tx.payToAddress(testAccounts.get(i).baseAddress(),
                        Amount.lovelace(BigInteger.valueOf(TOPUP_AMOUNT_LOVELACE)));
            }
            tx.from(funderAccount.baseAddress());

            try {
                var result = quickTxBuilder.compose(tx)
                        .withSigner(SignerProviders.signerFrom(funderAccount))
                        .completeAndWait(msg -> { });

                if (result.isSuccessful()) {
                    successCount.addAndGet(endIdx - startIdx);
                    String txHash = result.getValue();
                    log.info("Batch {}/{} completed: accounts {}-{} funded. Tx: {}",
                            batch + 1, numBatches, startIdx, endIdx - 1, txHash);
                    if (!waitForTxOutput(txHash, 0, Duration.ofSeconds(60))) {
                        log.warn("Timeout waiting for tx {} to be indexed", txHash);
                    }
                } else {
                    failCount.addAndGet(endIdx - startIdx);
                    log.error("Batch {} FAILED: {}", batch + 1, result.getResponse());
                }
            } catch (Exception e) {
                failCount.addAndGet(endIdx - startIdx);
                log.error("Batch {} EXCEPTION: {}", batch + 1, e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Top-Up Summary: {} funded, {} failed in {} ms",
                successCount.get(), failCount.get(), duration);

        assertTrue(successCount.get() >= TOTAL_ACCOUNTS * 0.9,
                "At least 90% of accounts should be funded. Got: " + successCount.get());
    }

    @Test
    @Order(2)
    void testParallelFlowsWithEngine() throws Exception {
        log.info("=== Test 2: Execute {} Parallel Flows with FlowEngine ===", NUM_PARALLEL_FLOWS);

        ensureAccountsFunded();

        long startTime = System.currentTimeMillis();
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore();

        FlowEngine engine = FlowEngine.builder(backendService)
                .executor(asyncExecutor)
                .maintenanceExecutor(maintenanceExecutor)
                .signerRegistry(signerRegistry)
                .store(store)
                .build();

        FlowExecutionSettings batchSettings = FlowExecutionSettings.builder()
                .chainingMode(ChainingMode.BATCH)
                .confirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build())
                .build();

        List<FlowExecutionHandle> handles = new ArrayList<>();
        ExecutorService launchExecutor = Executors.newFixedThreadPool(10);
        List<Future<FlowExecutionHandle>> futures = new ArrayList<>();

        for (int i = 0; i < NUM_PARALLEL_FLOWS; i++) {
            final int flowIndex = i;
            String senderRef = accountRef(flowIndex);
            String intermediateRef = accountRef(flowIndex + 100);
            Account sender = testAccounts.get(flowIndex);
            Account intermediate = testAccounts.get(flowIndex + 100);

            futures.add(launchExecutor.submit(() -> {
                TxFlow flow = TxFlow.builder("flow-" + flowIndex)
                        .withDescription("Circular transfer flow " + flowIndex)
                        .withExecutionSettings(batchSettings)
                        .addStep(FlowStep.builder("transfer-out")
                                .withDescription("Transfer to intermediate")
                                .withTxPlan(TxPlan.from(new Tx()
                                                .payToAddress(intermediate.baseAddress(),
                                                        Amount.ada(TRANSFER_AMOUNT_ADA))
                                                .fromRef(senderRef))
                                        .withSigner(senderRef))
                                .build())
                        .addStep(FlowStep.builder("transfer-back")
                                .withDescription("Transfer back to sender")
                                .needs("transfer-out")
                                .fundsFrom("transfer-out")
                                .withTxPlan(TxPlan.from(new Tx()
                                                .payToAddress(sender.baseAddress(),
                                                        Amount.ada(TRANSFER_AMOUNT_ADA * 0.8))
                                                .fromRef(intermediateRef))
                                        .withSigner(intermediateRef))
                                .build())
                        .build();

                return engine.start(FlowExecutionRequest.builder(flow)
                        .executionId("parallel-flow-" + flowIndex)
                        .build());
            }));
        }

        for (Future<FlowExecutionHandle> future : futures) {
            try {
                handles.add(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("Failed to launch flow: {}", e.getMessage());
            }
        }
        launchExecutor.shutdown();

        log.info("Launched {} flows", handles.size());

        int successfulFlows = 0;
        int failedFlows = 0;
        int totalTransactions = 0;

        for (FlowExecutionHandle handle : handles) {
            try {
                FlowExecutionResult result = handle.completion()
                        .toCompletableFuture()
                        .get(5, TimeUnit.MINUTES);
                if (result.isSuccessful()) {
                    successfulFlows++;
                    totalTransactions += result.steps().size();
                } else {
                    failedFlows++;
                }
            } catch (Exception e) {
                failedFlows++;
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        log.info("Scalability Summary: {} succeeded, {} failed, {} txs in {} ms ({} tx/sec)",
                successfulFlows, failedFlows, totalTransactions, totalDuration,
                String.format("%.2f", (totalTransactions * 1000.0 / totalDuration)));

        assertTrue(successfulFlows >= NUM_PARALLEL_FLOWS * 0.8,
                "At least 80% of flows should succeed. Got: " + successfulFlows + "/" + NUM_PARALLEL_FLOWS);
    }

    @Test
    @Order(3)
    void testExecutionEventAndStoreTracking() throws Exception {
        log.info("=== Test 3: Execution Event and Store Tracking ===");

        ensureAccountsFunded();

        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore();
        FlowEngine engine = FlowEngine.builder(backendService)
                .executor(asyncExecutor)
                .maintenanceExecutor(maintenanceExecutor)
                .signerRegistry(signerRegistry)
                .store(store)
                .build();

        FlowExecutionSettings batchSettings = FlowExecutionSettings.builder()
                .chainingMode(ChainingMode.BATCH)
                .build();

        int numFlows = 5;
        List<FlowExecutionHandle> handles = new ArrayList<>();

        for (int i = 0; i < numFlows; i++) {
            String senderRef = accountRef(i);
            Account receiver = testAccounts.get(i + 100);

            TxFlow flow = TxFlow.builder("status-test-" + i)
                    .withExecutionSettings(batchSettings)
                    .addStep(FlowStep.builder("transfer")
                            .withTxPlan(TxPlan.from(new Tx()
                                            .payToAddress(receiver.baseAddress(), Amount.ada(0.5))
                                            .fromRef(senderRef))
                                    .withSigner(senderRef))
                            .build())
                    .build();

            handles.add(engine.start(FlowExecutionRequest.builder(flow)
                    .executionId("status-exec-" + i)
                    .build()));
        }

        for (int i = 0; i < numFlows; i++) {
            FlowExecutionHandle handle = handles.get(i);
            FlowExecutionResult result = handle.completion()
                    .toCompletableFuture()
                    .get(60, TimeUnit.SECONDS);
            assertTrue(handle.isDone(), "Flow should be done");
            assertEquals(FlowExecutionState.COMPLETED, result.state());
            List<FlowEvent> events = handle.getEvents();
            assertFalse(events.isEmpty(), "Execution should emit ordered events");
            assertTrue(store.get("status-exec-" + i).isPresent(),
                    "Execution should be persisted in store");
        }
    }

    @Test
    @Order(4)
    void testHighVolumeFlowCreation() throws Exception {
        log.info("=== Test 4: High Volume Flow Creation (Stress Test) ===");

        ensureAccountsFunded();

        int numFlows = 50;
        int stepsPerFlow = 3;

        FlowEngine engine = FlowEngine.builder(backendService)
                .executor(asyncExecutor)
                .signerRegistry(signerRegistry)
                .build();

        FlowExecutionSettings batchSettings = FlowExecutionSettings.builder()
                .chainingMode(ChainingMode.BATCH)
                .confirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(180)).build())
                .build();

        long startTime = System.currentTimeMillis();
        List<FlowExecutionHandle> handles = new ArrayList<>();

        for (int i = 0; i < numFlows; i++) {
            String refA = accountRef(i);
            String refB = accountRef(i + numFlows);
            String refC = accountRef(i + numFlows * 2);
            Account a = testAccounts.get(i);
            Account b = testAccounts.get(i + numFlows);
            Account c = testAccounts.get(i + numFlows * 2);

            TxFlow flow = TxFlow.builder("stress-" + i)
                    .withExecutionSettings(batchSettings)
                    .addStep(FlowStep.builder("a-to-b")
                            .withTxPlan(TxPlan.from(new Tx()
                                            .payToAddress(b.baseAddress(), Amount.ada(0.5))
                                            .attachMetadata(MessageMetadata.create().add("TxFlow: " + i + "-step1"))
                                            .fromRef(refA))
                                    .withSigner(refA))
                            .build())
                    .addStep(FlowStep.builder("b-to-c")
                            .needs("a-to-b")
                            .fundsFrom("a-to-b")
                            .withTxPlan(TxPlan.from(new Tx()
                                            .payToAddress(c.baseAddress(), Amount.ada(0.4))
                                            .attachMetadata(MessageMetadata.create().add("TxFlow: " + i + "-step2"))
                                            .fromRef(refB))
                                    .withSigner(refB))
                            .build())
                    .addStep(FlowStep.builder("c-to-a")
                            .needs("b-to-c")
                            .fundsFrom("b-to-c")
                            .withTxPlan(TxPlan.from(new Tx()
                                            .payToAddress(a.baseAddress(), Amount.ada(0.3))
                                            .attachMetadata(MessageMetadata.create().add("TxFlow: " + i + "-step3"))
                                            .fromRef(refC))
                                    .withSigner(refC))
                            .build())
                    .build();

            handles.add(engine.start(FlowExecutionRequest.builder(flow)
                    .executionId("stress-exec-" + i)
                    .build()));
        }

        int successful = 0;
        int failed = 0;
        int totalTxs = 0;

        for (FlowExecutionHandle handle : handles) {
            try {
                FlowExecutionResult result = handle.completion()
                        .toCompletableFuture()
                        .get(180, TimeUnit.SECONDS);
                if (result.isSuccessful()) {
                    successful++;
                    totalTxs += result.steps().size();
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Stress Test Summary: {}x{} flows -> {} succeeded, {} failed, {} txs in {} ms ({} tx/sec)",
                numFlows, stepsPerFlow, successful, failed, totalTxs, duration,
                String.format("%.2f", (totalTxs * 1000.0 / duration)));

        assertTrue(successful >= numFlows * 0.7,
                "At least 70% of flows should succeed. Got: " + successful);
    }

    private boolean waitForTxOutput(String txHash, int outputIndex, Duration timeout) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();
        long pollIntervalMs = 2000;

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

    private void ensureAccountsFunded() throws Exception {
        try {
            List<Utxo> utxos = backendService.getUtxoService()
                    .getUtxos(testAccounts.get(0).baseAddress(), 10, 1)
                    .getValue();

            if (utxos == null || utxos.isEmpty()) {
                log.info("Test accounts not funded. Running top-up...");
                testTopUpAccounts();
                Thread.sleep(5000);
            } else {
                log.info("Test accounts already funded ({} UTXOs found)", utxos.size());
            }
        } catch (Exception e) {
            log.info("Could not check account balance, attempting top-up: {}", e.getMessage());
            testTopUpAccounts();
            Thread.sleep(5000);
        }
    }

    @AfterEach
    void tearDown() {
        if (asyncExecutor != null) {
            asyncExecutor.shutdownNow();
        }
        if (maintenanceExecutor != null) {
            maintenanceExecutor.shutdownNow();
        }
    }
}
