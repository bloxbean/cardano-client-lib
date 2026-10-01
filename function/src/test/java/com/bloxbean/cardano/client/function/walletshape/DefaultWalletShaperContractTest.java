package com.bloxbean.cardano.client.function.walletshape;

import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.bloxbean.cardano.client.function.walletshape.WalletShapeFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules of the default wallet shape (ADR §3), checked on generated change values.
 */
class DefaultWalletShaperContractTest {
    private static final int CASES = 400;

    TxBuilderContext context = new TxBuilderContext(null, PROTOCOL_PARAMS).mergeChange(false);
    DefaultWalletShaper shaper = new DefaultWalletShaper();

    @Test
    void shapedChange_followsTheRules() {
        Random random = new Random(42);
        int shaped = 0;
        for (int i = 0; i < CASES; i++) {
            Value change = randomChange(random);
            Transaction tx = tx(new TransactionOutput(RECEIVER, Value.fromCoin(BigInteger.valueOf(10_000_000))),
                    new ChangeOutput(ADDRESS, change));

            shaper.apply(context, tx);

            List<Value> pieces = tx.getBody().getOutputs().stream()
                    .filter(o -> ADDRESS.equals(o.getAddress()))
                    .map(TransactionOutput::getValue)
                    .toList();
            String name = "case " + i + ": " + change;

            assertThat(ChangeValues.sumUnits(pieces)).as(name).isEqualTo(ChangeValues.toUnitMap(change));
            if (pieces.size() == 1)
                continue;

            shaped++;
            // every piece is valid; the fee reserve sits on the ADA piece
            assertThat(pieces).as(name).allSatisfy(p -> assertThat(p.getCoin()).isGreaterThanOrEqualTo(minAda(p)));
            // tokens are never mixed with spendable ADA: at most one ADA-only piece, bundles hold only min-ada
            assertThat(adaOnly(pieces)).as(name).hasSizeLessThanOrEqualTo(1);
            // outputs stay small and don't repeat a policy
            assertThat(withTokens(pieces)).as(name).allSatisfy(p -> {
                assertThat(ChangeValues.serializedSize(p.getMultiAssets())).isLessThanOrEqualTo(1000);
                assertThat(p.getMultiAssets().stream().map(MultiAsset::getPolicyId).distinct().count())
                        .isEqualTo(p.getMultiAssets().size());
            });
        }
        assertThat(shaped).as("generated inputs must exercise shaping").isGreaterThan(CASES / 4);
    }

    @Test
    void deterministic() {
        Random random = new Random(11);
        for (int i = 0; i < 50; i++) {
            Value change = randomChange(random);
            Transaction first = tx(new ChangeOutput(ADDRESS, change));
            Transaction second = tx(new ChangeOutput(ADDRESS, change));

            shaper.apply(context, first);
            shaper.apply(context, second);

            assertThat(first.getBody().getOutputs()).usingRecursiveComparison().isEqualTo(second.getBody().getOutputs());
        }
    }

    private static Value randomChange(Random random) {
        long lovelace = switch (random.nextInt(4)) {
            case 0 -> random.nextInt(5_000_000);                                // around the fee reserve
            case 1 -> 2_000_000L + random.nextInt(50_000_000);                  // small
            case 2 -> 50_000_000L + (long) random.nextInt(500_000_000);         // medium
            default -> 500_000_000L + (long) (random.nextDouble() * 50_000_000_000L); // large
        };

        List<MultiAsset> policies = new ArrayList<>();
        int policyCount = random.nextInt(4) == 0 ? 0 : random.nextInt(random.nextBoolean() ? 4 : 60);
        for (int p = 0; p < policyCount; p++)
            policies.add(multiAsset(policy(random.nextInt(1000) * 100 + p), 1 + random.nextInt(random.nextBoolean() ? 3 : 40),
                    BigInteger.valueOf(1 + random.nextInt(1_000_000))));

        return new Value(BigInteger.valueOf(lovelace), policies);
    }
}
