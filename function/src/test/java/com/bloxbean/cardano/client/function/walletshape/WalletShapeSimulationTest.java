package com.bloxbean.cardano.client.function.walletshape;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.walletshape.WalletShapeSimulator.Result;
import com.bloxbean.cardano.client.function.walletshape.WalletShapeSimulator.Workload;
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
import static com.bloxbean.cardano.client.function.walletshape.WalletShapeFixtures.multiAsset;
import static com.bloxbean.cardano.client.function.walletshape.WalletShapeFixtures.policy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wallet simulation of the default wallet shape (ADR Part A, §4.4). Prints a markdown report and checks the properties
 * the ADR relies on.
 */
class WalletShapeSimulationTest {
    static final int TRANSACTIONS = 300;
    static final Value WALLET = Value.fromCoin(adaToLovelace(10_000));
    static final long SEED = 1;

    static final String NONE = "None (today)";
    static final String DEFAULT = "Default";
    static final String CONSOLIDATING = "Default + consolidation";

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
            // 120 single-token policies take 4,684 bytes, just below maxValSize (5,000): a realistic worst case
            new Workload("hot UTxO: 10,000 ADA + 120 tokens, random 1-50 ADA", TRANSACTIONS, withTokens(10_000, 120),
                    r -> BigInteger.valueOf(1_000_000L + r.nextInt(49_000_000)), 0),
            new Workload("small wallet: 150 ADA + 20 tokens, random 1-3 ADA", 40, withTokens(150, 20),
                    r -> BigInteger.valueOf(1_000_000L + r.nextInt(2_000_000)), 0));

    static final Map<String, TxBuilder> SHAPERS = new LinkedHashMap<>();

    static {
        SHAPERS.put(NONE, null);
        SHAPERS.put(DEFAULT, new DefaultWalletShaper());
        SHAPERS.put(CONSOLIDATING, DefaultWalletShaper.withConsolidation());
    }

    static final Map<String, Result> RESULTS = new LinkedHashMap<>();

    @BeforeAll
    static void simulate() {
        for (Workload workload : WORKLOADS)
            for (var shaper : SHAPERS.entrySet())
                RESULTS.put(key(shaper.getKey(), workload.name()),
                        WalletShapeSimulator.run(shaper.getKey(), shaper.getValue(), workload, SEED));
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
    void default_keepsOneAdaOutput_inEveryWorkload() {
        for (Workload workload : WORKLOADS) {
            assertThat(result(DEFAULT, workload).maxAdaOnlyUtxos()).as(workload.name()).isEqualTo(1);
            assertThat(result(CONSOLIDATING, workload).maxAdaOnlyUtxos()).as(workload.name()).isEqualTo(1);
        }
    }

    @Test
    void hotUtxo_paymentsStopMovingTokens() {
        Workload hot = WORKLOADS.get(4);
        assertThat(result(NONE, hot).tokenChurn()).isEqualTo(1.0);
        assertThat(result(NONE, hot).avgTokensMoved()).isEqualTo(120.0);
        assertThat(result(DEFAULT, hot).tokenChurn()).isLessThan(0.01);
        assertThat(result(CONSOLIDATING, hot).tokenChurn()).isLessThan(0.01);
    }

    @Test
    void hotUtxo_tokensEndUpInFewCompactBundles() {
        Result r = result(DEFAULT, WORKLOADS.get(4));
        assertThat(r.finalTokenUtxos()).isLessThanOrEqualTo(7);
        assertThat(r.adaInTokenUtxos()).isLessThan(adaToLovelace(30));
    }

    @Test
    void consolidation_reclaimsAirdroppedTokenUtxos() {
        Workload airdrops = WORKLOADS.get(3);
        Result none = result(NONE, airdrops);
        Result consolidating = result(CONSOLIDATING, airdrops);
        assertThat(result(DEFAULT, airdrops).finalTokenUtxos()).isEqualTo(none.finalTokenUtxos());
        assertThat(consolidating.finalTokenUtxos()).isLessThan(none.finalTokenUtxos() / 2);
        assertThat(consolidating.adaInTokenUtxos()).isLessThan(none.adaInTokenUtxos());
    }

    @Test
    void consolidation_addsFewOutputsPerTransaction() {
        for (Workload workload : WORKLOADS)
            assertThat(result(CONSOLIDATING, workload).avgChangeOutputs()).as(workload.name()).isLessThan(1.3);
    }

    private static Value withTokens(long ada, int policies) {
        List<MultiAsset> tokens = new ArrayList<>();
        for (int i = 0; i < policies; i++)
            tokens.add(multiAsset(policy(i), 1, BigInteger.ONE));
        return new Value(adaToLovelace(ada), tokens);
    }

    private static Result result(String shaper, Workload workload) {
        return RESULTS.get(key(shaper, workload.name()));
    }

    private static String key(String shaper, String workload) {
        return shaper + " | " + workload;
    }

    private static String report() {
        List<String> lines = new ArrayList<>();
        for (Workload workload : WORKLOADS) {
            lines.add("");
            lines.add("### " + workload.name() + " (" + workload.transactions() + " tx)");
            lines.add("| Shaper | final UTxOs | max UTxOs | max ADA-only | avg inputs | avg change outputs | avg fundable | token churn | avg tokens moved | token UTxOs | ADA in token UTxOs |");
            lines.add("|---|---|---|---|---|---|---|---|---|---|---|");
            for (String shaper : SHAPERS.keySet()) {
                Result r = result(shaper, workload);
                lines.add(String.format(Locale.ROOT, "| %s | %d | %d | %d | %.2f | %.2f | %.1f | %.0f%% | %.1f | %d | %.2f |",
                        shaper, r.finalUtxos(), r.maxUtxos(), r.maxAdaOnlyUtxos(), r.avgInputs(),
                        r.avgChangeOutputs(), r.avgFundable(), r.tokenChurn() * 100, r.avgTokensMoved(),
                        r.finalTokenUtxos(), r.adaInTokenUtxos().doubleValue() / 1_000_000));
            }
        }
        return String.join("\n", lines);
    }
}
