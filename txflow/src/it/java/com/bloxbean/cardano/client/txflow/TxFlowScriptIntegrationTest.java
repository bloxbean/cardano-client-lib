package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutor;
import com.bloxbean.cardano.client.txflow.exec.FlowListener;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.*;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TxFlow with script transactions (ScriptTx).
 * <p>
 * This test class covers:
 * - Script lock/unlock flows
 * - Token minting flows
 * - Mixed Tx + ScriptTx chains
 * - Error scenarios (script validation failures)
 * - Retry behavior
 * <p>
 * Prerequisites:
 * - Yaci DevKit running at http://localhost:8080/api/v1/
 * - Run with: ./gradlew :txflow:integrationTest --tests "TxFlowScriptIntegrationTest" -Dyaci.integration.test=true
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TxFlowScriptIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
     private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

//    private static final String DEFAULT_MNEMONIC = "wrong rocket grow cram october dish elevator endorse tunnel any select health sport thing ticket paper hobby bulb club rare shoot order aerobic roof";
    private BackendService backendService;
    private QuickTxBuilder quickTxBuilder;
    private DefaultUtxoSupplier utxoSupplier;

    // Pre-funded accounts from Yaci DevKit
    private Account account0; // Index 0 - Primary sender
    private Account account1; // Index 1 - Receiver
    private Account account2; // Index 2 - Secondary

    // Always-true script for testing (PlutusV2)
    private PlutusV2Script alwaysTrueScript;
    private String scriptAddress;

    @BeforeEach
    void setUp() {
        System.out.println("\n=== TxFlow Script Integration Test Setup ===");

        // Initialize backend service
         backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");
//        backendService = new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, "preprodJ9lqAJFlWztmUtNfbwx2FOKmPmnQE5La");
        quickTxBuilder = new QuickTxBuilder(backendService);
        utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());

        // Create accounts from default mnemonic at different indices
        account0 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 0);
        account1 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 10);
        account2 = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 20);

        // Initialize always-true script
        alwaysTrueScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();
        scriptAddress = AddressProvider.getEntAddress(alwaysTrueScript, Networks.testnet()).toBech32();

        System.out.println("Using pre-funded Yaci DevKit accounts:");
        System.out.println("  Account 0: " + account0.baseAddress());
        System.out.println("  Account 1: " + account1.baseAddress());
        System.out.println("  Account 2: " + account2.baseAddress());
        System.out.println("  Script Address: " + scriptAddress);

        System.out.println("Setup complete!\n");
    }

    /**
     * Test 1: Script Lock/Unlock Flow
     * <p>
     * Two-step flow:
     * Step 1: Regular Tx locks funds at script address with inline datum
     * Step 2: ScriptTx unlocks funds using the datum as redeemer
     */
    @Test
    @Order(1)
    void testScriptLockUnlockFlow() throws Exception {
        System.out.println("=== Test 1: Script Lock/Unlock Flow ===");

        // Create a unique datum for this test
        Random rand = new Random();
        int randValue = rand.nextInt(1000000);
        BigIntPlutusData datum = new BigIntPlutusData(BigInteger.valueOf(randValue));

        BigInteger lockAmount = BigInteger.valueOf(5_000_000); // 5 ADA

        System.out.println("Test parameters:");
        System.out.println("  Datum value: " + randValue);
        System.out.println("  Lock amount: " + lockAmount + " lovelace");

        // Step 1: Lock funds at script using regular QuickTxBuilder
        System.out.println("\nStep 1: Locking funds at script address...");
        var lockResult = quickTxBuilder.compose(new Tx()
                        .payToContract(scriptAddress, Amount.lovelace(lockAmount), datum)
                        .from(account0.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account0))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful(), "Lock transaction should succeed");
        System.out.println("Lock tx: " + lockResult.getValue());

        // Wait for UTXO to be available
        waitForUtxo(lockResult.getValue(), scriptAddress);

        // Find the script UTXO
        Optional<Utxo> scriptUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, datum);
        assertTrue(scriptUtxo.isPresent(), "Script UTXO should be found");
        System.out.println("Found script UTXO: " + scriptUtxo.get().getTxHash() + "#" + scriptUtxo.get().getOutputIndex());

        // Step 2: Unlock using TxFlow with ScriptTx and withTxContext
        System.out.println("\nStep 2: Unlocking funds from script...");
        Utxo utxoToUnlock = scriptUtxo.get();
        TxFlow unlockFlow = TxFlow.builder("unlock-flow")
                .withDescription("Unlock funds from script")
                .addStep(FlowStep.builder("unlock")
                        .withDescription("Unlock funds using ScriptTx")
                        .withTxContext(builder -> builder
                                .compose(new ScriptTx()
                                        .collectFrom(utxoToUnlock, datum)
                                        .payToAddress(account1.baseAddress(), Amount.lovelace(lockAmount.subtract(BigInteger.valueOf(500_000))))
                                        .attachSpendingValidator(alwaysTrueScript)
                                        .withChangeAddress(account0.baseAddress()))
                                .feePayer(account0.baseAddress())
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult unlockResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(unlockFlow);

        assertTrue(unlockResult.isSuccessful(), "Unlock flow should succeed: " +
                (unlockResult.getError() != null ? unlockResult.getError().getMessage() : ""));
        assertEquals(1, unlockResult.getCompletedStepCount());

        System.out.println("Unlock tx: " + unlockResult.getTransactionHashes().get(0));
        System.out.println("\n=== Test 1 completed successfully! ===\n");
    }

    /**
     * Test 2: Script to Script Flow (Partial Unlock)
     * <p>
     * Three-step flow:
     * Step 1: Lock 10 ADA at script
     * Step 2: Unlock 5 ADA, return 5 ADA to script (with datum)
     * Step 3: Unlock remaining 5 ADA
     */
    @Test
    @Order(2)
    void testScriptToScriptFlow() throws Exception {
        System.out.println("=== Test 2: Script to Script Flow (Partial Unlock) ===");

        Random rand = new Random();
        int randValue = rand.nextInt(1000000) + 1000000; // Different range from test 1
        BigIntPlutusData datum = new BigIntPlutusData(BigInteger.valueOf(randValue));

        BigInteger totalAmount = BigInteger.valueOf(10_000_000); // 10 ADA
        BigInteger partialAmount = BigInteger.valueOf(5_000_000); // 5 ADA

        System.out.println("Test parameters:");
        System.out.println("  Datum value: " + randValue);
        System.out.println("  Total lock amount: " + totalAmount + " lovelace");
        System.out.println("  Partial unlock: " + partialAmount + " lovelace");

        // Step 1: Lock 10 ADA at script
        System.out.println("\nStep 1: Locking 10 ADA at script address...");
        var lockResult = quickTxBuilder.compose(new Tx()
                        .payToContract(scriptAddress, Amount.lovelace(totalAmount), datum)
                        .from(account0.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account0))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful(), "Lock transaction should succeed");
        waitForUtxo(lockResult.getValue(), scriptAddress);

        // Find script UTXO
        Optional<Utxo> scriptUtxo1 = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, datum);
        assertTrue(scriptUtxo1.isPresent(), "Script UTXO should be found");

        // Step 2: Partial unlock - send 5 ADA to receiver, keep 5 ADA at script
        System.out.println("\nStep 2: Partial unlock (5 ADA out, 5 ADA back to script)...");
        Utxo utxoToPartialUnlock = scriptUtxo1.get();
        TxFlow partialUnlockFlow = TxFlow.builder("partial-unlock")
                .withDescription("Partially unlock from script")
                .addStep(FlowStep.builder("partial-unlock")
                        .withTxContext(builder -> builder
                                .compose(new ScriptTx()
                                        .collectFrom(utxoToPartialUnlock, datum)
                                        .payToAddress(account1.baseAddress(), Amount.lovelace(partialAmount.subtract(BigInteger.valueOf(500_000))))
                                        .attachSpendingValidator(alwaysTrueScript)
                                        .withChangeAddress(scriptAddress, datum)) // Change goes back to script with datum
                                .feePayer(account0.baseAddress())
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult partialResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(partialUnlockFlow);

        assertTrue(partialResult.isSuccessful(), "Partial unlock should succeed: " +
                (partialResult.getError() != null ? partialResult.getError().getMessage() : ""));
        waitForUtxo(partialResult.getTransactionHashes().get(0), scriptAddress);

        // Find remaining script UTXO
        Optional<Utxo> scriptUtxo2 = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, datum);
        assertTrue(scriptUtxo2.isPresent(), "Remaining script UTXO should be found");

        // Step 3: Full unlock of remaining funds
        System.out.println("\nStep 3: Final unlock (remaining funds)...");
        Utxo utxoToFinalUnlock = scriptUtxo2.get();
        TxFlow finalUnlockFlow = TxFlow.builder("final-unlock")
                .withDescription("Unlock remaining funds from script")
                .addStep(FlowStep.builder("final-unlock")
                        .withTxContext(builder -> builder
                                .compose(new ScriptTx()
                                        .collectFrom(utxoToFinalUnlock, datum)
                                        .payToAddress(account2.baseAddress(), Amount.lovelace(BigInteger.valueOf(3_000_000)))
                                        .attachSpendingValidator(alwaysTrueScript)
                                        .withChangeAddress(account0.baseAddress()))
                                .feePayer(account0.baseAddress())
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult finalResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(finalUnlockFlow);

        assertTrue(finalResult.isSuccessful(), "Final unlock should succeed: " +
                (finalResult.getError() != null ? finalResult.getError().getMessage() : ""));

        System.out.println("Transaction hashes:");
        System.out.println("  Lock: " + lockResult.getValue());
        System.out.println("  Partial unlock: " + partialResult.getTransactionHashes().get(0));
        System.out.println("  Final unlock: " + finalResult.getTransactionHashes().get(0));

        System.out.println("\n=== Test 2 completed successfully! ===\n");
    }

    /**
     * Test 3: Token Minting Flow
     * <p>
     * Single-step flow using ScriptTx to mint tokens
     */
    @Test
    @Order(3)
    void testMintingFlow() throws Exception {
        System.out.println("=== Test 3: Token Minting Flow ===");

        // Minting script (always-true for testing)
        PlutusV2Script mintingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String tokenName = "TestToken" + System.currentTimeMillis();
        Asset mintAsset = new Asset(tokenName, BigInteger.valueOf(1000));

        System.out.println("Minting parameters:");
        System.out.println("  Policy ID: " + mintingScript.getPolicyId());
        System.out.println("  Token name: " + tokenName);
        System.out.println("  Amount: 1000");

        TxFlow mintFlow = TxFlow.builder("mint-flow")
                .withDescription("Mint tokens using ScriptTx")
                .addStep(FlowStep.builder("mint")
                        .withDescription("Mint 1000 tokens")
                        .withTxContext(builder -> builder
                                .compose(new ScriptTx()
                                        .mintAsset(mintingScript, mintAsset, PlutusData.unit(), account1.baseAddress()))
                                .feePayer(account0.baseAddress())
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult result = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(mintFlow);

        assertTrue(result.isSuccessful(), "Minting should succeed: " +
                (result.getError() != null ? result.getError().getMessage() : ""));
        assertEquals(1, result.getCompletedStepCount());

        System.out.println("Mint tx: " + result.getTransactionHashes().get(0));
        System.out.println("\n=== Test 3 completed successfully! ===\n");
    }

    /**
     * Test 4: Mint and Lock Flow
     * <p>
     * Two-step flow:
     * Step 1: Mint tokens
     * Step 2: Lock some tokens at script address
     */
    @Test
    @Order(4)
    void testMintAndLockFlow() throws Exception {
        System.out.println("=== Test 4: Mint and Lock Flow ===");

        PlutusV2Script mintingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String tokenName = "LockToken" + System.currentTimeMillis();
        Asset mintAsset = new Asset(tokenName, BigInteger.valueOf(1000));
        String policyId = mintingScript.getPolicyId();

        Random rand = new Random();
        BigIntPlutusData lockDatum = new BigIntPlutusData(BigInteger.valueOf(rand.nextInt(1000000) + 2000000));

        System.out.println("Test parameters:");
        System.out.println("  Policy ID: " + policyId);
        System.out.println("  Token name: " + tokenName);
        System.out.println("  Mint amount: 1000");
        System.out.println("  Lock amount: 500 tokens");

        // Step 1: Mint tokens
        System.out.println("\nStep 1: Minting tokens...");
        TxFlow mintFlow = TxFlow.builder("mint-tokens")
                .addStep(FlowStep.builder("mint")
                        .withTxContext(builder -> builder
                                .compose(new ScriptTx()
                                        .mintAsset(mintingScript, mintAsset, PlutusData.unit(), account0.baseAddress()))
                                .feePayer(account0.baseAddress())
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult mintResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(mintFlow);

        assertTrue(mintResult.isSuccessful(), "Minting should succeed: " +
                (mintResult.getError() != null ? mintResult.getError().getMessage() : ""));
        waitForUtxo(mintResult.getTransactionHashes().get(0), account0.baseAddress());

        // Step 2: Lock 500 tokens at script address
        System.out.println("\nStep 2: Locking 500 tokens at script...");
        TxFlow lockFlow = TxFlow.builder("lock-tokens")
                .addStep(FlowStep.builder("lock-tokens")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToContract(scriptAddress,
                                                List.of(Amount.ada(2), Amount.asset(policyId, tokenName, BigInteger.valueOf(500))),
                                                lockDatum)
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult lockResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(lockFlow);

        assertTrue(lockResult.isSuccessful(), "Lock should succeed: " +
                (lockResult.getError() != null ? lockResult.getError().getMessage() : ""));

        System.out.println("Transaction hashes:");
        System.out.println("  Mint: " + mintResult.getTransactionHashes().get(0));
        System.out.println("  Lock: " + lockResult.getTransactionHashes().get(0));

        System.out.println("\n=== Test 4 completed successfully! ===\n");
    }

    /**
     * Test 5: Regular Tx to ScriptTx Chain
     * <p>
     * Chain: Regular Tx output -> ScriptTx input
     * Step 1: Tx sends ADA to account1
     * Step 2: ScriptTx (from account1) locks at script
     * Step 3: ScriptTx unlocks from script
     */
    @Test
    @Order(5)
    void testRegularTxToScriptTxChain() throws Exception {
        System.out.println("=== Test 5: Regular Tx to ScriptTx Chain ===");

        Random rand = new Random();
        BigIntPlutusData datum = new BigIntPlutusData(BigInteger.valueOf(rand.nextInt(1000000) + 3000000));
        BigInteger transferAmount = BigInteger.valueOf(8_000_000); // 8 ADA

        System.out.println("Test parameters:");
        System.out.println("  Datum value: " + datum.getValue());
        System.out.println("  Transfer amount: " + transferAmount + " lovelace");

        // Step 1: Regular Tx - account0 sends to account1
        System.out.println("\nStep 1: Regular Tx (account0 -> account1)...");
        TxFlow transferFlow = TxFlow.builder("transfer-to-account1")
                .addStep(FlowStep.builder("transfer")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.lovelace(transferAmount))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult transferResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(transferFlow);

        assertTrue(transferResult.isSuccessful(), "Transfer should succeed");
        waitForUtxo(transferResult.getTransactionHashes().get(0), account1.baseAddress());

        // Step 2: Account1 locks at script using regular Tx
        System.out.println("\nStep 2: Lock at script (account1 -> script)...");
        TxFlow lockFlow = TxFlow.builder("lock-at-script")
                .addStep(FlowStep.builder("lock")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToContract(scriptAddress, Amount.lovelace(BigInteger.valueOf(5_000_000)), datum)
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        FlowResult lockResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(lockFlow);

        assertTrue(lockResult.isSuccessful(), "Lock should succeed");
        waitForUtxo(lockResult.getTransactionHashes().get(0), scriptAddress);

        // Find script UTXO
        Optional<Utxo> scriptUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, datum);
        assertTrue(scriptUtxo.isPresent(), "Script UTXO should be found");

        // Step 3: Unlock from script using ScriptTx
        System.out.println("\nStep 3: Unlock from script (ScriptTx)...");
        Utxo utxoToUnlock = scriptUtxo.get();
        TxFlow unlockFlow = TxFlow.builder("unlock-from-script")
                .addStep(FlowStep.builder("unlock")
                        .withTxContext(builder -> builder
                                .compose(new ScriptTx()
                                        .collectFrom(utxoToUnlock, datum)
                                        .payToAddress(account2.baseAddress(), Amount.lovelace(BigInteger.valueOf(4_000_000)))
                                        .attachSpendingValidator(alwaysTrueScript)
                                        .withChangeAddress(account0.baseAddress()))
                                .feePayer(account0.baseAddress())
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult unlockResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(unlockFlow);

        assertTrue(unlockResult.isSuccessful(), "Unlock should succeed: " +
                (unlockResult.getError() != null ? unlockResult.getError().getMessage() : ""));

        System.out.println("Transaction hashes:");
        System.out.println("  Transfer: " + transferResult.getTransactionHashes().get(0));
        System.out.println("  Lock: " + lockResult.getTransactionHashes().get(0));
        System.out.println("  Unlock: " + unlockResult.getTransactionHashes().get(0));

        System.out.println("\n=== Test 5 completed successfully! ===\n");
    }

    /**
     * Test 6: ScriptTx to Regular Tx Chain
     * <p>
     * Chain: Script unlock -> Regular payment
     * Setup: Lock funds at script
     * Step 1: ScriptTx unlocks to account1
     * Step 2: Tx from account1 pays to account2
     */
    @Test
    @Order(6)
    void testScriptTxToRegularTxChain() throws Exception {
        System.out.println("=== Test 6: ScriptTx to Regular Tx Chain ===");

        Random rand = new Random();
        BigIntPlutusData datum = new BigIntPlutusData(BigInteger.valueOf(rand.nextInt(1000000) + 4000000));
        BigInteger lockAmount = BigInteger.valueOf(8_000_000); // 8 ADA

        System.out.println("Test parameters:");
        System.out.println("  Datum value: " + datum.getValue());
        System.out.println("  Lock amount: " + lockAmount + " lovelace");

        // Setup: Lock funds at script
        System.out.println("\nSetup: Locking funds at script...");
        var lockResult = quickTxBuilder.compose(new Tx()
                        .payToContract(scriptAddress, Amount.lovelace(lockAmount), datum)
                        .from(account0.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account0))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful(), "Lock should succeed");
        waitForUtxo(lockResult.getValue(), scriptAddress);

        // Find script UTXO
        Optional<Utxo> scriptUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, datum);
        assertTrue(scriptUtxo.isPresent(), "Script UTXO should be found");

        // Step 1: Unlock from script to account1
        System.out.println("\nStep 1: Unlock from script to account1...");
        Utxo utxoToUnlock = scriptUtxo.get();
        TxFlow unlockFlow = TxFlow.builder("unlock-to-account1")
                .addStep(FlowStep.builder("unlock")
                        .withTxContext(builder -> builder
                                .compose(new ScriptTx()
                                        .collectFrom(utxoToUnlock, datum)
                                        .payToAddress(account1.baseAddress(), Amount.lovelace(BigInteger.valueOf(6_000_000)))
                                        .attachSpendingValidator(alwaysTrueScript)
                                        .withChangeAddress(account0.baseAddress()))
                                .feePayer(account0.baseAddress())
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult unlockResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(unlockFlow);

        assertTrue(unlockResult.isSuccessful(), "Unlock should succeed: " +
                (unlockResult.getError() != null ? unlockResult.getError().getMessage() : ""));
        waitForUtxo(unlockResult.getTransactionHashes().get(0), account1.baseAddress());

        // Step 2: Regular Tx from account1 to account2
        System.out.println("\nStep 2: Regular Tx (account1 -> account2)...");
        TxFlow paymentFlow = TxFlow.builder("payment-to-account2")
                .addStep(FlowStep.builder("payment")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account2.baseAddress(), Amount.lovelace(BigInteger.valueOf(4_000_000)))
                                        .from(account1.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account1)))
                        .build())
                .build();

        FlowResult paymentResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(paymentFlow);

        assertTrue(paymentResult.isSuccessful(), "Payment should succeed");

        System.out.println("Transaction hashes:");
        System.out.println("  Lock: " + lockResult.getValue());
        System.out.println("  Unlock: " + unlockResult.getTransactionHashes().get(0));
        System.out.println("  Payment: " + paymentResult.getTransactionHashes().get(0));

        System.out.println("\n=== Test 6 completed successfully! ===\n");
    }

    /**
     * Test 7: Script Validation Failure
     * <p>
     * Test that FlowResult properly captures script validation failures.
     * Lock with datum X, try to unlock with wrong redeemer Y (for a strict script).
     */
    @Test
    @Order(7)
    void testScriptValidationFailure() throws Exception {
        System.out.println("=== Test 7: Script Validation Failure ===");

        // Use a script that requires specific redeemer (sum script)
        // This script requires the redeemer to be 36 when datum is 8
        PlutusV2Script sumScript = PlutusV2Script.builder()
                .cborHex("5907a65907a3010000323322323232323232323232323232323322323232323222232325335323232333573466e1ccc07000d200000201e01d3333573466e1cd55cea80224000466442466002006004646464646464646464646464646666ae68cdc39aab9d500c480008cccccccccccc88888888888848cccccccccccc00403403002c02802402001c01801401000c008cd405c060d5d0a80619a80b80c1aba1500b33501701935742a014666aa036eb94068d5d0a804999aa80dbae501a35742a01066a02e0446ae85401cccd5406c08dd69aba150063232323333573466e1cd55cea801240004664424660020060046464646666ae68cdc39aab9d5002480008cc8848cc00400c008cd40b5d69aba15002302e357426ae8940088c98c80c0cd5ce01881801709aab9e5001137540026ae854008c8c8c8cccd5cd19b8735573aa004900011991091980080180119a816bad35742a004605c6ae84d5d1280111931901819ab9c03103002e135573ca00226ea8004d5d09aba2500223263202c33573805a05805426aae7940044dd50009aba1500533501775c6ae854010ccd5406c07c8004d5d0a801999aa80dbae200135742a00460426ae84d5d1280111931901419ab9c029028026135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d55cf280089baa00135742a00860226ae84d5d1280211931900d19ab9c01b01a018375a00a6eb4014405c4c98c805ccd5ce24810350543500017135573ca00226ea800448c88c008dd6000990009aa80b911999aab9f0012500a233500930043574200460066ae880080508c8c8cccd5cd19b8735573aa004900011991091980080180118061aba150023005357426ae8940088c98c8050cd5ce00a80a00909aab9e5001137540024646464646666ae68cdc39aab9d5004480008cccc888848cccc00401401000c008c8c8c8cccd5cd19b8735573aa0049000119910919800801801180a9aba1500233500f014357426ae8940088c98c8064cd5ce00d00c80b89aab9e5001137540026ae854010ccd54021d728039aba150033232323333573466e1d4005200423212223002004357426aae79400c8cccd5cd19b875002480088c84888c004010dd71aba135573ca00846666ae68cdc3a801a400042444006464c6403666ae7007006c06406005c4d55cea80089baa00135742a00466a016eb8d5d09aba2500223263201533573802c02a02626ae8940044d5d1280089aab9e500113754002266aa002eb9d6889119118011bab00132001355014223233335573e0044a010466a00e66442466002006004600c6aae754008c014d55cf280118021aba200301213574200222440042442446600200800624464646666ae68cdc3a800a40004642446004006600a6ae84d55cf280191999ab9a3370ea0049001109100091931900819ab9c01101000e00d135573aa00226ea80048c8c8cccd5cd19b875001480188c848888c010014c01cd5d09aab9e500323333573466e1d400920042321222230020053009357426aae7940108cccd5cd19b875003480088c848888c004014c01cd5d09aab9e500523333573466e1d40112000232122223003005375c6ae84d55cf280311931900819ab9c01101000e00d00c00b135573aa00226ea80048c8c8cccd5cd19b8735573aa004900011991091980080180118029aba15002375a6ae84d5d1280111931900619ab9c00d00c00a135573ca00226ea80048c8cccd5cd19b8735573aa002900011bae357426aae7940088c98c8028cd5ce00580500409baa001232323232323333573466e1d4005200c21222222200323333573466e1d4009200a21222222200423333573466e1d400d2008233221222222233001009008375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c4664424444444660040120106eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc8848888888cc018024020c030d5d0a8049bae357426ae8940248cccd5cd19b875006480088c848888888c01c020c034d5d09aab9e500b23333573466e1d401d2000232122222223005008300e357426aae7940308c98c804ccd5ce00a00980880800780700680600589aab9d5004135573ca00626aae7940084d55cf280089baa0012323232323333573466e1d400520022333222122333001005004003375a6ae854010dd69aba15003375a6ae84d5d1280191999ab9a3370ea0049000119091180100198041aba135573ca00c464c6401866ae700340300280244d55cea80189aba25001135573ca00226ea80048c8c8cccd5cd19b875001480088c8488c00400cdd71aba135573ca00646666ae68cdc3a8012400046424460040066eb8d5d09aab9e500423263200933573801401200e00c26aae7540044dd500089119191999ab9a3370ea00290021091100091999ab9a3370ea00490011190911180180218031aba135573ca00846666ae68cdc3a801a400042444004464c6401466ae7002c02802001c0184d55cea80089baa0012323333573466e1d40052002200923333573466e1d40092000200923263200633573800e00c00800626aae74dd5000a4c240029210350543100320013550032225335333573466e1c0092000005004100113300333702004900119b80002001122002122001112323001001223300330020020011")
                .build();
        String sumScriptAddr = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();

        PlutusData correctDatum = new BigIntPlutusData(BigInteger.valueOf(8)); // datum = 8, requires redeemer = 36
        PlutusData wrongRedeemer = new BigIntPlutusData(BigInteger.valueOf(99)); // Wrong redeemer

        System.out.println("Test parameters:");
        System.out.println("  Script: sum script (requires redeemer = 36 when datum = 8)");
        System.out.println("  Datum: 8");
        System.out.println("  Wrong redeemer: 99 (should fail)");

        // Setup: Lock funds at sum script
        System.out.println("\nSetup: Locking funds at sum script...");
        var lockResult = quickTxBuilder.compose(new Tx()
                        .payToContract(sumScriptAddr, Amount.ada(4), correctDatum)
                        .from(account0.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account0))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful(), "Lock should succeed");
        waitForUtxo(lockResult.getValue(), sumScriptAddr);

        // Find script UTXO
        Optional<Utxo> scriptUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, sumScriptAddr, correctDatum);
        assertTrue(scriptUtxo.isPresent(), "Script UTXO should be found");

        // Try to unlock with wrong redeemer - should fail
        System.out.println("\nAttempting unlock with wrong redeemer (should fail)...");
        Utxo utxoToUnlock = scriptUtxo.get();
        TxFlow unlockFlow = TxFlow.builder("invalid-unlock")
                .addStep(FlowStep.builder("unlock-wrong-redeemer")
                        .withTxContext(builder -> builder
                                .compose(new ScriptTx()
                                        .collectFrom(utxoToUnlock, wrongRedeemer) // Wrong redeemer!
                                        .payToAddress(account1.baseAddress(), Amount.ada(3))
                                        .attachSpendingValidator(sumScript)
                                        .withChangeAddress(account0.baseAddress()))
                                .feePayer(account0.baseAddress())
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult result = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(unlockFlow);

        // Verify flow failed
        assertFalse(result.isSuccessful(), "Flow should fail due to script validation");
        assertTrue(result.isFailed(), "Flow should be marked as failed");
        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertNotNull(result.getError(), "Error should be captured");

        System.out.println("Flow failed as expected!");
        System.out.println("  Status: " + result.getStatus());
        System.out.println("  Error type: " + result.getError().getClass().getSimpleName());
        System.out.println("  Error message: " + result.getError().getMessage());

        // Verify failed step info
        Optional<FlowStepResult> failedStep = result.getFailedStep();
        assertTrue(failedStep.isPresent(), "Failed step should be recorded");
        assertEquals("unlock-wrong-redeemer", failedStep.get().getStepId());

        System.out.println("\n=== Test 7 completed successfully! ===\n");
    }

    /**
     * Test 8: Retry Behavior Test
     * <p>
     * Verify that retry policy works with FlowListener callbacks.
     */
    @Test
    @Order(8)
    void testRetryOnTransientFailure() throws Exception {
        System.out.println("=== Test 8: Retry Behavior Test ===");

        // Create a simple flow that will succeed on first try
        // We're testing that the retry infrastructure works, not actual retries

        AtomicInteger stepStartCount = new AtomicInteger(0);
        AtomicInteger stepCompleteCount = new AtomicInteger(0);
        AtomicInteger retryCount = new AtomicInteger(0);

        FlowListener retryTrackingListener = new FlowListener() {
            @Override
            public void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
                stepStartCount.incrementAndGet();
                System.out.println("Step started (count: " + stepStartCount.get() + "): " + step.getId());
            }

            @Override
            public void onStepCompleted(FlowStep step, FlowStepResult result) {
                stepCompleteCount.incrementAndGet();
                System.out.println("Step completed (count: " + stepCompleteCount.get() + "): " + step.getId());
            }

            @Override
            public void onStepRetry(FlowStep step, int attemptNumber, int maxAttempts, Throwable lastError) {
                retryCount.incrementAndGet();
                System.out.println("Step retry (attempt: " + attemptNumber + "/" + maxAttempts + "): " + step.getId());
            }

            @Override
            public void onFlowCompleted(TxFlow flow, FlowResult result) {
                System.out.println("Flow completed: " + flow.getId());
            }

            @Override
            public void onFlowFailed(TxFlow flow, FlowResult result) {
                System.out.println("Flow failed: " + flow.getId());
            }
        };

        // Configure retry policy (though this test won't trigger retries)
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .backoffStrategy(BackoffStrategy.FIXED)
                .initialDelay(Duration.ofSeconds(1))
                .build();

        TxFlow flow = TxFlow.builder("retry-test")
                .withDescription("Test retry behavior")
                .addStep(FlowStep.builder("simple-payment")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToAddress(account1.baseAddress(), Amount.ada(1))
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .withRetryPolicy(retryPolicy)
                        .build())
                .build();

        FlowResult result = FlowExecutor.create(backendService)
                .withDefaultRetryPolicy(retryPolicy)
                .withListener(retryTrackingListener)
                .executeSync(flow);

        assertTrue(result.isSuccessful(), "Flow should succeed");
        assertEquals(1, stepStartCount.get(), "Step should be started once");
        assertEquals(1, stepCompleteCount.get(), "Step should complete once");
        assertEquals(0, retryCount.get(), "No retries should occur for successful tx");

        System.out.println("\nRetry tracking results:");
        System.out.println("  Step starts: " + stepStartCount.get());
        System.out.println("  Step completions: " + stepCompleteCount.get());
        System.out.println("  Retries: " + retryCount.get());

        System.out.println("\n=== Test 8 completed successfully! ===\n");
    }

    /**
     * Test 9: Lock and Unlock in Single Flow (Advanced Usage)
     * <p>
     * This test demonstrates the most advanced usage of TxFlow:
     * A single flow that locks funds at a script in step 1, then unlocks them in step 2.
     * Step 2 depends on step 1's output and uses the chain-aware UTXO supplier to find
     * the locked UTXO even before it's confirmed on-chain.
     */
    @Test
    @Order(9)
    void testLockAndUnlockInSingleFlow() throws Exception {
        System.out.println("=== Test 9: Lock and Unlock in Single Flow (Advanced Usage) ===");

        // Create a unique datum for this test
        Random rand = new Random();
        int randValue = rand.nextInt(1000000) + 5000000;
        BigIntPlutusData datum = new BigIntPlutusData(BigInteger.valueOf(randValue));

        BigInteger lockAmount = BigInteger.valueOf(5_000_000); // 5 ADA
        BigInteger unlockAmount = BigInteger.valueOf(4_000_000); // 4 ADA (minus fees)

        System.out.println("Test parameters:");
        System.out.println("  Datum value: " + randValue);
        System.out.println("  Lock amount: " + lockAmount + " lovelace");
        System.out.println("  Unlock amount: " + unlockAmount + " lovelace");

        // Capture the script UTXO from step 1's output for use in step 2
        // We'll use FlowListener to track progress and AtomicReference to capture step results
        AtomicReference<FlowStepResult> step1Result = new AtomicReference<>();

        FlowListener progressListener = new FlowListener() {
            @Override
            public void onFlowStarted(TxFlow flow) {
                System.out.println("Flow started: " + flow.getId());
            }

            @Override
            public void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
                System.out.println("  Step " + (stepIndex + 1) + "/" + totalSteps + " started: " + step.getId());
            }

            @Override
            public void onStepCompleted(FlowStep step, FlowStepResult result) {
                System.out.println("  Step completed: " + step.getId() + " - Tx: " + result.getTransactionHash());
                if ("lock".equals(step.getId())) {
                    step1Result.set(result);
                    System.out.println("    Captured " + result.getOutputUtxos().size() + " output UTXOs from lock step");
                }
            }

            @Override
            public void onStepFailed(FlowStep step, FlowStepResult result) {
                System.out.println("  Step FAILED: " + step.getId());
                if (result.getError() != null) {
                    result.getError().printStackTrace();
                }
            }

            @Override
            public void onFlowCompleted(TxFlow flow, FlowResult result) {
                System.out.println("Flow completed: " + flow.getId() + " with " + result.getCompletedStepCount() + " steps");
            }

            @Override
            public void onFlowFailed(TxFlow flow, FlowResult result) {
                System.out.println("Flow FAILED: " + flow.getId());
                if (result.getError() != null) {
                    result.getError().printStackTrace();
                }
            }
        };

        System.out.println("Datum >> " + datum.serializeToHex());
        // Create a single flow with two steps: lock and unlock
        // The unlock step depends on the lock step and will use the chain-aware UTXO supplier
        TxFlow lockUnlockFlow = TxFlow.builder("lock-unlock-single-flow")
                .withDescription("Lock and unlock funds in a single flow execution")
                .addStep(FlowStep.builder("lock")
                        .withDescription("Lock 5 ADA at script address with inline datum")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToContract(scriptAddress, Amount.lovelace(lockAmount), datum)
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("unlock")
                        .withDescription("Unlock funds from script using ScriptTx")
                        .dependsOn("lock", SelectionStrategy.ALL) // Depend on lock step outputs
                        .withTxContext(builder -> {
                            return builder
                                    .compose(new ScriptTx()
                                            .collectFrom(scriptAddress,
                                                    utxo -> datum.serializeToHex().equals(utxo.getInlineDatum()), PlutusData.unit()) // Collect from script address
                                            .payToAddress(account1.baseAddress(), Amount.lovelace(unlockAmount))
                                            .attachSpendingValidator(alwaysTrueScript)
                                            .withChangeAddress(account0.baseAddress()))
                                    .feePayer(account0.baseAddress())
                                    // Use chain-aware UtxoSupplier for script cost evaluation
                                    // This allows the evaluator to see pending UTXOs from previous steps
                                    .withTxEvaluator(new AikenTransactionEvaluator(
                                            builder.getUtxoSupplier(),
                                            builder.getProtocolParamsSupplier()))
                                    .withSigner(SignerProviders.signerFrom(account0));
                        })
                        .build())
                .build();

        FlowResult result = FlowExecutor.create(backendService)
                .withListener(progressListener)
                .withChainingMode(ChainingMode.PIPELINED)
                .executeSync(lockUnlockFlow);


        System.out.println("\nExecuting lock-unlock flow...");
        System.out.println("  Step 1: Lock " + lockAmount + " lovelace at script");
        System.out.println("  Step 2: Unlock and send " + unlockAmount + " lovelace to account1");

//        // Execute the flow
//        FlowResult result = FlowExecutor.create(backendService)
//                .withListener(progressListener)
//                .withChainingMode(ChainingMode.PIPELINED)
//                .executeSync(lockUnlockFlow);

        // Verify results
        assertTrue(result.isSuccessful(), "Flow should complete successfully: " +
                (result.getError() != null ? result.getError().getMessage() : ""));
        assertEquals(2, result.getCompletedStepCount(), "Both steps should complete");
        assertEquals(2, result.getTransactionHashes().size(), "Should have 2 transaction hashes");

        System.out.println("\nFlow completed successfully!");
        System.out.println("Transaction hashes:");
        System.out.println("  Lock tx: " + result.getTransactionHashes().get(0));
        System.out.println("  Unlock tx: " + result.getTransactionHashes().get(1));

        // Verify the lock step captured the script output
        assertNotNull(step1Result.get(), "Should have captured step 1 result");
        assertTrue(step1Result.get().getOutputUtxos().size() > 0, "Lock step should have output UTXOs");

        System.out.println("\nLock step output UTXOs:");
        for (var utxo : step1Result.get().getOutputUtxos()) {
            System.out.println("  " + utxo.getTxHash() + "#" + utxo.getOutputIndex() + " @ " + utxo.getAddress());
        }

        System.out.println("\n=== Test 9 completed successfully! ===\n");
    }

    /**
     * Test 10: Lock and Unlock with Explicit UTXO Reference
     * <p>
     * Similar to Test 9, but demonstrates explicitly passing the locked UTXO
     * from step 1 to step 2 using a captured reference.
     */
    @Test
    @Order(10)
    void testLockAndUnlockWithExplicitUtxoReference() throws Exception {
        System.out.println("=== Test 10: Lock and Unlock with Explicit UTXO Reference ===");

        // Create a unique datum for this test
        Random rand = new Random();
        int randValue = rand.nextInt(1000000) + 6000000;
        BigIntPlutusData datum = new BigIntPlutusData(BigInteger.valueOf(randValue));

        BigInteger lockAmount = BigInteger.valueOf(6_000_000); // 6 ADA

        System.out.println("Test parameters:");
        System.out.println("  Datum value: " + randValue);
        System.out.println("  Lock amount: " + lockAmount + " lovelace");

        // Step 1: Lock funds at script
        System.out.println("\nStep 1: Locking funds at script address...");
        var lockResult = quickTxBuilder.compose(new Tx()
                        .payToContract(scriptAddress, Amount.lovelace(lockAmount), datum)
                        .from(account0.baseAddress()))
                .withSigner(SignerProviders.signerFrom(account0))
                .completeAndWait(System.out::println);

        assertTrue(lockResult.isSuccessful(), "Lock transaction should succeed");
        String lockTxHash = lockResult.getValue();
        System.out.println("Lock tx: " + lockTxHash);

        // Wait for UTXO to be available
        waitForUtxo(lockTxHash, scriptAddress);

        // Find the locked UTXO
        Optional<Utxo> lockedUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, datum);
        assertTrue(lockedUtxo.isPresent(), "Locked UTXO should be found");
        Utxo scriptUtxo = lockedUtxo.get();
        System.out.println("Found locked UTXO: " + scriptUtxo.getTxHash() + "#" + scriptUtxo.getOutputIndex());

        // Step 2: Create a flow that unlocks the specific UTXO
        System.out.println("\nStep 2: Creating unlock flow with explicit UTXO reference...");

        TxFlow unlockFlow = TxFlow.builder("explicit-unlock-flow")
                .withDescription("Unlock funds using explicit UTXO reference")
                .addStep(FlowStep.builder("unlock")
                        .withDescription("Unlock the specific UTXO locked in step 1")
                        .withTxContext(builder -> builder
                                .compose(new ScriptTx()
                                        .collectFrom(scriptUtxo, datum) // Use the specific UTXO
                                        .payToAddress(account1.baseAddress(), Amount.lovelace(lockAmount.subtract(BigInteger.valueOf(1_000_000))))
                                        .attachSpendingValidator(alwaysTrueScript)
                                        .withChangeAddress(account0.baseAddress()))
                                .feePayer(account0.baseAddress())
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        FlowResult unlockResult = FlowExecutor.create(backendService)
                .withListener(new LoggingFlowListener())
                .executeSync(unlockFlow);

        assertTrue(unlockResult.isSuccessful(), "Unlock flow should succeed: " +
                (unlockResult.getError() != null ? unlockResult.getError().getMessage() : ""));
        assertEquals(1, unlockResult.getCompletedStepCount());

        System.out.println("Unlock tx: " + unlockResult.getTransactionHashes().get(0));

        System.out.println("\nBoth lock and unlock completed successfully!");
        System.out.println("  Lock tx: " + lockTxHash);
        System.out.println("  Unlock tx: " + unlockResult.getTransactionHashes().get(0));

        System.out.println("\n=== Test 10 completed successfully! ===\n");
    }

    /**
     * Test 11: Batch Mode Submission
     * <p>
     * This test demonstrates BATCH mode where ALL transactions are built first,
     * then ALL are submitted in rapid succession. This maximizes the likelihood
     * of all transactions landing in the same block on fast networks.
     * <p>
     * Flow: Lock funds at script → Unlock funds (using BATCH mode)
     */
    @Test
    @Order(11)
    void testBatchModeSubmission() throws Exception {
        System.out.println("=== Test 11: Batch Mode Submission ===");

        Random rand = new Random();
        BigIntPlutusData datum = new BigIntPlutusData(BigInteger.valueOf(rand.nextInt(1000000) + 7000000));
        BigInteger lockAmount = BigInteger.valueOf(5_000_000);
        BigInteger unlockAmount = BigInteger.valueOf(4_000_000);

        System.out.println("Test parameters:");
        System.out.println("  Datum value: " + datum.getValue());
        System.out.println("  Lock amount: " + lockAmount + " lovelace");
        System.out.println("  Unlock amount: " + unlockAmount + " lovelace");
        System.out.println("  Mode: BATCH (build all first, then submit all)");

        TxFlow lockUnlockFlow = TxFlow.builder("batch-lock-unlock")
                .withDescription("Lock and unlock in BATCH mode")
                .addStep(FlowStep.builder("lock")
                        .withDescription("Lock funds at script address")
                        .withTxContext(builder -> builder
                                .compose(new Tx()
                                        .payToContract(scriptAddress, Amount.lovelace(lockAmount), datum)
                                        .from(account0.baseAddress()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .addStep(FlowStep.builder("unlock")
                        .withDescription("Unlock funds from script")
                        .dependsOn("lock", SelectionStrategy.ALL)
                        .withTxContext(builder -> builder
                                .compose(new ScriptTx()
                                        .collectFrom(scriptAddress,
                                                utxo -> datum.serializeToHex().equals(utxo.getInlineDatum()),
                                                PlutusData.unit())
                                        .payToAddress(account1.baseAddress(), Amount.lovelace(unlockAmount))
                                        .attachSpendingValidator(alwaysTrueScript)
                                        .withChangeAddress(account0.baseAddress()))
                                .feePayer(account0.baseAddress())
                                .withTxEvaluator(new AikenTransactionEvaluator(
                                        builder.getUtxoSupplier(),
                                        builder.getProtocolParamsSupplier()))
                                .withSigner(SignerProviders.signerFrom(account0)))
                        .build())
                .build();

        // Execute with BATCH mode - builds all transactions first, then submits all
        FlowResult result = FlowExecutor.create(backendService)
                .withChainingMode(ChainingMode.BATCH)
                .withListener(new LoggingFlowListener())
                .executeSync(lockUnlockFlow);

        assertTrue(result.isSuccessful(), "Batch flow should succeed: " +
                (result.getError() != null ? result.getError().getMessage() : ""));
        assertEquals(2, result.getCompletedStepCount());

        System.out.println("\nBatch mode completed successfully!");
        System.out.println("Transaction hashes:");
        System.out.println("  Lock tx: " + result.getTransactionHashes().get(0));
        System.out.println("  Unlock tx: " + result.getTransactionHashes().get(1));
        System.out.println("\nNote: In BATCH mode, both transactions were built first,");
        System.out.println("      then submitted within milliseconds of each other.");
        System.out.println("\n=== Test 11 completed successfully! ===\n");
    }

    // ============ Helper Methods ============

    /**
     * Wait for a UTXO to be available at an address.
     */
    private void waitForUtxo(String txHash, String address) {
        System.out.println("Waiting for UTXO at " + address + " (tx: " + txHash + ")...");
        int attempts = 0;
        while (attempts < 30) {
            try {
                List<Utxo> utxos = utxoSupplier.getAll(address);
                boolean found = utxos.stream()
                        .anyMatch(u -> u.getTxHash().equals(txHash));
                if (found) {
                    System.out.println("UTXO found after " + (attempts + 1) + " attempts");
                    return;
                }
            } catch (Exception e) {
                // Ignore and retry
            }
            attempts++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("Warning: UTXO not found after " + attempts + " attempts");
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
        public void onStepRetry(FlowStep step, int attemptNumber, int maxAttempts, Throwable lastError) {
            System.out.println("  Step retry (attempt " + attemptNumber + "/" + maxAttempts + "): " + step.getId());
            if (lastError != null) {
                System.out.println("    Last error: " + lastError.getMessage());
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
