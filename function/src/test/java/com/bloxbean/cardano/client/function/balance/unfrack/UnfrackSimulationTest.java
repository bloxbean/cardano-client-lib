package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.function.balance.unfrack.UnfrackSimulator.Result;
import com.bloxbean.cardano.client.function.balance.unfrack.UnfrackSimulator.Workload;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.multiAsset;
import static com.bloxbean.cardano.client.function.balance.unfrack.UnfrackFixtures.policy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wallet simulation of all strategies (ADR §12.8). Prints a markdown report and checks the properties the ADR relies on.
 */
class UnfrackSimulationTest {
    static final int TRANSACTIONS = 300;
    static final Value WALLET = Value.fromCoin(adaToLovelace(10_000));
    static final long SEED = 1;

    static final List<Workload> WORKLOADS = List.of(
            new Workload("fixed 10 ADA", TRANSACTIONS, WALLET, r -> adaToLovelace(10), 0),
            new Workload("random 1-50 ADA", TRANSACTIONS, WALLET,
                    r -> BigInteger.valueOf(1_000_000L + r.nextInt(49_000_000)), 0),
            new Workload("mixed 2-10 / 100-300 ADA", TRANSACTIONS, WALLET,
                    r -> BigInteger.valueOf(r.nextInt(10) == 0
                            ? 100_000_000L + r.nextInt(200_000_000)
                            : 2_000_000L + r.nextInt(8_000_000)), 0),
            new Workload("random 1-50 ADA + airdrop every 5 tx", TRANSACTIONS, WALLET,
                    r -> BigInteger.valueOf(1_000_000L + r.nextInt(49_000_000)), 5),
            new Workload("hot UTxO: 10,000 ADA + 150 tokens, random 1-50 ADA", TRANSACTIONS, withTokens(10_000, 150),
                    r -> BigInteger.valueOf(1_000_000L + r.nextInt(49_000_000)), 0),
            new Workload("small wallet: 150 ADA + 20 tokens, random 1-3 ADA", 40, withTokens(150, 20),
                    r -> BigInteger.valueOf(1_000_000L + r.nextInt(2_000_000)), 0));

    static final Map<String, ChangeSplitStrategy> STRATEGIES = new LinkedHashMap<>();

    static {
        STRATEGIES.put("None (today)", request -> List.of(request.getChange()));
        STRATEGIES.put("Evolution", new EvolutionStrategy());
        STRATEGIES.put("PercentageSplit", new PercentageSplitStrategy());
        STRATEGIES.put("EqualLanes 5x10", new EqualLanesStrategy());
        STRATEGIES.put("EqualLanes 5x60", EqualLanesStrategy.builder().minLaneAmount(adaToLovelace(60)).build());
        STRATEGIES.put("PaymentSized", new PaymentSizedStrategy());
        STRATEGIES.put("TargetShape 5x10", new TargetShapeStrategy());
        STRATEGIES.put("TargetShape 5x60", TargetShapeStrategy.builder().laneAmount(adaToLovelace(60)).build());
        STRATEGIES.put("TargetShape 10x60", TargetShapeStrategy.builder()
                .targetLanes(10).laneAmount(adaToLovelace(60)).build());
    }

    static final Map<String, Result> RESULTS = new LinkedHashMap<>();

    @BeforeAll
    static void simulate() {
        for (Workload workload : WORKLOADS)
            for (var strategy : STRATEGIES.entrySet())
                RESULTS.put(key(strategy.getKey(), workload.name()),
                        UnfrackSimulator.run(strategy.getKey(), strategy.getValue(), workload, SEED));
        System.out.println(report());
    }

    @Test
    void everyRun_completesAllTransactions_andConservesAdaAndTokens() {
        assertThat(RESULTS.values()).allSatisfy(r -> {
            Workload workload = WORKLOADS.stream().filter(w -> w.name().equals(r.workload())).findFirst().orElseThrow();
            assertThat(r.transactions()).as(r.strategy() + " / " + r.workload()).isEqualTo(workload.transactions());
            assertThat(r.finalBalance()).as(r.strategy() + " / " + r.workload()).isEqualTo(r.expectedBalance());
            assertThat(r.finalTokenUnits()).as(r.strategy() + " / " + r.workload()).isEqualTo(r.expectedTokenUnits());
        });
    }

    @Test
    void withoutUnfrack_walletKeepsOneAdaUtxo() {
        for (Workload workload : WORKLOADS.subList(0, 4))
            assertThat(result("None (today)", workload).maxAdaOnlyUtxos()).isEqualTo(1);
    }

    @Test
    void targetShape_neverExceedsItsTargetNumberOfAdaUtxos() {
        for (Workload workload : WORKLOADS) {
            assertThat(result("TargetShape 5x10", workload).maxAdaOnlyUtxos()).as(workload.name()).isLessThanOrEqualTo(5);
            assertThat(result("TargetShape 5x60", workload).maxAdaOnlyUtxos()).as(workload.name()).isLessThanOrEqualTo(5);
            assertThat(result("TargetShape 10x60", workload).maxAdaOnlyUtxos()).as(workload.name()).isLessThanOrEqualTo(10);
        }
    }

    @Test
    void perTransactionStrategies_keepGrowingTheWallet() {
        Workload fixed = WORKLOADS.get(0);
        for (String strategy : List.of("Evolution", "PercentageSplit", "EqualLanes 5x10", "EqualLanes 5x60", "PaymentSized"))
            assertThat(result(strategy, fixed).maxUtxos()).as(strategy).isGreaterThan(100);
    }

    @Test
    void paymentSized_piecesCanFundTheSamePaymentAlone() {
        assertThat(result("PaymentSized", WORKLOADS.get(0)).avgFundable()).isGreaterThan(100);
    }

    @Test
    void hotUtxo_everyStrategyStopsMovingTokensWithPayments() {
        Workload hot = WORKLOADS.get(4);
        assertThat(result("None (today)", hot).tokenChurn()).isEqualTo(1.0);
        for (String strategy : STRATEGIES.keySet())
            if (!strategy.startsWith("None"))
                assertThat(result(strategy, hot).tokenChurn()).as(strategy).isLessThan(0.01);
    }

    @Test
    void byteBudgetBundling_locksFarLessAdaThanOneBundlePerPolicy() {
        Workload hot = WORKLOADS.get(4);
        BigInteger evolution = result("Evolution", hot).adaInTokenUtxos();
        BigInteger percentage = result("PercentageSplit", hot).adaInTokenUtxos();
        assertThat(percentage.multiply(BigInteger.valueOf(4))).isLessThan(evolution);
    }

    @Test
    void evolutionSpread_storesAdaNextToTokensInSmallWallets() {
        Workload small = WORKLOADS.get(5);
        BigInteger evolution = result("Evolution", small).adaInTokenUtxos();
        BigInteger percentage = result("PercentageSplit", small).adaInTokenUtxos();
        assertThat(evolution).isGreaterThan(percentage.multiply(BigInteger.valueOf(3)));
    }

    private static Value withTokens(long ada, int policies) {
        List<MultiAsset> tokens = new ArrayList<>();
        for (int i = 0; i < policies; i++)
            tokens.add(multiAsset(policy(i), 1, BigInteger.ONE));
        return new Value(adaToLovelace(ada), tokens);
    }

    private static Result result(String strategy, Workload workload) {
        return RESULTS.get(key(strategy, workload.name()));
    }

    private static String key(String strategy, String workload) {
        return strategy + " | " + workload;
    }

    private static String report() {
        List<String> lines = new ArrayList<>();
        for (Workload workload : WORKLOADS) {
            lines.add("");
            lines.add("### " + workload.name() + " (" + workload.transactions() + " tx)");
            lines.add("| Strategy | final UTxOs | max UTxOs | max ADA-only | avg inputs | avg change outputs | avg fundable | token churn | avg tokens moved | ADA in token UTxOs |");
            lines.add("|---|---|---|---|---|---|---|---|---|---|");
            for (String strategy : STRATEGIES.keySet()) {
                Result r = result(strategy, workload);
                lines.add(String.format(Locale.ROOT, "| %s | %d | %d | %d | %.2f | %.2f | %.1f | %.0f%% | %.1f | %.2f |",
                        strategy, r.finalUtxos(), r.maxUtxos(), r.maxAdaOnlyUtxos(), r.avgInputs(),
                        r.avgChangeOutputs(), r.avgFundable(), r.tokenChurn() * 100, r.avgTokensMoved(),
                        r.adaInTokenUtxos().doubleValue() / 1_000_000));
            }
        }
        return String.join("\n", lines);
    }
}
