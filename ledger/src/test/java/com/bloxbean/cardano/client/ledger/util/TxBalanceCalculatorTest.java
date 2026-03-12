package com.bloxbean.cardano.client.ledger.util;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.ledger.slice.SimpleUtxoSlice;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class TxBalanceCalculatorTest {

    private static final String TX_HASH_1 = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
    private static final String TX_HASH_2 = "11223344556677889900aabbccddeeff00112233445566778899aabbccddeeff";
    private static final String POLICY_ID = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef01";
    private static final String TEST_ADDR = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
    private static final String STAKE_ADDR = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";

    @Test
    void consumed_sumsInputValues() {
        TransactionInput input1 = new TransactionInput(TX_HASH_1, 0);
        TransactionInput input2 = new TransactionInput(TX_HASH_2, 1);

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input1, outputWithCoin(3000000));
        utxoMap.put(input2, outputWithCoin(7000000));

        Transaction tx = txWithInputs(List.of(input1, input2));
        Value consumed = TxBalanceCalculator.consumed(
                new SimpleUtxoSlice(utxoMap), tx, defaultPP());

        assertThat(consumed).isNotNull();
        assertThat(consumed.getCoin()).isEqualTo(BigInteger.valueOf(10000000));
    }

    @Test
    void consumed_returnsNullForMissingInput() {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Transaction tx = txWithInputs(List.of(input));

        Value consumed = TxBalanceCalculator.consumed(
                new SimpleUtxoSlice(Collections.emptyMap()), tx, defaultPP());

        assertThat(consumed).isNull();
    }

    @Test
    void consumed_includesPositiveMint() {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(5000000));

        Asset mintedAsset = Asset.builder().name("token1").value(BigInteger.valueOf(100)).build();
        MultiAsset mint = MultiAsset.builder()
                .policyId(POLICY_ID)
                .assets(List.of(mintedAsset))
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .mint(List.of(mint))
                        .build())
                .build();

        Value consumed = TxBalanceCalculator.consumed(
                new SimpleUtxoSlice(utxoMap), tx, defaultPP());

        assertThat(consumed).isNotNull();
        assertThat(consumed.getCoin()).isEqualTo(BigInteger.valueOf(5000000));
        assertThat(consumed.getMultiAssets()).isNotEmpty();
    }

    @Test
    void consumed_excludesNegativeMint() {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(5000000));

        // Only burned tokens (negative)
        Asset burnedAsset = Asset.builder().name("token1").value(BigInteger.valueOf(-100)).build();
        MultiAsset mint = MultiAsset.builder()
                .policyId(POLICY_ID)
                .assets(List.of(burnedAsset))
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .mint(List.of(mint))
                        .build())
                .build();

        Value consumed = TxBalanceCalculator.consumed(
                new SimpleUtxoSlice(utxoMap), tx, defaultPP());

        assertThat(consumed).isNotNull();
        // No multi-assets on consumed side (burned tokens go to produced side)
        assertThat(consumed.getCoin()).isEqualTo(BigInteger.valueOf(5000000));
    }

    @Test
    void consumed_includesWithdrawals() {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(5000000));

        Withdrawal w = Withdrawal.builder()
                .rewardAddress(STAKE_ADDR)
                .coin(BigInteger.valueOf(1500000))
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .withdrawals(List.of(w))
                        .build())
                .build();

        Value consumed = TxBalanceCalculator.consumed(
                new SimpleUtxoSlice(utxoMap), tx, defaultPP());

        assertThat(consumed.getCoin()).isEqualTo(BigInteger.valueOf(6500000)); // 5M + 1.5M
    }

    @Test
    void produced_sumsOutputValues() {
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH_1, 0)))
                        .outputs(List.of(
                                outputWithCoin(3000000),
                                outputWithCoin(1500000)))
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        Value produced = TxBalanceCalculator.produced(tx, defaultPP());
        // outputs (4.5M) + fee (0.2M) = 4.7M
        assertThat(produced.getCoin()).isEqualTo(BigInteger.valueOf(4700000));
    }

    @Test
    void produced_includesBurnedTokens() {
        Asset burnedAsset = Asset.builder().name("token1").value(BigInteger.valueOf(-50)).build();
        MultiAsset mint = MultiAsset.builder()
                .policyId(POLICY_ID)
                .assets(List.of(burnedAsset))
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH_1, 0)))
                        .outputs(List.of(outputWithCoin(3000000)))
                        .fee(BigInteger.valueOf(200000))
                        .mint(List.of(mint))
                        .build())
                .build();

        Value produced = TxBalanceCalculator.produced(tx, defaultPP());

        // Burned tokens are sign-reversed on produced side
        assertThat(produced.getMultiAssets()).isNotEmpty();
        assertThat(produced.getMultiAssets().get(0).getAssets().get(0).getValue())
                .isEqualTo(BigInteger.valueOf(50)); // negated
    }

    @Test
    void produced_includesFee() {
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH_1, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(300000))
                        .build())
                .build();

        Value produced = TxBalanceCalculator.produced(tx, defaultPP());
        assertThat(produced.getCoin()).isEqualTo(BigInteger.valueOf(300000));
    }

    @Test
    void produced_includesTreasuryDonation() {
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH_1, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .donation(BigInteger.valueOf(5000000))
                        .build())
                .build();

        Value produced = TxBalanceCalculator.produced(tx, defaultPP());
        assertThat(produced.getCoin()).isEqualTo(BigInteger.valueOf(5200000)); // fee + donation
    }

    @Test
    void produced_includesProposalDeposits() {
        ProposalProcedure proposal = ProposalProcedure.builder()
                .deposit(BigInteger.valueOf(500000000))
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH_1, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .proposalProcedures(List.of(proposal))
                        .build())
                .build();

        Value produced = TxBalanceCalculator.produced(tx, defaultPP());
        assertThat(produced.getCoin()).isEqualTo(BigInteger.valueOf(500200000)); // fee + proposal deposit
    }

    // --- Certificate deposits and refunds ---

    @Test
    void computeTotalDeposits_stakeRegistration() {
        ProtocolParams pp = defaultPP();
        StakeRegistration cert = new StakeRegistration(StakeCredential.fromKeyHash(new byte[]{(byte)0xab, (byte)0xcd, (byte)0xef}));
        BigInteger deposits = TxBalanceCalculator.computeTotalDeposits(List.of(cert), pp);
        assertThat(deposits).isEqualTo(BigInteger.valueOf(2000000)); // keyDeposit
    }

    @Test
    void computeTotalDeposits_regCertWithExplicitDeposit() {
        ProtocolParams pp = defaultPP();
        RegCert cert = new RegCert(StakeCredential.fromKeyHash(new byte[]{(byte)0xab, (byte)0xcd, (byte)0xef}), BigInteger.valueOf(3000000));
        BigInteger deposits = TxBalanceCalculator.computeTotalDeposits(List.of(cert), pp);
        assertThat(deposits).isEqualTo(BigInteger.valueOf(3000000)); // explicit, not keyDeposit
    }

    @Test
    void computeTotalDeposits_poolRegistration() {
        ProtocolParams pp = defaultPP();
        PoolRegistration cert = PoolRegistration.builder().build();
        BigInteger deposits = TxBalanceCalculator.computeTotalDeposits(List.of(cert), pp);
        assertThat(deposits).isEqualTo(BigInteger.valueOf(500000000)); // poolDeposit
    }

    @Test
    void computeTotalDeposits_regDRepCert() {
        ProtocolParams pp = defaultPP();
        RegDRepCert cert = RegDRepCert.builder()
                .drepCredential(Credential.fromKey(new byte[]{(byte)0xab, (byte)0xcd, (byte)0xef}))
                .coin(BigInteger.valueOf(500000000))
                .build();
        BigInteger deposits = TxBalanceCalculator.computeTotalDeposits(List.of(cert), pp);
        assertThat(deposits).isEqualTo(BigInteger.valueOf(500000000));
    }

    @Test
    void computeTotalRefunds_stakeDeregistration() {
        ProtocolParams pp = defaultPP();
        StakeDeregistration cert = new StakeDeregistration(StakeCredential.fromKeyHash(new byte[]{(byte)0xab, (byte)0xcd, (byte)0xef}));
        BigInteger refunds = TxBalanceCalculator.computeTotalRefunds(List.of(cert), pp);
        assertThat(refunds).isEqualTo(BigInteger.valueOf(2000000)); // keyDeposit
    }

    @Test
    void computeTotalRefunds_unregCertWithExplicitRefund() {
        ProtocolParams pp = defaultPP();
        UnregCert cert = new UnregCert(StakeCredential.fromKeyHash(new byte[]{(byte)0xab, (byte)0xcd, (byte)0xef}), BigInteger.valueOf(3000000));
        BigInteger refunds = TxBalanceCalculator.computeTotalRefunds(List.of(cert), pp);
        assertThat(refunds).isEqualTo(BigInteger.valueOf(3000000));
    }

    @Test
    void computeTotalRefunds_unregDRepCert() {
        ProtocolParams pp = defaultPP();
        UnregDRepCert cert = UnregDRepCert.builder()
                .drepCredential(Credential.fromKey(new byte[]{(byte)0xab, (byte)0xcd, (byte)0xef}))
                .coin(BigInteger.valueOf(500000000))
                .build();
        BigInteger refunds = TxBalanceCalculator.computeTotalRefunds(List.of(cert), pp);
        assertThat(refunds).isEqualTo(BigInteger.valueOf(500000000));
    }

    @Test
    void computeTotalDeposits_nullCerts() {
        assertThat(TxBalanceCalculator.computeTotalDeposits(null, defaultPP())).isEqualTo(BigInteger.ZERO);
        assertThat(TxBalanceCalculator.computeTotalDeposits(List.of(), defaultPP())).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void computeTotalRefunds_nullCerts() {
        assertThat(TxBalanceCalculator.computeTotalRefunds(null, defaultPP())).isEqualTo(BigInteger.ZERO);
        assertThat(TxBalanceCalculator.computeTotalRefunds(List.of(), defaultPP())).isEqualTo(BigInteger.ZERO);
    }

    // --- Balanced tx test ---

    @Test
    void consumed_equals_produced_balancedTx() {
        TransactionInput input = new TransactionInput(TX_HASH_1, 0);
        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, outputWithCoin(10000000));

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of(outputWithCoin(9800000)))
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        ProtocolParams pp = defaultPP();
        Value consumed = TxBalanceCalculator.consumed(new SimpleUtxoSlice(utxoMap), tx, pp);
        Value produced = TxBalanceCalculator.produced(tx, pp);

        assertThat(consumed.getCoin()).isEqualTo(produced.getCoin());
    }

    // --- Helpers ---

    private ProtocolParams defaultPP() {
        ProtocolParams pp = new ProtocolParams();
        pp.setKeyDeposit("2000000");
        pp.setPoolDeposit("500000000");
        pp.setMinFeeA(44);
        pp.setMinFeeB(155381);
        return pp;
    }

    private TransactionOutput outputWithCoin(long lovelace) {
        return TransactionOutput.builder()
                .address(TEST_ADDR)
                .value(Value.builder().coin(BigInteger.valueOf(lovelace)).build())
                .build();
    }

    private Transaction txWithInputs(List<TransactionInput> inputs) {
        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(inputs)
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();
    }
}
