package com.bloxbean.cardano.client.function.walletshape;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.function.walletshape.WalletShapeFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DefaultWalletShaperTest {
    UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
    TxBuilderContext context = new TxBuilderContext(utxoSupplier, PROTOCOL_PARAMS).mergeChange(false);
    DefaultWalletShaper shaper = new DefaultWalletShaper();

    @Nested
    class Shape {

        @Test
        void tokensBundledApartFromAda_adaInOneOutput_feeReserveOnAdaPieceInPlace() {
            TransactionOutput payment = new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(10)));
            Value changeValue = new Value(adaToLovelace(1002), List.of(
                    multiAsset(POLICY_1, 3, BigInteger.ONE),
                    multiAsset(POLICY_2, 1, BigInteger.valueOf(1000))));
            TransactionOutput metadataOutput = new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(2)));
            Transaction tx = tx(payment, new ChangeOutput(ADDRESS, changeValue), metadataOutput);

            shaper.apply(context, tx);

            List<TransactionOutput> outputs = tx.getBody().getOutputs();
            assertThat(outputs).hasSize(4);
            assertThat(outputs.get(0)).isSameAs(payment);
            assertThat(outputs.get(2)).isSameAs(metadataOutput);
            // ADA piece in place of the change, carrying the fee reserve
            assertThat(outputs.get(1)).isInstanceOf(ChangeOutput.class);
            assertThat(outputs.get(1).getValue().getMultiAssets()).isNullOrEmpty();
            // one bundle with both small policies, holding only its min-ada
            TransactionOutput bundle = outputs.get(3);
            assertThat(bundle).isInstanceOf(ChangeOutput.class);
            assertThat(bundle.getValue().getMultiAssets()).hasSize(2);
            assertThat(bundle.getValue().getCoin()).isEqualTo(minAda(bundle.getValue()));
            assertThat(outputs.get(1).getValue().getCoin())
                    .isEqualTo(adaToLovelace(1002).subtract(bundle.getValue().getCoin()));
            assertConserved(changeValue, List.of(outputs.get(1).getValue(), bundle.getValue()));
        }

        @Test
        void adaOnlyChange_untouched() {
            ChangeOutput change = new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(10_000)));
            Transaction tx = tx(change);

            shaper.apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }

        @Test
        void manyPolicies_bundledWithinByteBudget_eachBundleAtMinAda() {
            List<MultiAsset> tokens = new ArrayList<>();
            for (int i = 0; i < 120; i++)
                tokens.add(multiAsset(policy(i), 1, BigInteger.ONE));
            Value changeValue = new Value(adaToLovelace(502), tokens);
            Transaction tx = tx(new ChangeOutput(ADDRESS, changeValue));

            shaper.apply(context, tx);

            List<Value> pieces = tx.getBody().getOutputs().stream().map(TransactionOutput::getValue).toList();
            assertThat(adaOnly(pieces)).hasSize(1);
            assertThat(withTokens(pieces)).hasSizeBetween(5, 7)
                    .allSatisfy(b -> {
                        assertThat(ChangeValues.serializedSize(b.getMultiAssets())).isLessThanOrEqualTo(1000);
                        assertThat(b.getCoin()).isEqualTo(minAda(b));
                    });
            assertValid(changeValue, pieces);
        }

        @Test
        void largePolicy_chunkedIntoSeveralBundles() {
            Value changeValue = new Value(adaToLovelace(102), List.of(multiAsset(POLICY_1, 200, BigInteger.ONE)));
            Transaction tx = tx(new ChangeOutput(ADDRESS, changeValue));

            shaper.apply(context, tx);

            List<Value> pieces = tx.getBody().getOutputs().stream().map(TransactionOutput::getValue).toList();
            assertThat(withTokens(pieces)).hasSizeGreaterThan(1)
                    .allSatisfy(b -> assertThat(b.getMultiAssets()).singleElement());
            assertValid(changeValue, pieces);
        }

        @Test
        void remainderBelowAdaOnlyMinAda_addedToLastBundle() {
            DefaultWalletShaper.MinAda minAdaOf = new DefaultWalletShaper.MinAda(PROTOCOL_PARAMS);
            List<MultiAsset> big = new ArrayList<>();
            big.add(multiAsset(POLICY_1, 20, BigInteger.ONE));
            big.add(multiAsset(POLICY_2, 20, BigInteger.ONE));
            big.add(multiAsset(POLICY_3, 20, BigInteger.ONE));
            List<List<MultiAsset>> bundles = new ByteBudgetBundling().bundle(big);
            BigInteger bundlesMin = bundles.stream()
                    .map(b -> minAda(new Value(adaToLovelace(1), b)))
                    .reduce(BigInteger.ZERO, BigInteger::add);
            Value change = new Value(bundlesMin.add(BigInteger.valueOf(300_000)), big);

            List<Value> pieces = shaper.split(minAdaOf, ADDRESS, change);

            assertThat(adaOnly(pieces)).isEmpty();
            assertThat(pieces).hasSize(bundles.size());
            assertValid(change, pieces);
        }

        @Test
        void bundlesUnaffordable_untouched() {
            Value changeValue = new Value(adaToLovelace(3), List.of(multiAsset(POLICY_1, 60, BigInteger.ONE)));
            ChangeOutput change = new ChangeOutput(ADDRESS, changeValue);
            Transaction tx = tx(change);

            shaper.apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }

        @Test
        void singleBundle_smallRemainder_untouched() {
            BigInteger bundleMin = minAda(new Value(adaToLovelace(1), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            Value changeValue = new Value(bundleMin.add(adaToLovelace(2)).add(BigInteger.valueOf(300_000)),
                    List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)));
            ChangeOutput change = new ChangeOutput(ADDRESS, changeValue);
            Transaction tx = tx(change);

            shaper.apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }

        @Test
        void severalChangeOutputs_eachShaped() {
            Value first = new Value(adaToLovelace(50), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)));
            Value second = new Value(adaToLovelace(60), List.of(multiAsset(POLICY_2, 1, BigInteger.ONE)));
            Transaction tx = tx(new ChangeOutput(ADDRESS, first), new ChangeOutput(RECEIVER, second));

            shaper.apply(context, tx);

            List<TransactionOutput> outputs = tx.getBody().getOutputs();
            assertThat(outputs).hasSize(4);
            assertThat(outputs.stream().filter(o -> o.getAddress().equals(ADDRESS))).hasSize(2);
            assertThat(outputs.stream().filter(o -> o.getAddress().equals(RECEIVER))).hasSize(2);
        }

        @Test
        void doesNotReadTheWallet() {
            Value changeValue = new Value(adaToLovelace(100), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)));
            shaper.apply(context, tx(new ChangeOutput(ADDRESS, changeValue)));

            verifyNoInteractions(utxoSupplier);
        }
    }

    @Nested
    class Untouched {

        @Test
        void nonChangeOutputs_andChangeWithInlineDatum() {
            TransactionOutput payment = new TransactionOutput(ADDRESS,
                    new Value(adaToLovelace(100), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            ChangeOutput changeWithDatum = new ChangeOutput(ADDRESS,
                    new Value(adaToLovelace(100), List.of(multiAsset(POLICY_2, 1, BigInteger.ONE))));
            changeWithDatum.setInlineDatum(BigIntPlutusData.of(42));
            Transaction tx = tx(payment, changeWithDatum);

            shaper.apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(payment, changeWithDatum);
        }

        @Test
        void changeWithDatumHash() {
            ChangeOutput change = new ChangeOutput(ADDRESS,
                    new Value(adaToLovelace(100), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            change.setDatumHash(new byte[32]);
            Transaction tx = tx(change);

            shaper.apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }

        @Test
        void changeWithScriptRef() {
            ChangeOutput change = new ChangeOutput(ADDRESS,
                    new Value(adaToLovelace(100), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            change.setScriptRef(new byte[]{1, 2, 3});
            Transaction tx = tx(change);

            shaper.apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }

        @Test
        void negativeChange() {
            ChangeOutput change = new ChangeOutput(ADDRESS, Value.fromCoin(BigInteger.valueOf(-1_000_000)));
            Transaction tx = tx(change);

            shaper.apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }

        @Test
        void changeAtOrBelowFeeReserve() {
            ChangeOutput change = new ChangeOutput(ADDRESS,
                    new Value(adaToLovelace(2), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            Transaction tx = tx(change);

            shaper.apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }

        @Test
        void noChangeOutput_changeMergedAway_noException() {
            TransactionOutput merged = new TransactionOutput(ADDRESS,
                    new Value(adaToLovelace(100), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE))));
            Transaction tx = tx(merged);

            shaper.apply(new TxBuilderContext(utxoSupplier, PROTOCOL_PARAMS), tx); // mergeChange defaults to true

            assertThat(tx.getBody().getOutputs()).containsExactly(merged);
        }
    }

    @Nested
    class FeeReserve {

        @Test
        void customFeeReserve_addedToAdaPiece() {
            Value changeValue = new Value(adaToLovelace(105), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)));
            Transaction tx = tx(new ChangeOutput(ADDRESS, changeValue));

            new DefaultWalletShaper(adaToLovelace(5)).apply(context, tx);

            TransactionOutput adaPiece = tx.getBody().getOutputs().get(0);
            TransactionOutput bundle = tx.getBody().getOutputs().get(1);
            assertThat(adaPiece.getValue().getCoin()).isEqualTo(adaToLovelace(105).subtract(bundle.getValue().getCoin()));
        }

        @Test
        void zeroFeeReserve() {
            Value changeValue = new Value(adaToLovelace(100), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)));
            Transaction tx = tx(new ChangeOutput(ADDRESS, changeValue));

            new DefaultWalletShaper(BigInteger.ZERO).apply(context, tx);

            assertThat(tx.getBody().getOutputs()).hasSize(2);
        }

        @Test
        void negativeFeeReserve_rejected() {
            assertThatThrownBy(() -> new DefaultWalletShaper(BigInteger.valueOf(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DefaultWalletShaper.withConsolidation(BigInteger.valueOf(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullFeeReserve_rejected() {
            assertThatThrownBy(() -> new DefaultWalletShaper(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void defaults() {
            assertThat(shaper.getFeeReserve()).isEqualTo(adaToLovelace(2));
            assertThat(shaper.isConsolidating()).isFalse();
            assertThat(DefaultWalletShaper.withConsolidation().isConsolidating()).isTrue();
            assertThat(shaper).isInstanceOf(WalletShaper.class);
        }
    }

    @Nested
    class Consolidation {
        String spentHash = hash(1);
        DefaultWalletShaper consolidating = DefaultWalletShaper.withConsolidation();

        @Test
        void tokenFragmentsFirst_thenSmallestAda_upToThree() {
            Utxo spent = utxo(spentHash, Amount.ada(100));
            Utxo adaDust = utxo(hash(2), Amount.ada(1));
            Utxo largerAdaDust = utxo(hash(4), Amount.lovelace(BigInteger.valueOf(1_500_000)));
            Utxo tokenDust1 = utxo(hash(3), Amount.ada(2), Amount.asset(POLICY_1 + "746f6b656e", 5));
            Utxo tokenDust2 = utxo(hash(5), Amount.ada(2), Amount.asset(POLICY_2 + "746f6b656e", 7));
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of(spent, adaDust, largerAdaDust, tokenDust1, tokenDust2));
            Transaction tx = spending(spent, adaToLovelace(90));

            consolidating.apply(context, tx);

            assertThat(tx.getBody().getInputs()).extracting(TransactionInput::getTransactionId)
                    .containsExactly(spentHash, hash(3), hash(5), hash(2));
            Value sum = changeValueSum(tx);
            assertThat(sum.getCoin()).isEqualTo(adaToLovelace(90 + 2 + 2 + 1));
            assertThat(sum.amountOf(POLICY_1, "0x746f6b656e")).isEqualTo(BigInteger.valueOf(5));
            assertThat(sum.amountOf(POLICY_2, "0x746f6b656e")).isEqualTo(BigInteger.valueOf(7));
        }

        @Test
        void atMostThreeUtxosPerTransaction() {
            List<Utxo> utxos = new ArrayList<>(List.of(utxo(spentHash, Amount.ada(100))));
            for (int i = 0; i < 6; i++)
                utxos.add(utxo(hash(10 + i), Amount.ada(1)));
            given(utxoSupplier.getAll(ADDRESS)).willReturn(utxos);
            Transaction tx = spending(utxos.get(0), adaToLovelace(90));

            consolidating.apply(context, tx);

            assertThat(tx.getBody().getInputs()).hasSize(1 + 3);
        }

        @Test
        void mergedFragments_endUpInOneBundle_separateFromAda() {
            Utxo spent = utxo(spentHash, Amount.ada(100));
            Utxo tokenDust1 = utxo(hash(3), Amount.ada(2), Amount.asset(POLICY_1 + "746f6b656e", 5));
            Utxo tokenDust2 = utxo(hash(5), Amount.ada(2), Amount.asset(POLICY_2 + "746f6b656e", 7));
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of(spent, tokenDust1, tokenDust2));
            Transaction tx = spending(spent, adaToLovelace(90));

            consolidating.apply(context, tx);

            List<TransactionOutput> outputs = tx.getBody().getOutputs();
            assertThat(outputs).hasSize(3); // payment + 1 ADA output + 1 bundle
            assertThat(outputs).filteredOn(o -> o.getValue().getMultiAssets() != null && !o.getValue().getMultiAssets().isEmpty())
                    .singleElement()
                    .satisfies(o -> {
                        assertThat(o.getValue().getMultiAssets()).hasSize(2);
                        assertThat(o.getValue().getCoin()).isEqualTo(minAda(o.getValue()));
                    });
        }

        @Test
        void singleTokenFragment_notMerged_itWouldOnlyBeRebundled() {
            Utxo spent = utxo(spentHash, Amount.ada(100));
            Utxo tokenDust = utxo(hash(3), Amount.ada(2), Amount.asset(POLICY_1 + "746f6b656e", 5));
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of(spent, tokenDust));
            Transaction tx = spending(spent, adaToLovelace(90));

            consolidating.apply(context, tx);

            assertThat(tx.getBody().getInputs()).hasSize(1);
        }

        @Test
        void fullBundles_notMerged() {
            Utxo spent = utxo(spentHash, Amount.ada(100));
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of(spent, fullBundle(hash(3), 0), fullBundle(hash(5), 100)));
            Transaction tx = spending(spent, adaToLovelace(90));

            consolidating.apply(context, tx);

            assertThat(tx.getBody().getInputs()).hasSize(1);
        }

        @Test
        void skipsLargeUtxos_datumAndScriptRef() {
            Utxo spent = utxo(spentHash, Amount.ada(100));
            Utxo large = utxo(hash(2), Amount.ada(6));
            Utxo withDatum = utxo(hash(3), Amount.ada(1));
            withDatum.setInlineDatum("d87980");
            Utxo withDataHash = utxo(hash(4), Amount.ada(1));
            withDataHash.setDataHash("aa");
            Utxo withScriptRef = utxo(hash(5), Amount.ada(1));
            withScriptRef.setReferenceScriptHash("ab");
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of(spent, large, withDatum, withDataHash, withScriptRef));
            Transaction tx = spending(spent, adaToLovelace(90));

            consolidating.apply(context, tx);

            assertThat(tx.getBody().getInputs()).hasSize(1);
        }

        @Test
        void addressWithoutInputInTransaction_notConsolidated() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of(utxo(hash(9), Amount.ada(100)), utxo(hash(2), Amount.ada(1))));
            Transaction tx = spending(utxo(spentHash, Amount.ada(100)), adaToLovelace(90));

            consolidating.apply(context, tx);

            assertThat(tx.getBody().getInputs()).hasSize(1);
        }

        @Test
        void transactionWithRedeemers_notConsolidated() {
            Utxo spent = utxo(spentHash, Amount.ada(100));
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of(spent, utxo(hash(2), Amount.ada(1))));
            Transaction tx = spending(spent, adaToLovelace(90));
            tx.setWitnessSet(new TransactionWitnessSet());
            tx.getWitnessSet().setRedeemers(new ArrayList<>(List.of(Redeemer.builder()
                    .tag(RedeemerTag.Spend).index(BigInteger.ZERO).data(BigIntPlutusData.of(1))
                    .exUnits(ExUnits.builder().mem(BigInteger.ONE).steps(BigInteger.ONE).build()).build())));

            consolidating.apply(context, tx);

            assertThat(tx.getBody().getInputs()).hasSize(1);
        }

        @Test
        void noUtxoSupplier_notConsolidated() {
            Transaction tx = spending(utxo(spentHash, Amount.ada(100)), adaToLovelace(90));

            consolidating.apply(new TxBuilderContext(null, PROTOCOL_PARAMS).mergeChange(false), tx);

            assertThat(tx.getBody().getInputs()).hasSize(1);
        }

        @Test
        void consolidatedAdaDust_joinsTheAdaOutput() {
            Utxo spent = utxo(spentHash, Amount.ada(100));
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of(spent, utxo(hash(2), Amount.ada(1)), utxo(hash(3), Amount.ada(2))));
            Transaction tx = spending(spent, adaToLovelace(90));

            consolidating.apply(context, tx);

            assertThat(tx.getBody().getOutputs()).hasSize(2);
            assertThat(tx.getBody().getOutputs().get(1).getValue().getCoin()).isEqualTo(adaToLovelace(93));
        }

        private Transaction spending(Utxo input, BigInteger change) {
            Transaction tx = tx(new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(9))),
                    new ChangeOutput(ADDRESS, Value.fromCoin(change)));
            tx.getBody().setInputs(new ArrayList<>(List.of(new TransactionInput(input.getTxHash(), input.getOutputIndex()))));
            return tx;
        }

        private Value changeValueSum(Transaction tx) {
            return tx.getBody().getOutputs().stream()
                    .filter(o -> ADDRESS.equals(o.getAddress()))
                    .map(TransactionOutput::getValue)
                    .reduce(Value.fromCoin(BigInteger.ZERO), Value::add);
        }

        private Utxo fullBundle(String txHash, int policyOffset) {
            List<Amount> amounts = new ArrayList<>(List.of(Amount.ada(5)));
            for (int i = 0; i < 20; i++)
                amounts.add(Amount.asset(policy(policyOffset + i) + "746f6b656e", 1));
            return utxo(txHash, amounts.toArray(Amount[]::new));
        }

        private Utxo utxo(String txHash, Amount... amounts) {
            return Utxo.builder().txHash(txHash).outputIndex(0).address(ADDRESS)
                    .amount(new ArrayList<>(List.of(amounts))).build();
        }

        private String hash(int n) {
            return String.format("%064x", n);
        }
    }

    private static String hash(int n) {
        return String.format("%064x", n);
    }
}
