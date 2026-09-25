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

class TargetShapeStrategyTest {
    TargetShapeStrategy strategy = new TargetShapeStrategy();

    UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);

    @Nested
    class Lanes {

        @Test
        void emptyWallet_createsTargetNumberOfLanes() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of());
            Value change = Value.fromCoin(adaToLovelace(1000));

            List<Value> result = strategy.split(request(change, tx(), utxoSupplier));

            assertThat(result).extracting(Value::getCoin).containsExactly(
                    adaToLovelace(10), adaToLovelace(10), adaToLovelace(10), adaToLovelace(10), adaToLovelace(960));
            assertValid(change, result);
        }

        @Test
        void walletAlreadyAtTarget_singleOutput() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(adaUtxos(5, 10));
            Value change = Value.fromCoin(adaToLovelace(1000));

            assertThat(strategy.split(request(change, tx(), utxoSupplier))).containsExactly(change);
        }

        @Test
        void walletAboveTarget_singleOutput() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(adaUtxos(8, 50));
            Value change = Value.fromCoin(adaToLovelace(1000));

            assertThat(strategy.split(request(change, tx(), utxoSupplier))).containsExactly(change);
        }

        @Test
        void onlyMissingLanesAreCreated() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(adaUtxos(2, 20));
            Value change = Value.fromCoin(adaToLovelace(1000));

            List<Value> result = strategy.split(request(change, tx(), utxoSupplier));

            assertThat(result).extracting(Value::getCoin)
                    .containsExactly(adaToLovelace(10), adaToLovelace(10), adaToLovelace(980));
        }

        @Test
        void oneLaneMissing_changeItselfIsTheLane() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(adaUtxos(4, 20));
            Value change = Value.fromCoin(adaToLovelace(1000));

            assertThat(strategy.split(request(change, tx(), utxoSupplier))).containsExactly(change);
        }

        @Test
        void lanesLimitedByAvailableAda() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of());
            Value change = Value.fromCoin(adaToLovelace(25));

            List<Value> result = strategy.split(request(change, tx(), utxoSupplier));

            assertThat(result).extracting(Value::getCoin).containsExactly(adaToLovelace(10), adaToLovelace(15));
        }

        @Test
        void customLaneAmountAndTarget() {
            TargetShapeStrategy custom = TargetShapeStrategy.builder()
                    .targetLanes(3).laneAmount(adaToLovelace(100)).build();
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of());
            Value change = Value.fromCoin(adaToLovelace(1000));

            List<Value> result = custom.split(request(change, tx(), utxoSupplier));

            assertThat(result).extracting(Value::getCoin)
                    .containsExactly(adaToLovelace(100), adaToLovelace(100), adaToLovelace(800));
        }

        @Test
        void laneAmountBelowMinAda_raisedToMinAda() {
            TargetShapeStrategy tiny = TargetShapeStrategy.builder().targetLanes(3).laneAmount(BigInteger.ONE).build();
            given(utxoSupplier.getAll(ADDRESS)).willReturn(List.of());
            Value change = Value.fromCoin(adaToLovelace(100));

            List<Value> result = tiny.split(request(change, tx(), utxoSupplier));

            assertThat(result).hasSize(3);
            assertThat(result.subList(0, 2)).allSatisfy(v -> assertThat(v.getCoin()).isEqualTo(adaOnlyMinAda()));
            assertValid(change, result);
        }
    }

    @Nested
    class CountingExistingLanes {

        @Test
        void smallAdaUtxos_notCounted() {
            List<Utxo> utxos = new ArrayList<>(adaUtxos(5, 5)); // 5 ADA < 10 ADA lane
            given(utxoSupplier.getAll(ADDRESS)).willReturn(utxos);

            List<Value> result = strategy.split(request(Value.fromCoin(adaToLovelace(1000)), tx(), utxoSupplier));

            assertThat(result).hasSize(5);
        }

        @Test
        void tokenUtxos_notCounted() {
            List<Utxo> utxos = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                utxos.add(utxo("tokens", i, List.of(Amount.ada(100),
                        Amount.asset(POLICY_1 + "746f6b656e", BigInteger.ONE))));
            given(utxoSupplier.getAll(ADDRESS)).willReturn(utxos);

            List<Value> result = strategy.split(request(Value.fromCoin(adaToLovelace(1000)), tx(), utxoSupplier));

            assertThat(result).hasSize(5);
        }

        @Test
        void utxosWithDatumOrScriptRef_notCounted() {
            List<Utxo> utxos = new ArrayList<>(adaUtxos(3, 50));
            utxos.get(0).setInlineDatum("d87980");
            utxos.get(1).setDataHash("aa");
            utxos.get(2).setReferenceScriptHash("bb");
            given(utxoSupplier.getAll(ADDRESS)).willReturn(utxos);

            List<Value> result = strategy.split(request(Value.fromCoin(adaToLovelace(1000)), tx(), utxoSupplier));

            assertThat(result).hasSize(5);
        }

        @Test
        void utxosSpentByThisTransaction_notCounted() {
            List<Utxo> utxos = adaUtxos(5, 50);
            given(utxoSupplier.getAll(ADDRESS)).willReturn(utxos);
            Transaction tx = tx();
            tx.getBody().setInputs(List.of(
                    new TransactionInput(utxos.get(0).getTxHash(), utxos.get(0).getOutputIndex()),
                    new TransactionInput(utxos.get(1).getTxHash(), utxos.get(1).getOutputIndex())));

            List<Value> result = strategy.split(request(Value.fromCoin(adaToLovelace(1000)), tx, utxoSupplier));

            assertThat(result).hasSize(2);
        }

        @Test
        void noUtxoSupplier_noExistingLanes() {
            List<Value> result = strategy.split(request(Value.fromCoin(adaToLovelace(1000))));

            assertThat(result).hasSize(5);
        }

        @Test
        void supplierReturnsNull_noExistingLanes() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(null);

            List<Value> result = strategy.split(request(Value.fromCoin(adaToLovelace(1000)), tx(), utxoSupplier));

            assertThat(result).hasSize(5);
        }

        @Test
        void changeBelowMinAda_supplierNotQueried() {
            Value change = Value.fromCoin(BigInteger.valueOf(500_000));

            assertThat(strategy.split(request(change, tx(), utxoSupplier))).containsExactly(change);
            verifyNoInteractions(utxoSupplier);
        }
    }

    @Nested
    class WithTokens {

        @Test
        void tokensBundled_missingLanesFromRemainingAda() {
            given(utxoSupplier.getAll(ADDRESS)).willReturn(adaUtxos(3, 10));
            Value change = new Value(adaToLovelace(100), List.of(multiAsset(POLICY_1, 2, BigInteger.ONE)));

            List<Value> result = strategy.split(request(change, tx(), utxoSupplier));

            assertThat(withTokens(result)).hasSize(1);
            assertThat(adaOnly(result)).hasSize(2);
            assertValid(change, result);
        }
    }

    @Nested
    class Configuration {

        @Test
        void defaults() {
            assertThat(strategy.getTargetLanes()).isEqualTo(5);
            assertThat(strategy.getLaneAmount()).isEqualTo(adaToLovelace(10));
            assertThat(strategy.getTokenBundling()).isInstanceOf(ByteBudgetBundling.class);
        }

        @Test
        void targetLanesBelowOne_rejected() {
            assertThatThrownBy(() -> TargetShapeStrategy.builder().targetLanes(0).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void negativeLaneAmount_rejected() {
            assertThatThrownBy(() -> TargetShapeStrategy.builder().laneAmount(BigInteger.valueOf(-1)).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static List<Utxo> adaUtxos(int count, int ada) {
        List<Utxo> utxos = new ArrayList<>();
        for (int i = 0; i < count; i++)
            utxos.add(utxo("ada" + ada, i, List.of(Amount.ada(ada))));
        return utxos;
    }

    private static Utxo utxo(String seed, int index, List<Amount> amounts) {
        return Utxo.builder()
                .txHash(String.format("%064x", Math.abs(seed.hashCode())))
                .outputIndex(index)
                .address(ADDRESS)
                .amount(new ArrayList<>(amounts))
                .build();
    }
}
