package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.custom.LegacyCborEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Old (cbor-java's recursive decoder and the recursive encoder) against new (iterative {@link CborSerializationUtil}) over
 * the real transactions and blocks. Reports median and p99 time and allocated bytes per item.
 * <p>
 * Excluded from normal builds; run with {@code CCL_BENCHMARK=true ./gradlew :common:test --tests '*CborCodecBenchmark'}.
 * The report is written to {@code common/build/cbor-codec-benchmark.md}.
 */
@EnabledIfEnvironmentVariable(named = "CCL_BENCHMARK", matches = "true")
class CborCodecBenchmark {
    private static final long WARMUP_NANOS = 5_000_000_000L;
    private static final int ROUNDS = 300;

    @Test
    void oldVersusNew() throws IOException {
        List<byte[]> txs = new ArrayList<>();
        List<byte[]> blocks = new ArrayList<>();
        Map<byte[], DataItem> trees = new IdentityHashMap<>();
        for (RealCborCorpus.Item item : RealCborCorpus.all()) {
            try {
                trees.put(item.cbor(), CborDecoder.decode(item.cbor()).get(0));
            } catch (StackOverflowError | CborException e) {
                continue; // the old decoder overflows on the deeply nested trigger
            }
            (item.name().startsWith("block") ? blocks : txs).add(item.cbor());
        }

        StringBuilder report = new StringBuilder();
        report.append("| Path | Corpus | Old median (µs) | New median (µs) | Old p99 (µs) | New p99 (µs) | Old alloc (B/op) | New alloc (B/op) | Median new/old |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        compare(report, "`CborSerializationUtil.deserialize` (decode)", txs.size() + " txs", txs,
                CborCodecBenchmark::cborJavaDecode, CborSerializationUtil::deserializeAll);
        compare(report, "`CborSerializationUtil.deserialize` (decode)", blocks.size() + " blocks", blocks,
                CborCodecBenchmark::cborJavaDecode, CborSerializationUtil::deserializeAll);
        compare(report, "`CborSerializationUtil.serialize` (canonical encode)", txs.size() + " txs", txs,
                tx -> legacyEncode(trees.get(tx), true), tx -> encode(trees.get(tx), true));
        compare(report, "`CborSerializationUtil.serialize` (canonical encode)", blocks.size() + " blocks", blocks,
                block -> legacyEncode(trees.get(block), true), block -> encode(trees.get(block), true));
        compare(report, "`CborSerializationUtil.serialize(item, false)` (encode)", txs.size() + " txs", txs,
                tx -> legacyEncode(trees.get(tx), false), tx -> encode(trees.get(tx), false));

        report.append("\nJVM: ").append(System.getProperty("java.vm.name")).append(' ').append(System.getProperty("java.version"))
                .append(", ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
                .append(". ").append(ROUNDS).append(" measured rounds after ").append(WARMUP_NANOS / 1_000_000_000L)
                .append(" s warm-up per path; one sample per item per round.\n");
        Path out = Path.of("build", "cbor-codec-benchmark.md");
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

    // CborSerializationUtil.serialize as it was: the same builder and stream, with the recursive encoder.
    private static void legacyEncode(DataItem item, boolean canonical) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            co.nstant.in.cbor.CborBuilder builder = new co.nstant.in.cbor.CborBuilder();
            builder.add(item);
            LegacyCborEncoder encoder = new LegacyCborEncoder(out);
            if (!canonical)
                encoder.nonCanonical();
            encoder.encode(builder.build());
            out.toByteArray();
        } catch (CborException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void encode(DataItem item, boolean canonical) {
        try {
            CborSerializationUtil.serialize(item, canonical);
        } catch (CborException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }
}
