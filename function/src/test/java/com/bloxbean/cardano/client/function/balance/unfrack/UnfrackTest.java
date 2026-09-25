package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class UnfrackTest {
    UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
    TxBuilderContext context = new TxBuilderContext(utxoSupplier, PROTOCOL_PARAMS);

    @Nested
    class DefaultStrategy {

        @Test
        void defaults() {
            Unfrack unfrack = new Unfrack();

            assertThat(unfrack.getStrategy()).isInstanceOf(EvolutionStrategy.class);
            assertThat(unfrack.getFeeReserve()).isEqualTo(adaToLovelace(2));
        }

        @Test
        void changeOutput_splitInPlace_otherOutputsKeepIndexes() {
            TransactionOutput payment = new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(10)));
            Value changeValue = new Value(adaToLovelace(1002), List.of(
                    multiAsset(POLICY_1, 3, BigInteger.ONE),
                    multiAsset(POLICY_2, 1, BigInteger.valueOf(1000))));
            TransactionOutput metadataOutput = new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(2)));
            Transaction tx = tx(payment, new ChangeOutput(ADDRESS, changeValue), metadataOutput);

            new Unfrack().apply(context, tx);

            List<TransactionOutput> outputs = tx.getBody().getOutputs();
            assertThat(outputs).hasSize(3 + 8); // 2 bundles + 7 ada slices, one of them in place
            assertThat(outputs.get(0)).isSameAs(payment);
            assertThat(outputs.get(2)).isSameAs(metadataOutput);
            assertThat(outputs.get(1)).isInstanceOf(ChangeOutput.class);
            // appended right after the in-place piece: the two token bundles, each holding only its min ada
            assertThat(outputs.get(3).getValue().getMultiAssets()).isNotEmpty();
            assertThat(outputs.get(4).getValue().getMultiAssets()).isNotEmpty();
            // fee bearing piece: largest ada slice (50% of ada left after bundles) + 2 ADA fee reserve
            BigInteger remainingAda = adaToLovelace(1000)
                    .subtract(outputs.get(3).getValue().getCoin())
                    .subtract(outputs.get(4).getValue().getCoin());
            assertThat(outputs.get(1).getValue().getCoin())
                    .isEqualTo(remainingAda.multiply(BigInteger.valueOf(50)).divide(BigInteger.valueOf(100)).add(adaToLovelace(2)));
            assertThat(outputs.get(1).getValue().getMultiAssets()).isNullOrEmpty();
            assertThat(outputs.get(1).getValue().getCoin())
                    .isEqualTo(outputs.stream().map(o -> o.getValue().getCoin()).max(BigInteger::compareTo).orElseThrow());

            List<Value> changePieces = new ArrayList<>();
            changePieces.add(outputs.get(1).getValue());
            outputs.subList(3, outputs.size()).forEach(o -> {
                assertThat(o).isInstanceOf(ChangeOutput.class);
                assertThat(o.getAddress()).isEqualTo(ADDRESS);
                changePieces.add(o.getValue());
            });
            assertConserved(changeValue, changePieces);
        }

        @Test
        void smallChange_untouched() {
            ChangeOutput change = new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(50)));
            Transaction tx = tx(change);

            new Unfrack().apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }
    }

    @Nested
    class WhichOutputsAreSplit {

        @Test
        void nonChangeOutputs_andChangeWithInlineDatum_untouched() {
            TransactionOutput payment = new TransactionOutput(ADDRESS, Value.fromCoin(adaToLovelace(1000)));
            ChangeOutput changeWithDatum = new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(1000)));
            changeWithDatum.setInlineDatum(BigIntPlutusData.of(42));
            Transaction tx = tx(payment, changeWithDatum);

            new Unfrack().apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(payment, changeWithDatum);
        }

        @Test
        void changeWithDatumHash_untouched() {
            ChangeOutput change = new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(1000)));
            change.setDatumHash(new byte[32]);
            Transaction tx = tx(change);

            new Unfrack().apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }

        @Test
        void changeWithScriptRef_untouched() {
            ChangeOutput change = new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(1000)));
            change.setScriptRef(new byte[]{1, 2, 3});
            Transaction tx = tx(change);

            new Unfrack().apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }

        @Test
        void negativeChange_untouched() {
            ChangeOutput change = new ChangeOutput(ADDRESS, Value.fromCoin(BigInteger.valueOf(-1_000_000)));
            Transaction tx = tx(change);

            new Unfrack().apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }

        @Test
        void severalChangeOutputs_eachSplit_piecesAppendedInOrder() {
            String otherAddress = RECEIVER;
            ChangeOutput first = new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(102)));
            ChangeOutput second = new ChangeOutput(otherAddress, Value.fromCoin(adaToLovelace(202)));
            Transaction tx = tx(first, second);

            new Unfrack(new EqualLanesStrategy()).apply(context, tx);

            List<TransactionOutput> outputs = tx.getBody().getOutputs();
            assertThat(outputs).hasSize(10);
            assertThat(outputs.subList(2, 6)).allSatisfy(o -> assertThat(o.getAddress()).isEqualTo(ADDRESS));
            assertThat(outputs.subList(6, 10)).allSatisfy(o -> assertThat(o.getAddress()).isEqualTo(otherAddress));
            assertThat(outputs.get(0).getValue().getCoin()).isEqualTo(adaToLovelace(20 + 2));
            assertThat(outputs.get(1).getValue().getCoin()).isEqualTo(adaToLovelace(40 + 2));
        }
    }

    @Nested
    class PluggableStrategy {

        @Test
        void strategyReceivesChangeMinusReserve_transaction_andUtxoSupplier() {
            AtomicReference<ChangeSplitRequest> captured = new AtomicReference<>();
            ChangeSplitStrategy capturing = request -> {
                captured.set(request);
                return List.of(request.getChange());
            };
            ChangeOutput change = new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(100)));
            Transaction tx = tx(change);

            new Unfrack(capturing).apply(context, tx);

            ChangeSplitRequest request = captured.get();
            assertThat(request.getAddress()).isEqualTo(ADDRESS);
            assertThat(request.getChange().getCoin()).isEqualTo(adaToLovelace(98));
            assertThat(request.getTransaction()).isSameAs(tx);
            assertThat(request.getUtxoSupplier()).isSameAs(utxoSupplier);
            assertThat(request.getProtocolParams()).isSameAs(PROTOCOL_PARAMS);
        }

        @Test
        void customStrategyResultIsUsed_reserveAddedToLargestPiece() {
            ChangeSplitStrategy thirds = request -> List.of(
                    Value.fromCoin(adaToLovelace(10)), Value.fromCoin(adaToLovelace(70)), Value.fromCoin(adaToLovelace(20)));
            Transaction tx = tx(new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(102))));

            new Unfrack(thirds).apply(context, tx);

            assertThat(tx.getBody().getOutputs()).extracting(o -> o.getValue().getCoin())
                    .containsExactly(adaToLovelace(72), adaToLovelace(10), adaToLovelace(20));
        }

        @Test
        void customFeeReserve() {
            Transaction tx = tx(new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(505))));

            new Unfrack(new EqualLanesStrategy(), adaToLovelace(5)).apply(context, tx);

            assertThat(tx.getBody().getOutputs()).extracting(o -> o.getValue().getCoin()).containsExactly(
                    adaToLovelace(105), adaToLovelace(100), adaToLovelace(100), adaToLovelace(100), adaToLovelace(100));
        }

        @Test
        void zeroFeeReserve() {
            Transaction tx = tx(new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(500))));

            new Unfrack(new EqualLanesStrategy(), BigInteger.ZERO).apply(context, tx);

            assertThat(tx.getBody().getOutputs()).extracting(o -> o.getValue().getCoin())
                    .containsOnly(adaToLovelace(100)).hasSize(5);
        }

        @Test
        void changeAtOrBelowReserve_strategyNotCalled() {
            AtomicInteger calls = new AtomicInteger();
            ChangeSplitStrategy counting = request -> {
                calls.incrementAndGet();
                return List.of(request.getChange());
            };
            Transaction tx = tx(new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(2))));

            new Unfrack(counting).apply(context, tx);

            assertThat(calls).hasValue(0);
        }

        @Test
        void singlePiece_outputUntouched() {
            ChangeOutput change = new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(1000)));
            Transaction tx = tx(change);

            new Unfrack(request -> List.of(request.getChange())).apply(context, tx);

            assertThat(tx.getBody().getOutputs()).containsExactly(change);
        }
    }

    @Nested
    class StrategyResultVerification {

        @Test
        void piecesNotSummingToChange_rejected() {
            ChangeSplitStrategy lossy = request -> List.of(Value.fromCoin(adaToLovelace(10)), Value.fromCoin(adaToLovelace(10)));
            Transaction tx = tx(new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(100))));

            assertThatThrownBy(() -> new Unfrack(lossy).apply(context, tx))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("value mismatch");
        }

        @Test
        void droppedToken_rejected() {
            ChangeSplitStrategy dropsTokens = request -> List.of(
                    Value.fromCoin(adaToLovelace(50)), Value.fromCoin(request.getChange().getCoin().subtract(adaToLovelace(50))));
            Transaction tx = tx(new ChangeOutput(ADDRESS,
                    new Value(adaToLovelace(100), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)))));

            assertThatThrownBy(() -> new Unfrack(dropsTokens).apply(context, tx))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void pieceBelowMinAda_rejected() {
            ChangeSplitStrategy dust = request -> List.of(
                    Value.fromCoin(BigInteger.valueOf(100_000)),
                    Value.fromCoin(request.getChange().getCoin().subtract(BigInteger.valueOf(100_000))));
            Transaction tx = tx(new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(100))));

            assertThatThrownBy(() -> new Unfrack(dust).apply(context, tx))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("min-ada");
        }

        @Test
        void emptyResult_rejected() {
            Transaction tx = tx(new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(100))));

            assertThatThrownBy(() -> new Unfrack(request -> List.of()).apply(context, tx))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void nullResult_rejected() {
            Transaction tx = tx(new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(100))));

            assertThatThrownBy(() -> new Unfrack(request -> null).apply(context, tx))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Configuration {

        @Test
        void negativeFeeReserve_rejected() {
            assertThatThrownBy(() -> new Unfrack(new EvolutionStrategy(), BigInteger.valueOf(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullStrategy_rejected() {
            assertThatThrownBy(() -> new Unfrack(null, BigInteger.ZERO)).isInstanceOf(NullPointerException.class);
        }
    }
}
