package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.transaction.spec.LegacyTransactionDeserializer;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Old (cbor-java) against new ({@link CborSpan}) for the paths moved onto the walker, over the real transaction and block
 * corpus. Reports median and p99 time per transaction (or block) and allocated bytes per operation.
 * <p>
 * Excluded from normal builds; run with {@code CCL_BENCHMARK=true ./gradlew :transaction-spec:test --tests '*TransactionBytesBenchmark'}.
 * The report is written to {@code transaction-spec/build/cbor-span-benchmark.md}.
 */
@EnabledIfEnvironmentVariable(named = "CCL_BENCHMARK", matches = "true")
class TransactionBytesBenchmark {
    private static final long WARMUP_NANOS = 5_000_000_000L;
    private static final int ROUNDS = 400;

    @Test
    void oldVersusNew() throws IOException {
        String trigger = RealCborFixtures.trigger().txHash();
        // The old path overflows the stack on the trigger, so the corpus for the comparison leaves it out
        List<byte[]> txs = RealCborFixtures.txs().stream().map(RealCborFixtures.Tx::cbor).collect(Collectors.toList());
        RealCborFixtures.blockTxs().stream().filter(tx -> !tx.txHash().equals(trigger)).forEach(tx -> txs.add(tx.cbor()));
        List<byte[]> blocks = RealCborFixtures.blocks().stream().filter(b -> !b.txHashes().contains(trigger))
                .map(RealCborFixtures.Block::cbor).collect(Collectors.toList());

        StringBuilder report = new StringBuilder();
        report.append("| Path | Corpus | Old median (µs) | New median (µs) | Old p99 (µs) | New p99 (µs) | Old alloc (B/op) | New alloc (B/op) | Median new/old |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        compare(report, "`new TransactionBytes(tx)`", txs.size() + " txs", txs,
                LegacyTransactionBytes::new, TransactionBytes::new);
        compare(report, "`TransactionUtil.getTxHash(byte[])`", txs.size() + " txs", txs,
                tx -> HexUtil.encodeHexString(Blake2bUtil.blake2bHash256(LegacyTransactionBytes.extractTransactionBodyFromTx(tx))),
                TransactionUtil::getTxHash);
        compare(report, "`TransactionUtil.extractTransactionBodyFromTx`", txs.size() + " txs", txs,
                LegacyTransactionBytes::extractTransactionBodyFromTx, TransactionUtil::extractTransactionBodyFromTx);
        // The witness step alone: both sides start from the same TransactionBytes
        byte[] vkey = new byte[32];
        byte[] signature = new byte[64];
        Map<byte[], TransactionBytes> sliced = new IdentityHashMap<>();
        txs.forEach(tx -> sliced.put(tx, new TransactionBytes(tx)));
        compare(report, "`TransactionSigner.addWitnessToTransaction` (witness step)", txs.size() + " txs", txs,
                tx -> LegacyTransactionSigner.addWitnessToTransaction(sliced.get(tx), vkey, signature),
                tx -> sliced.get(tx).withVkeyWitness(vkey, signature).getTxBytes());
        List<byte[]> modelTxs = txs.stream().filter(TransactionBytesBenchmark::decodesOnBothPaths).collect(Collectors.toList());
        compare(report, "`Transaction.deserialize` (cbor-java vs iterative decoder)", modelTxs.size() + " txs", modelTxs,
                TransactionBytesBenchmark::legacyDeserialize, TransactionBytesBenchmark::deserialize);
        compare(report, "walk a whole tx (cbor-java `decode` vs `CborSpan.of`)", txs.size() + " txs", txs,
                TransactionBytesBenchmark::cborJavaDecode, CborSpan::of);
        compare(report, "walk a whole block (cbor-java `decode` vs `CborSpan.of`)", blocks.size() + " blocks", blocks,
                TransactionBytesBenchmark::cborJavaDecode, CborSpan::of);

        report.append("\nJVM: ").append(System.getProperty("java.vm.name")).append(' ').append(System.getProperty("java.version"))
                .append(", ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
                .append(". ").append(ROUNDS).append(" measured rounds after ").append(WARMUP_NANOS / 1_000_000_000L)
                .append(" s warm-up per path; one sample per item per round, each the mean of enough repetitions to last about 2 µs.\n");
        Path out = Path.of("build", "cbor-span-benchmark.md");
        Files.createDirectories(out.getParent());
        Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(report);
    }

    // Each sample times enough repetitions of one item to last about 2 µs (the clock ticks in tens of nanoseconds), the
    // same count for the old and the new path, and records the time per operation.
    private static void compare(StringBuilder report, String path, String corpus, List<byte[]> items,
                                Consumer<byte[]> oldPath, Consumer<byte[]> newPath) {
        long warmupEnd = System.nanoTime() + WARMUP_NANOS;
        while (System.nanoTime() < warmupEnd) {
            items.forEach(oldPath);
            items.forEach(newPath);
        }
        int[] repetitions = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            long start = System.nanoTime();
            for (int r = 0; r < 100; r++)
                oldPath.accept(items.get(i));
            long perOperation = Math.max(1, (System.nanoTime() - start) / 100);
            repetitions[i] = (int) Math.min(1_000, Math.max(1, 2_000 / perOperation));
        }
        long operationsPerRound = Arrays.stream(repetitions).asLongStream().sum();
        double[] oldTimes = new double[ROUNDS * items.size()];
        double[] newTimes = new double[ROUNDS * items.size()];
        long oldAllocated = 0;
        long newAllocated = 0;
        for (int round = 0; round < ROUNDS; round++) {
            oldAllocated += measure(items, repetitions, oldPath, oldTimes, round * items.size());
            newAllocated += measure(items, repetitions, newPath, newTimes, round * items.size());
        }
        long operations = ROUNDS * operationsPerRound;
        double oldMedian = percentile(oldTimes, 0.50);
        double newMedian = percentile(newTimes, 0.50);
        report.append(String.format("| %s | %s | %.3f | %.3f | %.2f | %.2f | %,d | %,d | %.2f |%n", path, corpus,
                oldMedian, newMedian, percentile(oldTimes, 0.99), percentile(newTimes, 0.99),
                oldAllocated / operations, newAllocated / operations, newMedian / oldMedian));
    }

    private static long measure(List<byte[]> items, int[] repetitions, Consumer<byte[]> path, double[] times, int from) {
        long allocatedBefore = allocatedBytes();
        for (int i = 0; i < items.size(); i++) {
            byte[] item = items.get(i);
            int count = repetitions[i];
            long start = System.nanoTime();
            for (int r = 0; r < count; r++)
                path.accept(item);
            times[from + i] = (System.nanoTime() - start) / (double) count;
        }
        return allocatedBytes() - allocatedBefore;
    }

    private static double percentile(double[] nanos, double percentile) {
        double[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(percentile * sorted.length))] / 1_000.0;
    }

    private static boolean decodesOnBothPaths(byte[] tx) {
        try {
            LegacyTransactionDeserializer.deserialize(tx);
            Transaction.deserialize(tx);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void legacyDeserialize(byte[] tx) {
        try {
            LegacyTransactionDeserializer.deserialize(tx);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deserialize(byte[] tx) {
        try {
            Transaction.deserialize(tx);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void cborJavaDecode(byte[] bytes) {
        try {
            CborDecoder.decode(bytes);
        } catch (CborException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }
}
