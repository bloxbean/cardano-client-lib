package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.function.helper.OutputMergers;
import com.bloxbean.cardano.client.function.walletshape.DefaultWalletShaper;
import com.bloxbean.cardano.client.function.walletshape.WalletShaper;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.transaction.spec.AuxiliaryData;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuickTx with wallet shapers (ADR Part A), incl. the interaction with mergeOutputs (ADR §6.5).
 */
class QuickTxWalletShapeTest {
    static final String SENDER = "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";
    static final String RECEIVER = "addr_test1qzllzd3cxvz53k9gkq3n3mpcm6g7kv7rj5yvs88n7xwm3nmcs8dpnr85lclka6sycwccput39p0cffqegn8kkf6euzks6h9ldv";
    static final String POLICY_1 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8";
    static final String POLICY_2 = "b1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8";

    static final ProtocolParams PROTOCOL_PARAMS = ProtocolParams.builder()
            .minFeeA(44)
            .minFeeB(155381)
            .minUtxo("1000000")
            .coinsPerUtxoSize("4310")
            .maxTxSize(16384)
            .minFeeRefScriptCostPerByte(BigDecimal.valueOf(15))
            .build();

    final List<Utxo> wallet = new ArrayList<>(List.of(walletUtxo(hash(1), tokenWallet(1000))));
    final UtxoSupplier utxoSupplier = new InMemoryUtxoSupplier(wallet);
    final ProtocolParamsSupplier protocolParamsSupplier = () -> PROTOCOL_PARAMS;

    @Nested
    class DefaultShape {

        @Test
        void withoutShaper_singleChangeOutputWithAllTokens() {
            Transaction tx = builder().compose(payment()).build();

            assertThat(tx.getBody().getOutputs()).hasSize(2);
            assertThat(changeOutputs(tx)).singleElement()
                    .satisfies(o -> assertThat(o.getValue().getMultiAssets()).hasSize(2));
            assertBalanced(tx);
        }

        @Test
        void defaultShaper_tokensInOneBundle_adaInOneOutput() {
            Transaction tx = builder().compose(payment()).preBalanceTx(new DefaultWalletShaper()).build();

            List<TransactionOutput> outputs = tx.getBody().getOutputs();
            // payment + ADA output (fee bearing, in place of the change) + 1 bundle with both policies
            assertThat(outputs).hasSize(3);
            assertThat(outputs.get(0).getAddress()).isEqualTo(RECEIVER);
            assertThat(outputs.get(1).getValue().getMultiAssets()).isNullOrEmpty();
            assertThat(outputs.get(2).getValue().getMultiAssets()).hasSize(2);
            assertThat(outputs.get(2).getValue().getCoin()).isEqualTo(minAda(outputs.get(2)));
            assertAllMeetMinAda(tx);
            assertBalanced(tx);
        }

        @Test
        void withConsolidation_mergesDustAndTokenFragments() {
            wallet.add(walletUtxo(hash(2), List.of(Amount.ada(1))));
            wallet.add(walletUtxo(hash(3), List.of(Amount.ada(2), Amount.asset(POLICY_1 + hex("drop1"), 1))));
            wallet.add(walletUtxo(hash(4), List.of(Amount.ada(2), Amount.asset(POLICY_2 + hex("drop2"), 1))));

            Transaction tx = builder().compose(payment()).preBalanceTx(DefaultWalletShaper.withConsolidation()).build();

            assertThat(tx.getBody().getInputs()).extracting(TransactionInput::getTransactionId)
                    .contains(hash(1), hash(2), hash(3), hash(4));
            assertThat(changeOutputs(tx).stream().filter(o -> o.getValue().getMultiAssets() == null
                    || o.getValue().getMultiAssets().isEmpty())).hasSize(1);
            assertAllMeetMinAda(tx);
            assertBalanced(tx);
        }

        @Test
        void chainedPreBalanceTx_allApplied() {
            Transaction tx = builder().compose(payment())
                    .preBalanceTx((context, txn) -> txn.setAuxiliaryData(AuxiliaryData.builder()
                            .metadata(MetadataBuilder.createMetadata().put(BigInteger.ONE, "pre-balance"))
                            .build()))
                    .preBalanceTx(new DefaultWalletShaper())
                    .build();

            assertThat(tx.getAuxiliaryData()).isNotNull();
            assertThat(tx.getBody().getOutputs()).hasSize(3);
            assertBalanced(tx);
        }
    }

    @Nested
    class MergeOutputs {

        @Test
        void selfPayment_withShaper_changeStaysSeparateAndIsShaped() {
            Transaction tx = builder().compose(paymentAndSelfPayment())
                    .mergeOutputs(true)
                    .preBalanceTx(new DefaultWalletShaper())
                    .build();

            assertSelfPaymentKeptPlain_andChangeShaped(tx);
        }

        @Test
        void selfPayment_withShaper_configuredFirst_sameResult() {
            Transaction tx = builder().compose(paymentAndSelfPayment())
                    .preBalanceTx(new DefaultWalletShaper())
                    .mergeOutputs(true)
                    .build();

            assertSelfPaymentKeptPlain_andChangeShaped(tx);
        }

        @Test
        void withShaper_paymentsToTheSameRecipientAreStillMerged() {
            Transaction tx = builder().compose(new Tx()
                            .payToAddress(RECEIVER, Amount.ada(10))
                            .payToAddress(RECEIVER, Amount.ada(5))
                            .from(SENDER))
                    .mergeOutputs(true)
                    .preBalanceTx(new DefaultWalletShaper())
                    .build();

            assertThat(tx.getBody().getOutputs()).filteredOn(o -> o.getAddress().equals(RECEIVER))
                    .singleElement()
                    .satisfies(o -> assertThat(o.getValue().getCoin()).isEqualTo(adaToLovelace(15)));
            assertBalanced(tx);
        }

        @Test
        void otherPreBalanceTransformer_changeMergedAsToday() {
            Transaction tx = builder().compose(paymentAndSelfPayment())
                    .mergeOutputs(true)
                    .preBalanceTx((context, txn) -> txn.setAuxiliaryData(AuxiliaryData.builder()
                            .metadata(MetadataBuilder.createMetadata().put(BigInteger.ONE, "meta"))
                            .build()))
                    .build();

            // change merged into the payment to the sender, tokens mixed with ADA
            assertThat(tx.getBody().getOutputs()).hasSize(2);
            assertThat(changeOutputs(tx)).isEmpty();
            assertThat(tx.getBody().getOutputs()).filteredOn(o -> o.getAddress().equals(SENDER))
                    .singleElement()
                    .satisfies(o -> assertThat(o.getValue().getMultiAssets()).hasSize(2));
            assertBalanced(tx);
        }

        @Test
        void customWalletShaper_getsTheSameTreatment() {
            WalletShaper noOpShaper = (context, txn) -> {
            };

            Transaction tx = builder().compose(paymentAndSelfPayment())
                    .mergeOutputs(true)
                    .preBalanceTx(noOpShaper)
                    .build();

            assertThat(changeOutputs(tx)).hasSize(1);
            assertThat(tx.getBody().getOutputs()).filteredOn(o -> o.getAddress().equals(SENDER)).hasSize(2);
            assertBalanced(tx);
        }

        @Test
        void outputMergerAfterShaper_buildsButUndoesTheShape() {
            Transaction tx = builder().compose(payment())
                    .preBalanceTx(new DefaultWalletShaper())
                    .postBalanceTx(OutputMergers.mergeOutputsForAddress(SENDER))
                    .build();

            // a warning is logged; the merger runs as configured
            assertThat(tx.getBody().getOutputs()).filteredOn(o -> o.getAddress().equals(SENDER)).hasSize(1);
        }

        private void assertSelfPaymentKeptPlain_andChangeShaped(Transaction tx) {
            List<TransactionOutput> senderOutputs = tx.getBody().getOutputs().stream()
                    .filter(o -> o.getAddress().equals(SENDER)).toList();
            // plain 5 ADA self-payment + ADA change + token bundle
            assertThat(senderOutputs).hasSize(3);
            assertThat(senderOutputs).filteredOn(o -> !(o instanceof ChangeOutput))
                    .singleElement()
                    .satisfies(o -> {
                        assertThat(o.getValue().getCoin()).isEqualTo(adaToLovelace(5));
                        assertThat(o.getValue().getMultiAssets()).isNullOrEmpty();
                    });
            assertThat(changeOutputs(tx)).hasSize(2);
            assertAllMeetMinAda(tx);
            assertBalanced(tx);
        }
    }

    private QuickTxBuilder builder() {
        return new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null);
    }

    private static Tx payment() {
        return new Tx().payToAddress(RECEIVER, Amount.ada(10)).from(SENDER);
    }

    private static Tx paymentAndSelfPayment() {
        return new Tx().payToAddress(RECEIVER, Amount.ada(10)).payToAddress(SENDER, Amount.ada(5)).from(SENDER);
    }

    private static List<Amount> tokenWallet(long ada) {
        List<Amount> amounts = new ArrayList<>();
        amounts.add(Amount.ada(ada));
        for (int i = 0; i < 3; i++)
            amounts.add(Amount.asset(POLICY_1 + hex("nft" + i), BigInteger.ONE));
        amounts.add(Amount.asset(POLICY_2 + hex("token"), BigInteger.valueOf(5000)));
        return amounts;
    }

    private static Utxo walletUtxo(String txHash, List<Amount> amounts) {
        return Utxo.builder().address(SENDER).txHash(txHash).outputIndex(0).amount(new ArrayList<>(amounts)).build();
    }

    private static List<TransactionOutput> changeOutputs(Transaction tx) {
        return tx.getBody().getOutputs().stream().filter(ChangeOutput.class::isInstance).toList();
    }

    private static BigInteger minAda(TransactionOutput output) {
        return new MinAdaCalculator(PROTOCOL_PARAMS).calculateMinAda(output);
    }

    private static void assertAllMeetMinAda(Transaction tx) {
        assertThat(tx.getBody().getOutputs())
                .allSatisfy(o -> assertThat(o.getValue().getCoin()).isGreaterThanOrEqualTo(minAda(o)));
    }

    private void assertBalanced(Transaction tx) {
        Value in = Value.fromCoin(BigInteger.ZERO);
        for (TransactionInput input : tx.getBody().getInputs()) {
            Utxo utxo = wallet.stream()
                    .filter(u -> u.getTxHash().equals(input.getTransactionId()) && u.getOutputIndex() == input.getIndex())
                    .findFirst().orElseThrow();
            in = in.add(utxo.toValue());
        }
        Value out = tx.getBody().getOutputs().stream().map(TransactionOutput::getValue)
                .reduce(Value.fromCoin(tx.getBody().getFee()), Value::add);
        assertThat(out.getCoin()).isEqualTo(in.getCoin());
        assertThat(out.subtract(in).isZero()).isTrue();
    }

    private static String hash(int n) {
        return String.format("%064x", n);
    }

    private static String hex(String s) {
        return HexUtil.encodeHexString(s.getBytes());
    }

    /**
     * Returns all UTxOs of an address on the first page.
     */
    private record InMemoryUtxoSupplier(List<Utxo> utxos) implements UtxoSupplier {
        @Override
        public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
            if (page != null && page > 0)
                return List.of();
            return utxos.stream().filter(u -> u.getAddress().equals(address)).toList();
        }

        @Override
        public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
            return utxos.stream().filter(u -> u.getTxHash().equals(txHash) && u.getOutputIndex() == outputIndex).findFirst();
        }
    }
}
