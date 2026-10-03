package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
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
        compare(report, "walk a whole tx (cbor-java `decode` vs `CborSpan.of`)", txs.size() + " txs", txs,
                TransactionBytesBenchmark::cborJavaDecode, CborSpan::of);
        compare(report, "walk a whole block (cbor-java `decode` vs `CborSpan.of`)", blocks.size() + " blocks", blocks,
                TransactionBytesBenchmark::cborJavaDecode, CborSpan::of);

        report.append("\nJVM: ").append(System.getProperty("java.vm.name")).append(' ').append(System.getProperty("java.version"))
                .append(", ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
                .append(". ").append(ROUNDS).append(" measured rounds after ").append(WARMUP_NANOS / 1_000_000_000L)
                .append(" s warm-up per path; one sample per item per round.\n");
        Path out = Path.of("build", "cbor-span-benchmark.md");
        Files.createDirectories(out.getParent());
        Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(report);
    }

    private static void compare(StringBuilder report, String path, String corpus, List<byte[]> items,
                                Consumer<byte[]> oldPath, Consumer<byte[]> newPath) {
        long warmupEnd = System.nanoTime() + WARMUP_NANOS;
        while (System.nanoTime() < warmupEnd) {
            items.forEach(oldPath);
            items.forEach(newPath);
        }
        long[] oldTimes = new long[ROUNDS * items.size()];
        long[] newTimes = new long[ROUNDS * items.size()];
        long oldAllocated = 0;
        long newAllocated = 0;
        for (int round = 0; round < ROUNDS; round++) {
            oldAllocated += measure(items, oldPath, oldTimes, round * items.size());
            newAllocated += measure(items, newPath, newTimes, round * items.size());
        }
        long operations = (long) ROUNDS * items.size();
        double oldMedian = percentile(oldTimes, 0.50);
        double newMedian = percentile(newTimes, 0.50);
        report.append(String.format("| %s | %s | %.2f | %.2f | %.2f | %.2f | %,d | %,d | %.2f |%n", path, corpus,
                oldMedian, newMedian, percentile(oldTimes, 0.99), percentile(newTimes, 0.99),
                oldAllocated / operations, newAllocated / operations, newMedian / oldMedian));
    }

    private static long measure(List<byte[]> items, Consumer<byte[]> path, long[] times, int from) {
        long allocatedBefore = allocatedBytes();
        for (int i = 0; i < items.size(); i++) {
            long start = System.nanoTime();
            path.accept(items.get(i));
            times[from + i] = System.nanoTime() - start;
        }
        return allocatedBytes() - allocatedBefore;
    }

    private static double percentile(long[] nanos, double percentile) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(percentile * sorted.length))] / 1_000.0;
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
