package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdaShapeTest {
    UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);

    @Nested
    class Lanes {
        AdaShape lanes = AdaShape.lanes(5, Amount.ada(10));

        @Test
        void emptyWallet_createsTargetNumberOfLanes() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of());

            assertThat(split(lanes, adaToLovelace(1000), tx())).containsExactly(
                    adaToLovelace(10), adaToLovelace(10), adaToLovelace(10), adaToLovelace(10), adaToLovelace(960));
        }

        @Test
        void walletAlreadyAtTarget_singleAmount() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(adaUtxos(5, 10));

            assertThat(split(lanes, adaToLovelace(1000), tx())).containsExactly(adaToLovelace(1000));
        }

        @Test
        void walletAboveTarget_singleAmount() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(adaUtxos(8, 50));

            assertThat(split(lanes, adaToLovelace(1000), tx())).containsExactly(adaToLovelace(1000));
        }

        @Test
        void onlyMissingLanesAreCreated() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(adaUtxos(2, 20));

            assertThat(split(lanes, adaToLovelace(1000), tx()))
                    .containsExactly(adaToLovelace(10), adaToLovelace(10), adaToLovelace(980));
        }

        @Test
        void oneLaneMissing_changeItselfIsTheLane() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(adaUtxos(4, 20));

            assertThat(split(lanes, adaToLovelace(1000), tx())).containsExactly(adaToLovelace(1000));
        }

        @Test
        void lanesLimitedByAvailableAda() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of());

            assertThat(split(lanes, adaToLovelace(25), tx())).containsExactly(adaToLovelace(10), adaToLovelace(15));
        }

        @Test
        void belowOneLane_singleAmount() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of());

            assertThat(split(lanes, adaToLovelace(9), tx())).containsExactly(adaToLovelace(9));
        }

        @Test
        void laneSizeBelowMinAda_raisedToMinAda() {
            AdaShape tiny = new AdaShape.Lanes(3, BigInteger.ONE);
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of());

            List<BigInteger> result = split(tiny, adaToLovelace(100), tx());

            assertThat(result).hasSize(3);
            assertThat(result.subList(0, 2)).allSatisfy(a -> assertThat(a).isEqualTo(adaOnlyMinAda()));
        }

        @Test
        void smallAdaUtxos_notCountedAsLanes() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(adaUtxos(5, 5));

            assertThat(split(lanes, adaToLovelace(1000), tx())).hasSize(5);
        }

        @Test
        void tokenUtxos_notCountedAsLanes() {
            List<Utxo> utxos = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                utxos.add(utxo("tokens", i, List.of(Amount.ada(100), Amount.asset(POLICY_1 + "746f6b656e", BigInteger.ONE))));
            given(utxoSupplier.getAll(ADDRESS)).willReturn(utxos);

            assertThat(split(lanes, adaToLovelace(1000), tx())).hasSize(5);
        }

        @Test
        void utxosWithDatumOrScriptRef_notCountedAsLanes() {
            List<Utxo> utxos = new ArrayList<>(adaUtxos(3, 50));
            utxos.get(0).setInlineDatum("d87980");
            utxos.get(1).setDataHash("aa");
            utxos.get(2).setReferenceScriptHash("bb");
            given(utxoSupplier.getAll(ADDRESS)).willReturn(utxos);

            assertThat(split(lanes, adaToLovelace(1000), tx())).hasSize(5);
        }

        @Test
        void utxosSpentByThisTransaction_notCountedAsLanes() {
            List<Utxo> utxos = adaUtxos(5, 50);
            given(utxoSupplier.getAll(ADDRESS)).willReturn(utxos);
            Transaction tx = tx();
            tx.getBody().setInputs(List.of(
                    new TransactionInput(utxos.get(0).getTxHash(), utxos.get(0).getOutputIndex()),
                    new TransactionInput(utxos.get(1).getTxHash(), utxos.get(1).getOutputIndex())));

            assertThat(split(lanes, adaToLovelace(1000), tx)).hasSize(2);
        }

        @Test
        void noUtxoSupplier_singleAmount_noUnboundedGrowth() {
            ChangeSplitRequest request = request(Value.fromCoin(adaToLovelace(1000)), tx(), null);

            assertThat(lanes.split(adaToLovelace(1000), request)).containsExactly(adaToLovelace(1000));
        }

        @Test
        void supplierReturnsNull_noExistingLanes() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(null);

            assertThat(split(lanes, adaToLovelace(1000), tx())).hasSize(5);
        }

        @Test
        void invalidConfig_rejected() {
            assertThatThrownBy(() -> new AdaShape.Lanes(0, BigInteger.TEN)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AdaShape.Lanes(1, BigInteger.valueOf(-1))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AdaShape.Lanes(1, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void factory_convertsAmount() {
            assertThat(AdaShape.lanes(7, Amount.ada(60))).isEqualTo(new AdaShape.Lanes(7, adaToLovelace(60)));
        }

        @Test
        void factory_rejectsNonLovelaceAmount() {
            assertThatThrownBy(() -> AdaShape.lanes(3, Amount.asset(POLICY_1 + "746f6b656e", 5)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Percentages {
        AdaShape evolutionLike = AdaShape.percentages(Amount.ada(100), 50, 15, 10, 10, 5, 5, 5);

        @Test
        void belowThreshold_singleAmount() {
            assertThat(split(evolutionLike, adaToLovelace(99), tx())).containsExactly(adaToLovelace(99));
        }

        @Test
        void atThreshold_split() {
            assertThat(split(evolutionLike, adaToLovelace(100), tx())).containsExactly(
                    adaToLovelace(50), adaToLovelace(15), adaToLovelace(10), adaToLovelace(10),
                    adaToLovelace(5), adaToLovelace(5), adaToLovelace(5));
        }

        @Test
        void lastSliceGetsRoundingRemainder() {
            List<BigInteger> result = split(evolutionLike, BigInteger.valueOf(123_456_789L), tx());

            assertThat(result).hasSize(7);
            assertThat(result.stream().reduce(BigInteger.ZERO, BigInteger::add)).isEqualTo(BigInteger.valueOf(123_456_789L));
        }

        @Test
        void smallestSliceBelowMinAda_singleAmount() {
            AdaShape shape = AdaShape.percentages(Amount.ada(10), 95, 5);

            assertThat(split(shape, adaToLovelace(10), tx())).containsExactly(adaToLovelace(10));
        }

        @Test
        void doesNotReadTheWallet() {
            split(evolutionLike, adaToLovelace(1000), tx());

            verifyNoInteractions(utxoSupplier);
        }

        @Test
        void invalidConfig_rejected() {
            assertThatThrownBy(() -> AdaShape.percentages(Amount.ada(1), 60, 60)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AdaShape.percentages(Amount.ada(1))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AdaShape.Percentages(BigInteger.valueOf(-1), List.of(100)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void percentagesAreCopied() {
            List<Integer> mutable = new ArrayList<>(List.of(50, 50));
            AdaShape.Percentages shape = new AdaShape.Percentages(BigInteger.ONE, mutable);
            mutable.set(0, 10);

            assertThat(shape.percentages()).containsExactly(50, 50);
        }
    }

    @Nested
    class Single {

        @Test
        void neverSplits() {
            assertThat(split(AdaShape.single(), adaToLovelace(1_000_000), tx())).containsExactly(adaToLovelace(1_000_000));
            verifyNoInteractions(utxoSupplier);
        }
    }

    private List<BigInteger> split(AdaShape shape, BigInteger lovelace, Transaction tx) {
        return shape.split(lovelace, request(Value.fromCoin(lovelace), tx, utxoSupplier));
    }

    private static List<Utxo> adaUtxos(int count, int ada) {
        List<Utxo> utxos = new ArrayList<>();
        for (int i = 0; i < count; i++)
            utxos.add(utxo("ada" + ada, i, List.of(Amount.ada(ada))));
        return utxos;
    }

    static Utxo utxo(String seed, int index, List<Amount> amounts) {
        return Utxo.builder()
                .txHash(String.format("%064x", Math.abs(seed.hashCode())))
                .outputIndex(index)
                .address(ADDRESS)
                .amount(new ArrayList<>(amounts))
                .build();
    }
}
