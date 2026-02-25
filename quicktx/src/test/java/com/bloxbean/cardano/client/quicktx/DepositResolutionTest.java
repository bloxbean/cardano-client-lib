package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

/**
 * Tests for Phase 4 unified deposit resolution.
 * Covers all DepositMode options, mergeOutputs true/false, ChangeOutput detection,
 * explicit depositPayer, compose scenarios, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
public class DepositResolutionTest extends QuickTxBaseTest {

    @Mock
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    @Mock
    private TransactionProcessor transactionProcessor;

    Account account1 = new Account();
    Account account2 = new Account();
    Account account3 = new Account();

    String sender = account1.baseAddress();
    String receiver = account2.baseAddress();
    String treasury = account3.baseAddress();

    // Protocol param deposit amounts
    BigInteger KEY_DEPOSIT = new BigInteger("2000000");       // 2 ADA
    BigInteger DREP_DEPOSIT = new BigInteger("2000000");      // 2 ADA
    BigInteger POOL_DEPOSIT = new BigInteger("500000000");    // 500 ADA

    @BeforeEach
    @SneakyThrows
    public void setup() {
        MockitoAnnotations.openMocks(this);
        protocolParamJsonFile = "protocol-params.json";
        ProtocolParams protocolParams = (ProtocolParams) loadObjectFromJson("protocol-parameters", ProtocolParams.class);
        protocolParamsSupplier = () -> protocolParams;
    }

    private void mockUtxos(String address, BigInteger... amounts) {
        List<Utxo> utxos = new java.util.ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            utxos.add(Utxo.builder()
                    .address(address)
                    .txHash(generateRandomHexValue(64))
                    .outputIndex(0)
                    .amount(List.of(Amount.lovelace(amounts[i])))
                    .build());
        }
        // Return UTXOs only for page 0; empty for subsequent pages to avoid infinite pagination
        given(utxoSupplier.getPage(eq(address), anyInt(), any(), any())).willAnswer(invocation -> {
            int page = invocation.getArgument(2);
            return page == 0 ? utxos : java.util.Collections.emptyList();
        });
    }

    // ========== AUTO Mode + mergeOutputs=false ==========

    @Test
    void autoMode_mergeOff_stakeRegistration_deductsFromChangeOutput() {
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .registerStakeAddress(sender)
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(false)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(StakeRegistration.class);

        // Balance: outputs + fee + deposit = inputs
        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(KEY_DEPOSIT))
                .isEqualTo(adaToLovelace(50));

        // Receiver's 10 ADA output is preserved
        TransactionOutput receiverOutput = transaction.getBody().getOutputs().stream()
                .filter(o -> o.getAddress().equals(receiver))
                .findFirst().orElseThrow();
        assertThat(receiverOutput.getValue().getCoin()).isEqualTo(adaToLovelace(10));
    }

    @Test
    void autoMode_mergeOff_preservesUserPayToAddress_atSameAddress() {
        // User pays 5 ADA to themselves + stake registration deposit
        // Phase 4 should deduct from ChangeOutput, NOT from user's 5 ADA output
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .payToAddress(sender, Amount.ada(5))
                .registerStakeAddress(sender)
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(false)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);

        // Should have 2 outputs: user's 5 ADA + change output
        assertThat(transaction.getBody().getOutputs()).hasSize(2);

        // Find user's output (TransactionOutput, not ChangeOutput)
        TransactionOutput userOutput = transaction.getBody().getOutputs().stream()
                .filter(o -> o.getAddress().equals(sender) && !(o instanceof ChangeOutput))
                .findFirst().orElseThrow();
        assertThat(userOutput.getValue().getCoin()).isEqualTo(adaToLovelace(5));

        // Balance check
        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(KEY_DEPOSIT))
                .isEqualTo(adaToLovelace(50));
    }

    @Test
    void autoMode_mergeOff_drepRegistration_deductsFromChangeOutput() {
        mockUtxos(sender, adaToLovelace(50));

        var anchor = new Anchor("https://test.com/test.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .registerDRep(account1, anchor)
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(false)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(RegDRepCert.class);

        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(DREP_DEPOSIT))
                .isEqualTo(adaToLovelace(50));
    }

    @Test
    void autoMode_mergeOff_multipleDeposits_stakeAndDrep() {
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .registerStakeAddress(sender)
                .registerDRep(account1)
                .payToAddress(receiver, Amount.ada(5))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(false)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(2);
        BigInteger totalDeposits = KEY_DEPOSIT.add(DREP_DEPOSIT); // 4 ADA

        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(totalDeposits))
                .isEqualTo(adaToLovelace(50));
    }

    @Test
    void autoMode_mergeOff_poolRegistration_deductsLargeDeposit() {
        mockUtxos(sender, adaToLovelace(1000));

        String regCbor = "8a03581ced40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a45820b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b1b00000600aea7d0001a1dcd6500d81e820d1903e8581de1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a35613481581cf3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134838400190bb94436b12923f68400190bb944037dfcb6f68400190bb944343fe1bef6827468747470733a2f2f6769742e696f2f4a7474546c582051700f7e33476a20b6e5a3f681e31d2cf0d8e706393f45912a5dbe3a8d7edd41";
        PoolRegistration poolReg;
        try {
            poolReg = PoolRegistration.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(regCbor)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Tx tx = new Tx()
                .registerPool(poolReg)
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(false)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(PoolRegistration.class);

        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(POOL_DEPOSIT))
                .isEqualTo(adaToLovelace(1000));
    }

    // ========== AUTO Mode + mergeOutputs=true (default) ==========

    @Test
    void autoMode_mergeOn_stakeRegistration_withPayment() {
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .registerStakeAddress(sender)
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        // mergeOutputs=true is default
        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(StakeRegistration.class);

        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(KEY_DEPOSIT))
                .isEqualTo(adaToLovelace(50));
    }

    @Test
    void autoMode_mergeOn_stakeRegistration_onlyDeposit_noPayments() {
        // Only a stake registration, no payToAddress at all
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .registerStakeAddress(sender)
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);

        // Phase 4 should use UTXO selection since no outputs exist
        assertThat(transaction.getBody().getInputs()).isNotEmpty();
        assertThat(transaction.getBody().getOutputs()).isNotEmpty();

        BigInteger totalInputValue = adaToLovelace(50);
        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(KEY_DEPOSIT))
                .isEqualTo(totalInputValue);
    }

    @Test
    void autoMode_mergeOn_payToSender_deductsFromMergedOutput() {
        // mergeOutputs=true: change merged into user's output at sender address
        // Phase 4 AUTO: anyOutput → succeeds (deducts from merged output)
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .payToAddress(sender, Amount.ada(5))
                .registerStakeAddress(sender)
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(true)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);

        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(KEY_DEPOSIT))
                .isEqualTo(adaToLovelace(50));
    }

    // ========== Explicit DepositMode ==========

    @Test
    void depositMode_changeOutput_deductsOnlyFromChangeOutput() {
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .registerStakeAddress(sender)
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(false)
                .depositMode(DepositMode.CHANGE_OUTPUT)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);

        // Receiver output should be preserved
        TransactionOutput receiverOut = transaction.getBody().getOutputs().stream()
                .filter(o -> o.getAddress().equals(receiver))
                .findFirst().orElseThrow();
        assertThat(receiverOut.getValue().getCoin()).isEqualTo(adaToLovelace(10));

        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(KEY_DEPOSIT))
                .isEqualTo(adaToLovelace(50));
    }

    @Test
    void depositMode_anyOutput_deductsFromAnyOutput() {
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .registerStakeAddress(sender)
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(false)
                .depositMode(DepositMode.ANY_OUTPUT)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);

        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(KEY_DEPOSIT))
                .isEqualTo(adaToLovelace(50));
    }

    @Test
    void depositMode_newUtxoSelection_selectsNewUtxos() {
        mockUtxos(sender, adaToLovelace(20), adaToLovelace(30));

        Tx tx = new Tx()
                .registerStakeAddress(sender)
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(false)
                .depositMode(DepositMode.NEW_UTXO_SELECTION)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);
        assertThat(transaction.getBody().getInputs().size()).isGreaterThanOrEqualTo(1);

        BigInteger totalInputValue = adaToLovelace(50); // Both UTXOs
        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(KEY_DEPOSIT))
                .isLessThanOrEqualTo(totalInputValue);
    }

    // ========== Explicit Deposit Payer ==========

    @Test
    void explicitDepositPayer_differentFromSender() {
        mockUtxos(sender, adaToLovelace(20));
        mockUtxos(treasury, adaToLovelace(50));

        Tx tx = new Tx()
                .registerStakeAddress(sender)
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(false)
                .depositPayer(treasury)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);

        // Should have inputs from both sender (for payment) and treasury (for deposit)
        assertThat(transaction.getBody().getInputs().size()).isGreaterThanOrEqualTo(2);

        // Treasury change output should exist
        boolean hasTreasuryOutput = transaction.getBody().getOutputs().stream()
                .anyMatch(o -> o.getAddress().equals(treasury));
        assertThat(hasTreasuryOutput).isTrue();
    }

    // ========== Compose Scenarios ==========

    @Test
    void compose_depositFromOtherTxChange() {
        // Tx1: payment from sender, creates change output at sender
        // Tx2: stake registration from treasury with depositPayer=sender
        mockUtxos(sender, adaToLovelace(50));

        Tx paymentTx = new Tx()
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        Tx stakeTx = new Tx()
                .registerStakeAddress(sender)
                .from(treasury);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(paymentTx, stakeTx)
                .mergeOutputs(false)
                .depositPayer(sender)
                .feePayer(sender)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(StakeRegistration.class);

        // Receiver gets exactly 10 ADA
        TransactionOutput receiverOut = transaction.getBody().getOutputs().stream()
                .filter(o -> o.getAddress().equals(receiver))
                .findFirst().orElseThrow();
        assertThat(receiverOut.getValue().getCoin()).isEqualTo(adaToLovelace(10));
    }

    // Note: compose with different senders + fee balancing requires real UTXO pagination
    // and is better tested in integration tests with Yaci DevKit.

    // ========== Pool Update (no deposit) ==========

    @Test
    void poolUpdate_noDepositDeducted() {
        mockUtxos(sender, adaToLovelace(50));

        String regCbor = "8a03581ced40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a45820b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b1b00000600aea7d0001a1dcd6500d81e820d1903e8581de1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a35613481581cf3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134838400190bb94436b12923f68400190bb944037dfcb6f68400190bb944343fe1bef6827468747470733a2f2f6769742e696f2f4a7474546c582051700f7e33476a20b6e5a3f681e31d2cf0d8e706393f45912a5dbe3a8d7edd41";
        PoolRegistration poolReg;
        try {
            poolReg = PoolRegistration.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(regCbor)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Tx tx = new Tx()
                .updatePool(poolReg)
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);

        // Pool update = no deposit, so outputs + fee = inputs
        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()))
                .isEqualTo(adaToLovelace(50));
    }

    // ========== ChangeOutput Marker Verification ==========

    @Test
    void changeOutput_isInstanceOfTransactionOutput() {
        ChangeOutput co = new ChangeOutput("addr_test1...",
                com.bloxbean.cardano.client.transaction.spec.Value.builder()
                        .coin(BigInteger.TEN).build());
        assertThat(co).isInstanceOf(TransactionOutput.class);
        assertThat(co).isInstanceOf(ChangeOutput.class);
    }

    @Test
    void regularTransactionOutput_isNotChangeOutput() {
        TransactionOutput to = new TransactionOutput("addr_test1...",
                com.bloxbean.cardano.client.transaction.spec.Value.builder()
                        .coin(BigInteger.TEN).build());
        assertThat(to).isInstanceOf(TransactionOutput.class);
        assertThat(to).isNotInstanceOf(ChangeOutput.class);
    }

    @Test
    void mergeOutputsFalse_producesChangeOutput() {
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .mergeOutputs(false)
                .build();

        // Should have receiver output (TransactionOutput) and change (ChangeOutput)
        long changeOutputCount = transaction.getBody().getOutputs().stream()
                .filter(o -> o instanceof ChangeOutput)
                .count();
        assertThat(changeOutputCount).isGreaterThanOrEqualTo(1);
    }

    // ========== Edge Cases ==========

    @Test
    void noDepositIntents_phase4Skipped() {
        mockUtxos(sender, adaToLovelace(10));

        Tx tx = new Tx()
                .payToAddress(receiver, Amount.ada(5))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .build();

        assertThat(transaction.getBody().getCerts()).isNullOrEmpty();

        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()))
                .isEqualTo(adaToLovelace(10));
    }

    @Test
    void stakeRegistration_withRegularPayments() {
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .registerStakeAddress(receiver)
                .payToAddress(receiver, Amount.ada(2))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);
        assertThat(transaction.getBody().getCerts().get(0)).isInstanceOf(StakeRegistration.class);

        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(KEY_DEPOSIT))
                .isEqualTo(adaToLovelace(50));
    }

    @Test
    void multipleStakeRegistrations() {
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .registerStakeAddress(sender)
                .registerStakeAddress(receiver)
                .payToAddress(receiver, Amount.ada(3))
                .from(sender);

        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(2);

        BigInteger totalDeposits = KEY_DEPOSIT.multiply(BigInteger.TWO);
        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(totalDeposits))
                .isEqualTo(adaToLovelace(50));
    }

    @Test
    void depositModeDefault_isAuto() {
        mockUtxos(sender, adaToLovelace(50));

        Tx tx = new Tx()
                .registerStakeAddress(sender)
                .payToAddress(receiver, Amount.ada(10))
                .from(sender);

        // No depositMode set — should default to AUTO
        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor)
                .compose(tx)
                .build();

        assertThat(transaction.getBody().getCerts()).hasSize(1);

        BigInteger totalOutputs = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(totalOutputs.add(transaction.getBody().getFee()).add(KEY_DEPOSIT))
                .isEqualTo(adaToLovelace(50));
    }
}
