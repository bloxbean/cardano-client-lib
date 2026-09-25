package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentSizedStrategyTest {
    PaymentSizedStrategy strategy = new PaymentSizedStrategy();

    @Nested
    class AdaOnly {

        @Test
        void onePayment_onePieceOfPaymentSizePlusRemainder() {
            Value change = Value.fromCoin(adaToLovelace(990));

            List<Value> result = strategy.split(request(change, payments(10), null));

            assertThat(result).extracting(Value::getCoin).containsExactly(adaToLovelace(10), adaToLovelace(980));
        }

        @Test
        void severalPayments_largestFirst() {
            Value change = Value.fromCoin(adaToLovelace(1000));

            List<Value> result = strategy.split(request(change, payments(5, 50, 20), null));

            assertThat(result).extracting(Value::getCoin).containsExactly(
                    adaToLovelace(50), adaToLovelace(20), adaToLovelace(5), adaToLovelace(925));
        }

        @Test
        void maxPieces_capsNumberOfPiecesIncludingRemainder() {
            PaymentSizedStrategy capped = PaymentSizedStrategy.builder().maxPieces(3).build();
            Value change = Value.fromCoin(adaToLovelace(1000));

            List<Value> result = capped.split(request(change, payments(10, 20, 30, 40), null));

            assertThat(result).extracting(Value::getCoin)
                    .containsExactly(adaToLovelace(40), adaToLovelace(30), adaToLovelace(930));
        }

        @Test
        void maxPiecesOne_neverSplits() {
            PaymentSizedStrategy one = PaymentSizedStrategy.builder().maxPieces(1).build();
            Value change = Value.fromCoin(adaToLovelace(1000));

            assertThat(one.split(request(change, payments(10), null))).containsExactly(change);
        }

        @Test
        void paymentBelowMinAda_skipped() {
            Value change = Value.fromCoin(adaToLovelace(100));
            Transaction tx = tx(new TransactionOutput(RECEIVER, Value.fromCoin(BigInteger.valueOf(500_000))),
                    new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(7))));

            List<Value> result = strategy.split(request(change, tx, null));

            assertThat(result).extracting(Value::getCoin).containsExactly(adaToLovelace(7), adaToLovelace(93));
        }

        @Test
        void paymentLeavingRemainderBelowMinAda_skipped_smallerOneStillUsed() {
            Value change = Value.fromCoin(adaToLovelace(50));

            List<Value> result = strategy.split(request(change, payments(49.5, 20), null));

            assertThat(result).extracting(Value::getCoin).containsExactly(adaToLovelace(20), adaToLovelace(30));
        }

        @Test
        void paymentEqualToChange_notUsed() {
            Value change = Value.fromCoin(adaToLovelace(10));

            assertThat(strategy.split(request(change, payments(10), null))).containsExactly(change);
        }

        @Test
        void changeOutputsAreNotPayments() {
            Value change = Value.fromCoin(adaToLovelace(100));
            Transaction tx = tx(new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(30))),
                    new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(10))));

            List<Value> result = strategy.split(request(change, tx, null));

            assertThat(result).extracting(Value::getCoin).containsExactly(adaToLovelace(10), adaToLovelace(90));
        }

        @Test
        void paymentToOwnAddressCountsAsPayment() {
            Value change = Value.fromCoin(adaToLovelace(100));
            Transaction tx = tx(new TransactionOutput(ADDRESS, Value.fromCoin(adaToLovelace(25))));

            List<Value> result = strategy.split(request(change, tx, null));

            assertThat(result).extracting(Value::getCoin).containsExactly(adaToLovelace(25), adaToLovelace(75));
        }

        @Test
        void tokenPayment_usesItsAdaAmount() {
            Value change = Value.fromCoin(adaToLovelace(100));
            Transaction tx = tx(new TransactionOutput(RECEIVER,
                    new Value(adaToLovelace(2), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)))));

            List<Value> result = strategy.split(request(change, tx, null));

            assertThat(result).extracting(Value::getCoin).containsExactly(adaToLovelace(2), adaToLovelace(98));
        }

        @Test
        void noTransaction_singleOutput() {
            Value change = Value.fromCoin(adaToLovelace(1000));

            assertThat(strategy.split(request(change))).containsExactly(change);
        }

        @Test
        void noPayments_singleOutput() {
            Value change = Value.fromCoin(adaToLovelace(1000));

            assertThat(strategy.split(request(change, tx(), null))).containsExactly(change);
        }
    }

    @Nested
    class WithTokens {

        @Test
        void tokensBundled_adaSplitByPaymentSize() {
            Value change = new Value(adaToLovelace(500), List.of(multiAsset(POLICY_1, 2, BigInteger.ONE)));

            List<Value> result = strategy.split(request(change, payments(40), null));

            assertThat(withTokens(result)).singleElement().satisfies(b -> assertThat(b.getCoin()).isEqualTo(minAda(b)));
            assertThat(adaOnly(result)).extracting(Value::getCoin).first().isEqualTo(adaToLovelace(40));
            assertThat(adaOnly(result)).hasSize(2);
            assertValid(change, result);
        }
    }

    @Nested
    class Configuration {

        @Test
        void defaults() {
            assertThat(strategy.getMaxPieces()).isEqualTo(5);
            assertThat(strategy.getTokenBundling()).isInstanceOf(ByteBudgetBundling.class);
        }

        @Test
        void maxPiecesBelowOne_rejected() {
            assertThatThrownBy(() -> PaymentSizedStrategy.builder().maxPieces(0).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static Transaction payments(double... ada) {
        return tx(Arrays.stream(ada)
                .mapToObj(a -> new TransactionOutput(RECEIVER, Value.fromCoin(BigInteger.valueOf((long) (a * 1_000_000)))))
                .toArray(TransactionOutput[]::new));
    }
}
