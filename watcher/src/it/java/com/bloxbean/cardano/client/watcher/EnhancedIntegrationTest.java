package com.bloxbean.cardano.client.watcher;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.watcher.api.WatchHandle;
import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.chain.BasicWatchHandle;
import com.bloxbean.cardano.client.watcher.quicktx.WatchableQuickTxBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enhanced integration test with detailed error diagnostics.
 * 
 * This test shows how to get detailed error information when transactions fail.
 */
//@EnabledIfSystemProperty(named = "yaci.integration.test", matches = "true")
public class EnhancedIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String SENDER_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private Account senderAccount;
    private WatchableQuickTxBuilder watchableBuilder;
    private String receiverAddress = "addr_test1qrzufj3g0ua489yt235wtc3mrjrlucww2tqdnt7kt5rs09grsag6vxw5v053atks5a6whke03cf2qx3h3g2nhsmzwv3sgml3ed";

    @BeforeEach
    void setUp() {
        System.out.println("=== Enhanced Integration Test Setup ===");

        // Initialize Blockfrost-compatible backend service pointing to Yaci DevKit
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");

        // Create sender account from mnemonic
        senderAccount = new Account(Networks.testnet(), SENDER_MNEMONIC);
        System.out.println("Sender Address: " + senderAccount.baseAddress());

        // Create WatchableQuickTxBuilder using the new API
        watchableBuilder = new WatchableQuickTxBuilder(backendService);

        System.out.println("WatchableQuickTxBuilder created successfully!");
        System.out.println("Backend URL: " + YACI_BASE_URL);
        System.out.println();
    }

    @Test
    void testTransactionWithDetailedDiagnostics() throws Exception {
        System.out.println("=== Test: Transaction with Detailed Diagnostics ===");

        // Create a simple payment transaction
        Tx paymentTx = new Tx()
            .payToAddress(receiverAddress, Amount.ada(1.5))  // Send 1.5 ADA
            .from(senderAccount.baseAddress());

        System.out.println("Creating WatchableTxContext for 1.5 ADA payment");

        // Use the new WatchableQuickTxBuilder API
        WatchHandle handle = watchableBuilder.compose(paymentTx)
            .withSigner(SignerProviders.signerFrom(senderAccount))
            .feePayer(senderAccount.baseAddress())
            .withDescription("Enhanced Test: 1.5 ADA Payment with Diagnostics")
            .watch();

        assertNotNull(handle, "Watch handle should not be null");
        System.out.println("Watch started with ID: " + handle.getWatchId());

        // Enhanced error diagnostics
        if (handle instanceof BasicWatchHandle) {
            BasicWatchHandle basicHandle = (BasicWatchHandle) handle;
            WatchStatus status = basicHandle.getStatus();
            System.out.println("Current status: " + status);

            // Show detailed diagnostics if failed
            if (status == WatchStatus.FAILED) {
                // Use the diagnostic helper to show detailed error information
                DiagnosticHelper.printFailureDiagnostics(basicHandle);
                DiagnosticHelper.printLikelyCauses(basicHandle);
            } else {
                System.out.println("✅ Transaction did not fail - Status: " + status);
            }
        }

        System.out.println("=== Enhanced diagnostic test completed! ===\n");
    }
}