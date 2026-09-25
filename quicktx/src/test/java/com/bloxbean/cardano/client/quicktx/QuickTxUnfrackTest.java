package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.function.balance.unfrack.EqualLanesStrategy;
import com.bloxbean.cardano.client.function.balance.unfrack.Unfrack;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.transaction.spec.AuxiliaryData;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuickTxUnfrackTest {
    static final String SENDER = "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";
    static final String RECEIVER = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
    static final String POLICY_1 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8";
    static final String POLICY_2 = "b1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8";

    @Mock
    UtxoSupplier utxoSupplier;

    @Mock
    ProtocolParamsSupplier protocolParamsSupplier;

    ProtocolParams protocolParams = ProtocolParams.builder()
            .minFeeA(44)
            .minFeeB(155381)
            .minUtxo("1000000")
            .coinsPerUtxoSize("4310")
            .maxTxSize(16384)
            .minFeeRefScriptCostPerByte(BigDecimal.valueOf(15))
            .build();

    @BeforeEach
    void setup() {
        List<Amount> amounts = new ArrayList<>();
        amounts.add(Amount.ada(1000));
        for (int i = 0; i < 3; i++)
            amounts.add(Amount.asset(POLICY_1 + hex("nft" + i), BigInteger.ONE));
        amounts.add(Amount.asset(POLICY_2 + hex("token"), BigInteger.valueOf(5000)));

        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any()))
                .willReturn(List.of(Utxo.builder()
                        .address(SENDER)
                        .txHash("7e1eecf7439fb5119a6762985a61c9fb3ca8158d9fc38361f0c4746430d5e0c7")
                        .outputIndex(0)
                        .amount(amounts)
                        .build()), List.of());
        given(protocolParamsSupplier.getProtocolParams()).willReturn(protocolParams);
    }

    @Test
    void withoutUnfrack_singleChangeOutput() {
        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .compose(payment())
                .build();

        assertThat(transaction.getBody().getOutputs()).hasSize(2);
        assertBalanced(transaction);
    }

    @Test
    void withUnfrack_changeSplitIntoBundlesAndAdaSlices() {
        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .compose(payment())
                .preBalanceTx(new Unfrack())
                .build();

        List<TransactionOutput> outputs = transaction.getBody().getOutputs();
        // payment + 2 token bundles (one per policy) + 7 ada slices
        assertThat(outputs).hasSize(1 + 2 + 7);
        assertThat(outputs.get(0).getAddress()).isEqualTo(RECEIVER);
        assertThat(outputs.subList(1, outputs.size())).allSatisfy(o -> assertThat(o.getAddress()).isEqualTo(SENDER));

        List<TransactionOutput> tokenOutputs = outputs.stream()
                .filter(o -> o.getValue().getMultiAssets() != null && !o.getValue().getMultiAssets().isEmpty())
                .toList();
        assertThat(tokenOutputs).hasSize(2).allSatisfy(o -> assertThat(o.getValue().getMultiAssets()).hasSize(1));

        MinAdaCalculator minAdaCalculator = new MinAdaCalculator(protocolParams);
        assertThat(outputs).allSatisfy(o ->
                assertThat(o.getValue().getCoin()).isGreaterThanOrEqualTo(minAdaCalculator.calculateMinAda(o)));
        assertBalanced(transaction);
    }

    @Test
    void withEqualLanesStrategy_tokensInOneBundleAndEqualAdaLanes() {
        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .compose(payment())
                .preBalanceTx(new Unfrack(EqualLanesStrategy.builder().lanes(4).build()))
                .build();

        List<TransactionOutput> outputs = transaction.getBody().getOutputs();
        // payment + 1 token bundle (both policies fit the byte budget) + 4 ada lanes
        assertThat(outputs).hasSize(1 + 1 + 4);
        List<TransactionOutput> adaLanes = outputs.stream()
                .filter(o -> o.getValue().getMultiAssets() == null || o.getValue().getMultiAssets().isEmpty())
                .filter(o -> o.getAddress().equals(SENDER))
                .toList();
        assertThat(adaLanes).hasSize(4);
        // lanes are equal except the fee bearing one, which got the fee reserve minus the fee
        BigInteger lane = adaLanes.get(1).getValue().getCoin();
        assertThat(adaLanes.subList(1, 4)).allSatisfy(o -> assertThat(o.getValue().getCoin()).isBetween(lane, lane.add(BigInteger.TEN)));
        assertThat(adaLanes.get(0).getValue().getCoin())
                .isEqualTo(lane.add(Unfrack.DEFAULT_FEE_RESERVE).subtract(transaction.getBody().getFee()));
        assertBalanced(transaction);
    }

    @Test
    void multiplePreBalanceTx_allApplied() {
        Transaction transaction = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .compose(payment())
                .preBalanceTx((context, txn) -> txn.setAuxiliaryData(AuxiliaryData.builder()
                        .metadata(MetadataBuilder.createMetadata().put(BigInteger.ONE, "pre-balance"))
                        .build()))
                .preBalanceTx(new Unfrack())
                .build();

        assertThat(transaction.getAuxiliaryData()).isNotNull();
        assertThat(transaction.getBody().getOutputs()).hasSize(1 + 2 + 7);
        assertBalanced(transaction);
    }

    private Tx payment() {
        return new Tx()
                .payToAddress(RECEIVER, Amount.ada(10))
                .from(SENDER);
    }

    private static void assertBalanced(Transaction transaction) {
        BigInteger totalOut = transaction.getBody().getOutputs().stream()
                .map(o -> o.getValue().getCoin())
                .reduce(BigInteger.ZERO, BigInteger::add)
                .add(transaction.getBody().getFee());
        assertThat(totalOut).isEqualTo(adaToLovelace(1000));

        BigInteger nfts = BigInteger.ZERO;
        BigInteger tokens = BigInteger.ZERO;
        for (TransactionOutput o : transaction.getBody().getOutputs()) {
            for (int i = 0; i < 3; i++)
                nfts = nfts.add(o.getValue().amountOf(POLICY_1, "0x" + hex("nft" + i)));
            tokens = tokens.add(o.getValue().amountOf(POLICY_2, "0x" + hex("token")));
        }
        assertThat(nfts).isEqualTo(BigInteger.valueOf(3));
        assertThat(tokens).isEqualTo(BigInteger.valueOf(5000));
    }

    private static String hex(String s) {
        return HexUtil.encodeHexString(s.getBytes());
    }
}
