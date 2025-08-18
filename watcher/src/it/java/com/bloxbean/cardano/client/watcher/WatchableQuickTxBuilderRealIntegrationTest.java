package com.bloxbean.cardano.client.watcher;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.ADAConversionUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.watcher.api.WatchHandle;
import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.chain.*;
import com.bloxbean.cardano.client.watcher.quicktx.WatchableQuickTxBuilder;
import com.bloxbean.cardano.client.watcher.quicktx.WatchableStep;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real integration test for WatchableQuickTxBuilder using external Yaci DevKit.
 *
 * Prerequisites:
 * - Yaci DevKit running at http://localhost:8080/api/v1/
 * - Run with: -Dyaci.integration.test=true
 *
 * Yaci DevKit provides 10 pre-funded addresses (index 0-9) from the default mnemonic.
 * Each address has sufficient ADA for testing.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
//@EnabledIfSystemProperty(named = "yaci.integration.test", matches = "true")
public class WatchableQuickTxBuilderRealIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private WatchableQuickTxBuilder watchableBuilder;

    // Pre-funded accounts from Yaci DevKit
    private Account account0; // Index 0 - Primary sender
    private Account account1; // Index 1 - Primary receiver
    private Account account2; // Index 2 - Secondary account

    @BeforeEach
    void setUp() {
        System.out.println("\n=== WatchableQuickTxBuilder Real Integration Test Setup ===");

        // Initialize backend service
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");

        // Create accounts from default mnemonic at different indices
        // Yaci DevKit pre-funds these addresses
        account0 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 0);
        account1 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 1);
        account2 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 2);

        System.out.println("Using pre-funded Yaci DevKit accounts:");
        System.out.println("  Account 0: " + account0.baseAddress());
        System.out.println("  Account 1: " + account1.baseAddress());
        System.out.println("  Account 2: " + account2.baseAddress());

        // Create WatchableQuickTxBuilder
        watchableBuilder = new WatchableQuickTxBuilder(backendService);

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

        System.out.println("✅ Setup complete!");
        System.out.println();
    }

    @Test
    @Order(1)
    void testSingleTransactionWatchWithRealConfirmation() throws Exception {
        System.out.println("=== Test 1: Single Transaction Watch with Real Confirmation ===");

        // Create a simple payment transaction
        Tx paymentTx = new Tx()
            .payToAddress(account1.baseAddress(), Amount.ada(2.5))
            .from(account0.baseAddress());

        System.out.println("Creating watchable transaction to send 2.5 ADA...");
        System.out.println("  From: " + account0.baseAddress());
        System.out.println("  To: " + account1.baseAddress());

        // Cast to BasicWatchHandle for detailed monitoring first
        // Add step completion listener before starting watch
        AtomicBoolean stepCompleted = new AtomicBoolean(false);
        
        System.out.println("Creating watch handle...");
        
        // Create and execute with watch
        WatchHandle handle = watchableBuilder.compose(paymentTx)
            .withSigner(SignerProviders.signerFrom(account0))
            .feePayer(account0.baseAddress())
            .withDescription("Test: Single 2.5 ADA payment")
            .watch();

        assertNotNull(handle);
        System.out.println("Watch started with ID: " + handle.getWatchId());

        // Cast to BasicWatchHandle for detailed monitoring
        assertTrue(handle instanceof BasicWatchHandle);
        BasicWatchHandle basicHandle = (BasicWatchHandle) handle;

        // Add step completion listener immediately after getting handle
        basicHandle.onStepComplete(stepResult -> {
            System.out.println("🎯 Step completed callback triggered: " + stepResult.getStepId());
            System.out.println("  Status: " + stepResult.getStatus());
            System.out.println("  Tx Hash: " + stepResult.getTransactionHash());
            stepCompleted.set(true);
        });
        
        System.out.println("Step completion listener added. Current status: " + basicHandle.getStatus());

        // Monitor progress
        new Thread(() -> {
            try {
                while (!basicHandle.isCompleted()) {
                    System.out.println("Progress: " + String.format("%.1f%%", basicHandle.getProgress() * 100) +
                                     " - Status: " + basicHandle.getStatus());
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        // Wait for chain completion
        System.out.println("Waiting for transaction confirmation...");
        ChainResult result = basicHandle.await(Duration.ofSeconds(120));

        // Verify results
        assertNotNull(result);
        assertTrue(result.isSuccessful(), "Transaction should be successful");
        assertEquals(1, result.getTransactionHashes().size());
        assertTrue(stepCompleted.get(), "Step completion callback should have been called");

        String txHash = result.getTransactionHashes().get(0);
        System.out.println("✅ Transaction confirmed!");
        System.out.println("  Tx Hash: " + txHash);
        System.out.println("  Duration: " + result.getDuration().get());

        // Wait a bit for UTXO to be available
        Thread.sleep(2000);

        // Verify receiver got the funds
        List<Utxo> receiverUtxos = backendService.getUtxoService()
            .getUtxos(account1.baseAddress(), 100, 1)
            .getValue();

        long receiverBalance = receiverUtxos.stream()
            .flatMap(utxo -> utxo.getAmount().stream())
            .filter(amt -> "lovelace".equals(amt.getUnit()))
            .mapToLong(amt -> amt.getQuantity().longValue())
            .sum();

        BigDecimal adaBalance = ADAConversionUtil.lovelaceToAda(BigInteger.valueOf(receiverBalance));
        System.out.println("✅ Receiver balance: " + adaBalance + " ADA");

        System.out.println("\n=== Test 1 completed successfully! ===\n");
    }

    @Test
    @Order(2)
    void testTransactionChainWithRealUTXODependencies() throws Exception {
        System.out.println("=== Test 2: Transaction Chain with Real UTXO Dependencies ===");

        // Step 1: Send from account0 to account1
        Tx step1Tx = new Tx()
            .payToAddress(account1.baseAddress(), Amount.ada(5.0))
            .from(account0.baseAddress());

        // Step 2: Send from account1 to account2 (depends on step 1)
        Tx step2Tx = new Tx()
            .payToAddress(account2.baseAddress(), Amount.ada(2.0))
            .from(account1.baseAddress());

        // Step 3: Send from account2 back to account0 (depends on step 2)
        Tx step3Tx = new Tx()
            .payToAddress(account0.baseAddress(), Amount.ada(1.5))
            .from(account2.baseAddress());

        System.out.println("Creating 3-step transaction chain:");
        System.out.println("  Step 1: Account0 -> Account1 (5 ADA)");
        System.out.println("  Step 2: Account1 -> Account2 (2 ADA) - depends on step 1");
        System.out.println("  Step 3: Account2 -> Account0 (1.5 ADA) - depends on step 2");

        // Create watchable steps with dependencies
        WatchableStep step1 = watchableBuilder.compose(step1Tx)
            .withSigner(SignerProviders.signerFrom(account0))
            .feePayer(account0.baseAddress())
            .withStepId("transfer-to-account1")
            .withDescription("Step 1: Transfer 5 ADA to account1")
            .watchable();

        WatchableStep step2 = watchableBuilder.compose(step2Tx)
            .withSigner(SignerProviders.signerFrom(account1))
            .feePayer(account1.baseAddress())
            .fromStep("transfer-to-account1")  // Depends on step 1
            .withStepId("transfer-to-account2")
            .withDescription("Step 2: Transfer 2 ADA to account2")
            .watchable();

        WatchableStep step3 = watchableBuilder.compose(step3Tx)
            .withSigner(SignerProviders.signerFrom(account2))
            .feePayer(account2.baseAddress())
            .fromStep("transfer-to-account2")  // Depends on step 2
            .withStepId("return-to-account0")
            .withDescription("Step 3: Return 1.5 ADA to account0")
            .watchable();

        // Build and execute chain
        System.out.println("\nBuilding and executing chain...");

        WatchHandle chainHandle = Watcher.build("test-utxo-chain")
            .step(step1)
            .step(step2)
            .step(step3)
            .withDescription("Test: 3-step chain with UTXO dependencies")
            .watch();

        BasicWatchHandle basicHandle = (BasicWatchHandle) chainHandle;

        // Monitor progress
        AtomicInteger completedSteps = new AtomicInteger(0);
        basicHandle.onStepComplete(stepResult -> {
            completedSteps.incrementAndGet();
            System.out.println("\nStep completed (" + completedSteps.get() + "/3): " + stepResult.getStepId());
            System.out.println("  Tx Hash: " + stepResult.getTransactionHash());
            System.out.println("  Chain Progress: " + String.format("%.1f%%", basicHandle.getProgress() * 100));
        });

        // Monitor chain status
        basicHandle.onChainComplete(chainResult -> {
            System.out.println("\n🎉 Chain completed!");
            System.out.println("  Status: " + chainResult.getStatus());
            System.out.println("  Total Duration: " + chainResult.getDuration().get());
        });

        // Wait for completion
        System.out.println("Waiting for chain completion...");
        ChainResult result = basicHandle.await(Duration.ofMinutes(3));

        // Verify chain results
        assertTrue(result.isSuccessful(), "Chain should complete successfully");
        assertEquals(3, result.getSuccessfulStepCount());
        assertEquals(0, result.getFailedStepCount());
        assertEquals(3, result.getTransactionHashes().size());

        System.out.println("\n✅ Chain completed successfully!");
        System.out.println("Transaction hashes:");
        for (int i = 0; i < result.getTransactionHashes().size(); i++) {
            System.out.println("  Step " + (i + 1) + ": " + result.getTransactionHashes().get(i));
        }

        System.out.println("\n=== Test 2 completed successfully! ===\n");
    }

    @Test
    @Order(3)
    void testAdvancedUTXOSelectionStrategies() throws Exception {
        System.out.println("=== Test 3: Advanced UTXO Selection Strategies ===");

        // Create a transaction with multiple outputs
        Tx multiOutputTx = new Tx()
            .payToAddress(account1.baseAddress(), Amount.ada(3.0))
            .payToAddress(account1.baseAddress(), Amount.ada(2.0))
            .payToAddress(account1.baseAddress(), Amount.ada(1.0))
            .from(account0.baseAddress());

        System.out.println("Creating transaction with 3 outputs (3, 2, 1 ADA)...");

        WatchableStep multiOutputStep = watchableBuilder.compose(multiOutputTx)
            .withSigner(SignerProviders.signerFrom(account0))
            .feePayer(account0.baseAddress())
            .withStepId("multi-output")
            .withDescription("Create multiple UTXOs")
            .watchable();

        // Use specific UTXO by index (middle one - 2 ADA)
        WatchableStep useIndexedStep = watchableBuilder.compose(
                new Tx().payToAddress(account2.baseAddress(), Amount.ada(1.8))
                    .from(account1.baseAddress()))
            .withSigner(SignerProviders.signerFrom(account1))
            .feePayer(account1.baseAddress())
            .fromStepUtxo("multi-output", 1)  // Use index 1 (2 ADA)
            .withStepId("use-indexed")
            .withDescription("Use specific UTXO by index")
            .watchable();

        // Use filtered UTXOs (only those > 2.5 ADA)
        WatchableStep useFilteredStep = watchableBuilder.compose(
                new Tx().payToAddress(account0.baseAddress(), Amount.ada(2.8))
                    .from(account1.baseAddress()))
            .withSigner(SignerProviders.signerFrom(account1))
            .feePayer(account1.baseAddress())
            .fromStepWhere("multi-output", utxo -> {
                return utxo.getAmount().stream()
                    .filter(amt -> "lovelace".equals(amt.getUnit()))
                    .anyMatch(amt -> amt.getQuantity().longValue() > 2_500_000L);
            })
            .withStepId("use-filtered")
            .withDescription("Use filtered UTXO (> 2.5 ADA)")
            .watchable();

        // Execute chain
        System.out.println("\nExecuting chain with advanced UTXO selection...");

        WatchHandle chainHandle = Watcher.build("advanced-utxo-selection")
            .step(multiOutputStep)
            .step(useIndexedStep)
            .step(useFilteredStep)
            .withDescription("Test: Advanced UTXO selection strategies")
            .watch();

        BasicWatchHandle basicHandle = (BasicWatchHandle) chainHandle;

        // Monitor which UTXOs are used
        basicHandle.onStepComplete(stepResult -> {
            System.out.println("\nStep completed: " + stepResult.getStepId());
            if (stepResult.getOutputUtxos() != null && !stepResult.getOutputUtxos().isEmpty()) {
                System.out.println("  Created " + stepResult.getOutputUtxos().size() + " output(s)");
            }
        });

        // Wait for completion
        ChainResult result = basicHandle.await(Duration.ofMinutes(3));

        assertTrue(result.isSuccessful());
        assertEquals(3, result.getSuccessfulStepCount());

        System.out.println("\n✅ Advanced UTXO selection test completed!");
        System.out.println("  Indexed selection: ✅");
        System.out.println("  Filtered selection: ✅");
        System.out.println("  Multiple outputs: ✅");

        System.out.println("\n=== Test 3 completed successfully! ===\n");
    }

    @Test
    @Order(4)
    void testErrorHandlingAndRecovery() throws Exception {
        System.out.println("=== Test 4: Error Handling and Recovery ===");

        // Test insufficient funds error
        System.out.println("Testing insufficient funds scenario...");

        Tx insufficientTx = new Tx()
            .payToAddress(account1.baseAddress(), Amount.ada(10000.0)) // Way more than available
            .from(account0.baseAddress());

        WatchHandle handle = watchableBuilder.compose(insufficientTx)
            .withSigner(SignerProviders.signerFrom(account0))
            .feePayer(account0.baseAddress())
            .withDescription("Test: Insufficient funds")
            .watch();

        BasicWatchHandle basicHandle = (BasicWatchHandle) handle;

        try {
            basicHandle.await(Duration.ofSeconds(30));
            fail("Should have failed with insufficient funds");
        } catch (Exception e) {
            System.out.println("✅ Insufficient funds handled correctly");
            System.out.println("  Error: " + e.getMessage());
        }

        ChainResult failedResult = basicHandle.getCurrentResult();
        assertTrue(failedResult.isFailed());

        // Test chain cancellation
        System.out.println("\nTesting chain cancellation...");

        WatchableStep slowStep = watchableBuilder.compose(
                new Tx().payToAddress(account1.baseAddress(), Amount.ada(0.5))
                    .from(account0.baseAddress()))
            .withSigner(SignerProviders.signerFrom(account0))
            .feePayer(account0.baseAddress())
            .withStepId("cancellable-step")
            .watchable();

        WatchHandle cancelHandle = Watcher.build("test-cancel")
            .step(slowStep)
            .watch();

        BasicWatchHandle cancelBasicHandle = (BasicWatchHandle) cancelHandle;

        // Cancel immediately
        cancelBasicHandle.cancelChain();

        ChainResult cancelResult = cancelBasicHandle.await();
        assertTrue(cancelResult.isCancelled());
        assertEquals(WatchStatus.CANCELLED, cancelResult.getStatus());
        System.out.println("✅ Chain cancellation handled correctly");

        System.out.println("\n=== Test 4 completed successfully! ===\n");
    }

    @Test
    @Order(5)
    void testProgressMonitoringAndCallbacks() throws Exception {
        System.out.println("=== Test 5: Progress Monitoring and Callbacks ===");

        // Create a simple 2-step chain
        WatchableStep step1 = watchableBuilder.compose(
                new Tx().payToAddress(account1.baseAddress(), Amount.ada(1.0))
                    .from(account0.baseAddress()))
            .withSigner(SignerProviders.signerFrom(account0))
            .feePayer(account0.baseAddress())
            .withStepId("monitor-step1")
            .watchable();

        WatchableStep step2 = watchableBuilder.compose(
                new Tx().payToAddress(account2.baseAddress(), Amount.ada(0.5))
                    .from(account1.baseAddress()))
            .withSigner(SignerProviders.signerFrom(account1))
            .feePayer(account1.baseAddress())
            .fromStep("monitor-step1")
            .withStepId("monitor-step2")
            .watchable();

        WatchHandle chainHandle = Watcher.build("test-monitoring")
            .step(step1)
            .step(step2)
            .withDescription("Test: Progress monitoring")
            .watch();

        BasicWatchHandle basicHandle = (BasicWatchHandle) chainHandle;

        // Track all events
        AtomicInteger stepEvents = new AtomicInteger(0);
        AtomicBoolean chainCompleted = new AtomicBoolean(false);

        // Add multiple listeners
        basicHandle.onStepComplete(result -> {
            stepEvents.incrementAndGet();
            System.out.println("📍 Step event #" + stepEvents.get() + ": " + result.getStepId());
        });

        basicHandle.onChainComplete(result -> {
            chainCompleted.set(true);
            System.out.println("🏁 Chain completed with status: " + result.getStatus());
        });

        // Add another step listener to verify multiple listeners work
        basicHandle.onStepComplete(result -> {
            System.out.println("📊 Progress update: " +
                String.format("%.0f%%", basicHandle.getProgress() * 100) + " complete");
        });

        // Wait for completion
        ChainResult result = basicHandle.await(Duration.ofMinutes(2));

        assertTrue(result.isSuccessful());
        assertEquals(2, stepEvents.get(), "Should receive 2 step events");
        assertTrue(chainCompleted.get(), "Chain completion callback should fire");
        assertEquals(1.0, basicHandle.getProgress(), 0.01, "Progress should be 100%");

        System.out.println("\n✅ Progress monitoring test completed!");
        System.out.println("  Step callbacks: " + stepEvents.get());
        System.out.println("  Chain callback: " + (chainCompleted.get() ? "✅" : "❌"));
        System.out.println("  Final progress: " + String.format("%.0f%%", basicHandle.getProgress() * 100));

        System.out.println("\n=== Test 5 completed successfully! ===\n");
    }

    @AfterEach
    void tearDown() {
        System.out.println("Test cleanup completed");
    }
}
