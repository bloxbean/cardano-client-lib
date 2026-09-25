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
    // default margin: 0.5 ADA fee allowance + ADA-only min-ada
    static final BigInteger MARGIN = BigInteger.valueOf(500_000).add(adaOnlyMinAda());

    PaymentSizedStrategy strategy = new PaymentSizedStrategy();

    @Nested
    class AdaOnly {

        @Test
        void onePayment_pieceOfPaymentPlusMargin_plusRemainder() {
            Value change = Value.fromCoin(adaToLovelace(990));

            List<Value> result = strategy.split(request(change, payments(10), null));

            assertThat(result).extracting(Value::getCoin).containsExactly(piece(10), adaToLovelace(990).subtract(piece(10)));
        }

        @Test
        void pieceCanFundTheSamePaymentAlone() {
            BigInteger fee = BigInteger.valueOf(200_000);

            List<Value> result = strategy.split(request(Value.fromCoin(adaToLovelace(990)), payments(10), null));

            BigInteger needed = adaToLovelace(10).add(fee).add(adaOnlyMinAda());
            assertThat(result.get(0).getCoin()).isGreaterThanOrEqualTo(needed);
        }

        @Test
        void severalPayments_largestFirst() {
            Value change = Value.fromCoin(adaToLovelace(1000));

            List<Value> result = strategy.split(request(change, payments(5, 50, 20), null));

            assertThat(result).extracting(Value::getCoin).containsExactly(
                    piece(50), piece(20), piece(5), adaToLovelace(1000).subtract(piece(50)).subtract(piece(20)).subtract(piece(5)));
        }

        @Test
        void maxPieces_capsNumberOfPiecesIncludingRemainder() {
            PaymentSizedStrategy capped = PaymentSizedStrategy.builder().maxPieces(3).build();
            Value change = Value.fromCoin(adaToLovelace(1000));

            List<Value> result = capped.split(request(change, payments(10, 20, 30, 40), null));

            assertThat(result).extracting(Value::getCoin)
                    .containsExactly(piece(40), piece(30), adaToLovelace(1000).subtract(piece(40)).subtract(piece(30)));
        }

        @Test
        void maxPiecesOne_neverSplits() {
            PaymentSizedStrategy one = PaymentSizedStrategy.builder().maxPieces(1).build();
            Value change = Value.fromCoin(adaToLovelace(1000));

            assertThat(one.split(request(change, payments(10), null))).containsExactly(change);
        }

        @Test
        void customFeeAllowance() {
            PaymentSizedStrategy custom = PaymentSizedStrategy.builder().feeAllowance(adaToLovelace(2)).build();
            Value change = Value.fromCoin(adaToLovelace(100));

            List<Value> result = custom.split(request(change, payments(10), null));

            assertThat(result.get(0).getCoin()).isEqualTo(adaToLovelace(12).add(adaOnlyMinAda()));
        }

        @Test
        void zeroFeeAllowance_pieceIsPaymentPlusMinAda() {
            PaymentSizedStrategy custom = PaymentSizedStrategy.builder().feeAllowance(BigInteger.ZERO).build();
            Value change = Value.fromCoin(adaToLovelace(100));

            List<Value> result = custom.split(request(change, payments(10), null));

            assertThat(result.get(0).getCoin()).isEqualTo(adaToLovelace(10).add(adaOnlyMinAda()));
        }

        @Test
        void paymentBelowMinAda_skipped() {
            Value change = Value.fromCoin(adaToLovelace(100));
            Transaction tx = tx(new TransactionOutput(RECEIVER, Value.fromCoin(BigInteger.valueOf(500_000))),
                    new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(7))));

            List<Value> result = strategy.split(request(change, tx, null));

            assertThat(result).extracting(Value::getCoin).containsExactly(piece(7), adaToLovelace(100).subtract(piece(7)));
        }

        @Test
        void pieceLeavingRemainderBelowMinAda_skipped_smallerOneStillUsed() {
            Value change = Value.fromCoin(adaToLovelace(50));

            List<Value> result = strategy.split(request(change, payments(49, 20), null));

            assertThat(result).extracting(Value::getCoin).containsExactly(piece(20), adaToLovelace(50).subtract(piece(20)));
        }

        @Test
        void pieceLargerThanChange_notUsed() {
            Value change = Value.fromCoin(adaToLovelace(11));

            assertThat(strategy.split(request(change, payments(10), null))).containsExactly(change);
        }

        @Test
        void changeOutputsAreNotPayments() {
            Value change = Value.fromCoin(adaToLovelace(100));
            Transaction tx = tx(new ChangeOutput(ADDRESS, Value.fromCoin(adaToLovelace(30))),
                    new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(10))));

            List<Value> result = strategy.split(request(change, tx, null));

            assertThat(result).extracting(Value::getCoin).containsExactly(piece(10), adaToLovelace(100).subtract(piece(10)));
        }

        @Test
        void paymentToOwnAddressCountsAsPayment() {
            Value change = Value.fromCoin(adaToLovelace(100));
            Transaction tx = tx(new TransactionOutput(ADDRESS, Value.fromCoin(adaToLovelace(25))));

            List<Value> result = strategy.split(request(change, tx, null));

            assertThat(result).extracting(Value::getCoin).containsExactly(piece(25), adaToLovelace(100).subtract(piece(25)));
        }

        @Test
        void tokenPayment_usesItsAdaAmount() {
            Value change = Value.fromCoin(adaToLovelace(100));
            Transaction tx = tx(new TransactionOutput(RECEIVER,
                    new Value(adaToLovelace(2), List.of(multiAsset(POLICY_1, 1, BigInteger.ONE)))));

            List<Value> result = strategy.split(request(change, tx, null));

            assertThat(result).extracting(Value::getCoin).containsExactly(piece(2), adaToLovelace(100).subtract(piece(2)));
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
            assertThat(adaOnly(result)).extracting(Value::getCoin).first().isEqualTo(piece(40));
            assertThat(adaOnly(result)).hasSize(2);
            assertValid(change, result);
        }
    }

    @Nested
    class Configuration {

        @Test
        void defaults() {
            assertThat(strategy.getMaxPieces()).isEqualTo(5);
            assertThat(strategy.getFeeAllowance()).isEqualTo(BigInteger.valueOf(500_000));
            assertThat(strategy.getTokenBundling()).isInstanceOf(ByteBudgetBundling.class);
        }

        @Test
        void maxPiecesBelowOne_rejected() {
            assertThatThrownBy(() -> PaymentSizedStrategy.builder().maxPieces(0).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void negativeFeeAllowance_rejected() {
            assertThatThrownBy(() -> PaymentSizedStrategy.builder().feeAllowance(BigInteger.valueOf(-1)).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static BigInteger piece(long paymentAda) {
        return adaToLovelace(paymentAda).add(MARGIN);
    }

    private static Transaction payments(long... ada) {
        return tx(Arrays.stream(ada)
                .mapToObj(a -> new TransactionOutput(RECEIVER, Value.fromCoin(adaToLovelace(a))))
                .toArray(TransactionOutput[]::new));
    }
}
