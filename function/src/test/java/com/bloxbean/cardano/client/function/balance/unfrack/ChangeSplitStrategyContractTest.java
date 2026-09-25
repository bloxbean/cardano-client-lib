package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules every {@link ChangeSplitStrategy} must follow, checked on generated change values.
 */
class ChangeSplitStrategyContractTest {
    private static final int CASES = 300;

    static Stream<Arguments> strategies() {
        return Stream.of(
                Arguments.of("Evolution", new EvolutionStrategy()),
                Arguments.of("Evolution small threshold", EvolutionStrategy.builder()
                        .subdivideThreshold(BigInteger.valueOf(5_000_000)).bundleSize(3).build()),
                Arguments.of("Percentage", new PercentageSplitStrategy()),
                Arguments.of("Percentage per policy", PercentageSplitStrategy.builder()
                        .subdivideThreshold(BigInteger.valueOf(5_000_000))
                        .tokenBundling(new PolicyBundling(2)).build()),
                Arguments.of("EqualLanes", new EqualLanesStrategy()),
                Arguments.of("EqualLanes tiny lanes", EqualLanesStrategy.builder()
                        .lanes(30).minLaneAmount(BigInteger.ONE).build()),
                Arguments.of("PaymentSized", new PaymentSizedStrategy()),
                Arguments.of("TargetShape", new TargetShapeStrategy()),
                Arguments.of("TargetShape tiny lanes", TargetShapeStrategy.builder()
                        .targetLanes(20).laneAmount(BigInteger.ONE).tokenBundling(new ByteBudgetBundling(150)).build()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strategies")
    void piecesConserveValueAndMeetMinAda(String name, ChangeSplitStrategy strategy) {
        Random random = new Random(42);
        int splits = 0;
        for (int i = 0; i < CASES; i++) {
            Value change = randomChange(random);
            ChangeSplitRequest request = request(change, randomPayments(random), null);

            List<Value> pieces = strategy.split(request);

            assertThat(pieces).as("case %d: %s", i, change).isNotEmpty();
            if (pieces.size() == 1) {
                assertThat(pieces.get(0)).as("single piece must be the input").isSameAs(change);
            } else {
                splits++;
                assertConserved(change, pieces);
                assertAllMeetMinAda(pieces);
                assertThat(pieces).allSatisfy(p -> assertThat(p.getCoin().signum()).isPositive());
                assertThat(pieces).allSatisfy(p -> {
                    if (p.getMultiAssets() != null)
                        assertThat(p.getMultiAssets().stream().map(MultiAsset::getPolicyId).distinct().count())
                                .as("no repeated policy in one output")
                                .isEqualTo(p.getMultiAssets().size());
                });
            }
        }
        assertThat(splits).as("generated inputs must exercise splitting").isGreaterThan(CASES / 5);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strategies")
    void inputIsNotModified(String name, ChangeSplitStrategy strategy) {
        Random random = new Random(7);
        for (int i = 0; i < 50; i++) {
            Value change = randomChange(random);
            var before = ChangeValues.toUnitMap(change);

            strategy.split(request(change, randomPayments(random), null));

            assertThat(ChangeValues.toUnitMap(change)).isEqualTo(before);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strategies")
    void deterministic(String name, ChangeSplitStrategy strategy) {
        Random random = new Random(11);
        for (int i = 0; i < 50; i++) {
            Value change = randomChange(random);
            Transaction payments = randomPayments(random);

            assertThat(strategy.split(request(change, payments, null)))
                    .usingRecursiveComparison()
                    .isEqualTo(strategy.split(request(change, payments, null)));
        }
    }

    private static Value randomChange(Random random) {
        long lovelace = switch (random.nextInt(4)) {
            case 0 -> random.nextInt(3_000_000);                      // below/around min-ada
            case 1 -> 1_000_000L + random.nextInt(50_000_000);        // small
            case 2 -> 50_000_000L + (long) random.nextInt(500_000_000); // medium
            default -> 500_000_000L + (long) (random.nextDouble() * 50_000_000_000L); // large
        };

        List<MultiAsset> policies = new ArrayList<>();
        int policyCount = random.nextInt(4) == 0 ? 0 : random.nextInt(random.nextBoolean() ? 4 : 40);
        for (int p = 0; p < policyCount; p++)
            policies.add(multiAsset(policy(random.nextInt(1000) * 100 + p), 1 + random.nextInt(random.nextBoolean() ? 3 : 30),
                    BigInteger.valueOf(1 + random.nextInt(1_000_000))));

        return new Value(BigInteger.valueOf(lovelace), policies);
    }

    private static Transaction randomPayments(Random random) {
        int count = random.nextInt(5);
        TransactionOutput[] outputs = new TransactionOutput[count];
        for (int i = 0; i < count; i++)
            outputs[i] = new TransactionOutput(RECEIVER,
                    Value.fromCoin(BigInteger.valueOf(500_000L + (long) random.nextInt(200_000_000))));
        return tx(outputs);
    }
}
