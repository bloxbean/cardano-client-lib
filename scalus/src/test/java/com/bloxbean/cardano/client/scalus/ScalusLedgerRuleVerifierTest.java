package com.bloxbean.cardano.client.scalus;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.VerifierException;
import com.bloxbean.cardano.client.scalus.bridge.SlotConfigBridge;
import com.bloxbean.cardano.client.transaction.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScalusLedgerRuleVerifierTest {

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
    void verify_shouldFailForEmptyInputs() {
        // Transaction with no inputs should fail EmptyInputsValidator
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

        UtxoSupplier utxoSupplier = createEmptyUtxoSupplier();
        ProtocolParamsSupplier ppSupplier = () -> protocolParams;

        ScalusLedgerRuleVerifier verifier = ScalusLedgerRuleVerifier.builder()
                .protocolParamsSupplier(ppSupplier)
                .utxoSupplier(utxoSupplier)
                .slotConfig(SlotConfigBridge.preview())
                .build();

        assertThatThrownBy(() -> verifier.verify(tx))
                .isInstanceOf(VerifierException.class);
    }

    @Test
    void verify_shouldFailForInsufficientFee() {
        // Transaction with fee = 0 should fail FeesOkValidator
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

        UtxoSupplier utxoSupplier = createUtxoSupplier(Map.of(txHash + "#0", inputUtxo));
        ProtocolParamsSupplier ppSupplier = () -> protocolParams;

        ScalusLedgerRuleVerifier verifier = ScalusLedgerRuleVerifier.builder()
                .protocolParamsSupplier(ppSupplier)
                .utxoSupplier(utxoSupplier)
                .slotConfig(SlotConfigBridge.preview())
                .build();

        assertThatThrownBy(() -> verifier.verify(tx))
                .isInstanceOf(VerifierException.class)
                .hasMessageContaining("Ledger rule validation failed");
    }


    private UtxoSupplier createEmptyUtxoSupplier() {
        return createUtxoSupplier(Map.of());
    }

    private UtxoSupplier createUtxoSupplier(Map<String, Utxo> utxoMap) {
        return new UtxoSupplier() {
            @Override
            public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, com.bloxbean.cardano.client.api.common.OrderEnum order) {
                return utxoMap.values().stream()
                        .filter(u -> u.getAddress().equals(address))
                        .toList();
            }

            @Override
            public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
                return Optional.ofNullable(utxoMap.get(txHash + "#" + outputIndex));
            }
        };
    }
}
