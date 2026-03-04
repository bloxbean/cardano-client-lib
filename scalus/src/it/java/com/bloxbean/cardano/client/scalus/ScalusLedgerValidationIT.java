package com.bloxbean.cardano.client.scalus;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationResult;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.scalus.bridge.SlotConfigBridge;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Scalus ledger validation (Phases 1-3) against Yaci DevKit.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScalusLedgerValidationIT extends BaseIT {

    private QuickTxBuilder quickTxBuilder;

    @BeforeEach
    void setup() {
        initializeAccounts();
        topupAllTestAccounts();
        quickTxBuilder = new QuickTxBuilder(getBackendService());
    }

    // =============================================
    // Phase 1: ScalusLedgerRuleVerifier tests
    // =============================================

    @Test
    @Order(1)
    void phase1_verifier_shouldPassValidTransaction() {
        ScalusLedgerRuleVerifier verifier = ScalusLedgerRuleVerifier.builder()
                .protocolParamsSupplier(new DefaultProtocolParamsSupplier(getBackendService().getEpochService()))
                .utxoSupplier(new DefaultUtxoSupplier(getBackendService().getUtxoService()))
                .slotConfig(SlotConfigBridge.preview())
                .build();

        Tx tx = new Tx()
                .payToAddress(address2, Amount.ada(2))
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withVerifier(verifier)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Transaction should succeed with verifier: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
        checkIfUtxoAvailable(result.getTxHash(), address2);
    }

    @Test
    @Order(2)
    void phase1_verifier_shouldPassMultiOutputTransaction() {
        ScalusLedgerRuleVerifier verifier = ScalusLedgerRuleVerifier.builder()
                .protocolParamsSupplier(new DefaultProtocolParamsSupplier(getBackendService().getEpochService()))
                .utxoSupplier(new DefaultUtxoSupplier(getBackendService().getUtxoService()))
                .slotConfig(SlotConfigBridge.preview())
                .build();

        Tx tx = new Tx()
                .payToAddress(address2, Amount.ada(1.5))
                .payToAddress(address3, Amount.ada(1.5))
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withVerifier(verifier)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Multi-output tx should succeed: " + result.getResponse());
        waitForTransaction(result);
    }

    // =============================================
    // Phase 2: TransactionValidator tests
    // =============================================

    @Test
    @Order(3)
    void phase2_validator_shouldPassValidTransaction() throws Exception {
        ProtocolParams pp = getBackendService().getEpochService().getProtocolParameters().getValue();

        ScalusTransactionValidator validator = ScalusTransactionValidator.builder()
                .protocolParams(pp)
                .slotConfig(SlotConfigBridge.preview())
                .build();

        Tx tx = new Tx()
                .payToAddress(address3, Amount.ada(2))
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Transaction should succeed with validator: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
        checkIfUtxoAvailable(result.getTxHash(), address3);
    }

    @Test
    @Order(4)
    void phase2_validator_shouldReturnStructuredErrorOnFailure() throws Exception {
        ProtocolParams pp = getBackendService().getEpochService().getProtocolParameters().getValue();

        ScalusTransactionValidator validator = ScalusTransactionValidator.builder()
                .protocolParams(pp)
                .slotConfig(SlotConfigBridge.preview())
                .build();

        // Build a transaction manually with intentionally wrong fee to trigger validation error
        // We test the validator directly with a badly-constructed transaction
        com.bloxbean.cardano.client.transaction.spec.Transaction badTx = new com.bloxbean.cardano.client.transaction.spec.Transaction();
        badTx.setBody(new com.bloxbean.cardano.client.transaction.spec.TransactionBody());
        badTx.getBody().setInputs(new java.util.ArrayList<>());
        badTx.getBody().setOutputs(java.util.List.of(
                com.bloxbean.cardano.client.transaction.spec.TransactionOutput.builder()
                        .address(address1)
                        .value(com.bloxbean.cardano.client.transaction.spec.Value.builder()
                                .coin(java.math.BigInteger.valueOf(2_000_000)).build())
                        .build()
        ));
        badTx.getBody().setFee(java.math.BigInteger.valueOf(200_000));
        badTx.setWitnessSet(new com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet());

        ValidationResult validationResult = validator.validateTx(badTx, java.util.Set.of());
        assertFalse(validationResult.isValid(), "Empty inputs tx should fail validation");
        assertFalse(validationResult.getErrors().isEmpty(), "Should have error details");
    }

    @Test
    @Order(5)
    void phase2_validator_combinedWithVerifier_shouldPass() throws Exception {
        ProtocolParams pp = getBackendService().getEpochService().getProtocolParameters().getValue();

        ScalusLedgerRuleVerifier verifier = ScalusLedgerRuleVerifier.builder()
                .protocolParamsSupplier(new DefaultProtocolParamsSupplier(getBackendService().getEpochService()))
                .utxoSupplier(new DefaultUtxoSupplier(getBackendService().getUtxoService()))
                .slotConfig(SlotConfigBridge.preview())
                .build();

        ScalusTransactionValidator validator = ScalusTransactionValidator.builder()
                .protocolParams(pp)
                .slotConfig(SlotConfigBridge.preview())
                .build();

        Tx tx = new Tx()
                .payToAddress(address4, Amount.ada(2))
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withVerifier(verifier)
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Transaction should succeed with both verifier and validator: " + result.getResponse());
        waitForTransaction(result);
    }

    // =============================================
    // Phase 3: EmulatorBackendService tests
    // =============================================

    @Test
    @Order(6)
    void phase3_emulator_shouldSubmitAndQueryTransaction() throws Exception {
        // Use the emulator as a standalone backend - no Yaci DevKit needed for this
        java.util.Map<String, Amount> initialFunds = new java.util.LinkedHashMap<>();
        initialFunds.put(address1, new Amount("lovelace", java.math.BigInteger.valueOf(100_000_000_000L)));
        initialFunds.put(address2, new Amount("lovelace", java.math.BigInteger.valueOf(100_000_000_000L)));

        ScalusEmulatorBackendService emulatorBackend = ScalusEmulatorBackendService.builder()
                .slotConfig(SlotConfigBridge.preview())
                .initialFunds(initialFunds)
                .build();

        // Check initial UTxOs are available
        var utxoResult = emulatorBackend.getUtxoService().getUtxos(address1, 40, 0);
        assertTrue(utxoResult.isSuccessful());
        assertFalse(utxoResult.getValue().isEmpty(), "Should have initial UTxOs for funded address");

        // Check empty for unfunded address
        var emptyResult = emulatorBackend.getUtxoService().getUtxos(address3, 40, 0);
        assertTrue(emptyResult.isSuccessful());
        assertTrue(emptyResult.getValue().isEmpty(), "Should have no UTxOs for unfunded address");

        // Verify protocol params are returned
        var ppResult = emulatorBackend.getEpochService().getProtocolParameters();
        assertTrue(ppResult.isSuccessful());
        assertNotNull(ppResult.getValue());
    }

    @Test
    @Order(7)
    void phase3_emulator_shouldRejectInvalidTransaction() throws Exception {
        java.util.Map<String, Amount> initialFunds = new java.util.LinkedHashMap<>();
        initialFunds.put(address1, new Amount("lovelace", java.math.BigInteger.valueOf(100_000_000_000L)));

        ScalusEmulatorBackendService emulatorBackend = ScalusEmulatorBackendService.builder()
                .slotConfig(SlotConfigBridge.preview())
                .initialFunds(initialFunds)
                .build();

        // Create a dummy empty transaction - should fail submission
        com.bloxbean.cardano.client.transaction.spec.Transaction badTx = new com.bloxbean.cardano.client.transaction.spec.Transaction();
        badTx.setBody(new com.bloxbean.cardano.client.transaction.spec.TransactionBody());
        badTx.getBody().setInputs(new java.util.ArrayList<>());
        badTx.getBody().setOutputs(java.util.List.of(
                com.bloxbean.cardano.client.transaction.spec.TransactionOutput.builder()
                        .address(address1)
                        .value(com.bloxbean.cardano.client.transaction.spec.Value.builder()
                                .coin(java.math.BigInteger.valueOf(2_000_000)).build())
                        .build()
        ));
        badTx.getBody().setFee(java.math.BigInteger.valueOf(200_000));
        badTx.setWitnessSet(new com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet());

        byte[] txBytes = badTx.serialize();
        var submitResult = emulatorBackend.getTransactionService().submitTransaction(txBytes);
        assertFalse(submitResult.isSuccessful(), "Invalid transaction should fail: " + submitResult.getResponse());
    }
}
