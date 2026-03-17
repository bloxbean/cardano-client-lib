package com.bloxbean.cardano.client.scalus;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.model.ValidationResult;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.scalus.bridge.SlotConfigBridge;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ScalusTransactionValidatorTest {

    private ProtocolParams protocolParams;
    private Account sender;
    private String senderAddr;

    @BeforeEach
    void setUp() {
        protocolParams = ScalusTestFixtures.buildTestProtocolParams();
        sender = new Account(Networks.testnet());
        senderAddr = sender.baseAddress();
    }

    @Test
    void validateTx_shouldReturnFailureForEmptyInputs() {
        Transaction tx = new Transaction();
        tx.setBody(new TransactionBody());
        tx.getBody().setInputs(new ArrayList<>());
        tx.getBody().setOutputs(List.of(
                TransactionOutput.builder()
                        .address(senderAddr)
                        .value(Value.builder().coin(BigInteger.valueOf(2_000_000)).build())
                        .build()
        ));
        tx.getBody().setFee(BigInteger.valueOf(200_000));
        tx.setWitnessSet(new TransactionWitnessSet());

        ScalusTransactionValidator validator = ScalusTransactionValidator.builder()
                .protocolParamsSupplier(() -> protocolParams)
                .slotConfig(SlotConfigBridge.preview())
                .build();

        ValidationResult result = validator.validateTx(tx, Set.of());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void validateTx_shouldReturnFailureForInsufficientFee() {
        String txHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";

        Utxo inputUtxo = Utxo.builder()
                .txHash(txHash)
                .outputIndex(0)
                .address(senderAddr)
                .amount(List.of(
                        new Amount("lovelace", BigInteger.valueOf(10_000_000))
                ))
                .build();

        Transaction tx = new Transaction();
        tx.setBody(new TransactionBody());
        tx.getBody().setInputs(List.of(
                new TransactionInput(txHash, 0)
        ));
        tx.getBody().setOutputs(List.of(
                TransactionOutput.builder()
                        .address(senderAddr)
                        .value(Value.builder().coin(BigInteger.valueOf(10_000_000)).build())
                        .build()
        ));
        tx.getBody().setFee(BigInteger.valueOf(1)); // Fee too small
        tx.setWitnessSet(new TransactionWitnessSet());

        ScalusTransactionValidator validator = ScalusTransactionValidator.builder()
                .protocolParamsSupplier(() -> protocolParams)
                .slotConfig(SlotConfigBridge.preview())
                .build();

        ValidationResult result = validator.validateTx(tx, Set.of(inputUtxo));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        // Should be a Phase 1 error
        assertThat(result.getErrors().get(0).getPhase()).isEqualTo(ValidationError.Phase.PHASE_1);
    }

    @Test
    void validateTx_shouldReturnStructuredErrorInfo() {
        String txHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";

        Utxo inputUtxo = Utxo.builder()
                .txHash(txHash)
                .outputIndex(0)
                .address(senderAddr)
                .amount(List.of(
                        new Amount("lovelace", BigInteger.valueOf(10_000_000))
                ))
                .build();

        Transaction tx = new Transaction();
        tx.setBody(new TransactionBody());
        tx.getBody().setInputs(List.of(
                new TransactionInput(txHash, 0)
        ));
        tx.getBody().setOutputs(List.of(
                TransactionOutput.builder()
                        .address(senderAddr)
                        .value(Value.builder().coin(BigInteger.valueOf(10_000_000)).build())
                        .build()
        ));
        tx.getBody().setFee(BigInteger.valueOf(1));
        tx.setWitnessSet(new TransactionWitnessSet());

        ScalusTransactionValidator validator = ScalusTransactionValidator.builder()
                .protocolParamsSupplier(() -> protocolParams)
                .slotConfig(SlotConfigBridge.preview())
                .build();

        ValidationResult result = validator.validateTx(tx, Set.of(inputUtxo));

        assertThat(result.isValid()).isFalse();
        ValidationError error = result.getErrors().get(0);
        assertThat(error.getRule()).isNotNull();
        assertThat(error.getMessage()).isNotNull();
        assertThat(error.getPhase()).isNotNull();
    }

}
