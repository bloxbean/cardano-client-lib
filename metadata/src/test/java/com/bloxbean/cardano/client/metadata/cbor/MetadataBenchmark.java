package com.bloxbean.cardano.client.metadata.cbor;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link CBORMetadata#deserialize(byte[])} on cbor-java's recursive decoder (old) against the iterative one (new), over
 * the metadata of the real transactions and blocks. Reports median and p99 time and allocated bytes per item. Encoding
 * and hashing go through the encoder measured in the {@code common} benchmark.
 * <p>
 * Excluded from normal builds; run with {@code CCL_BENCHMARK=true ./gradlew :metadata:test --tests '*MetadataBenchmark'}.
 * The report is written to {@code metadata/build/metadata-benchmark.md}.
 */
@EnabledIfEnvironmentVariable(named = "CCL_BENCHMARK", matches = "true")
class MetadataBenchmark {
    private static final long WARMUP_NANOS = 5_000_000_000L;
    private static final int ROUNDS = 300;

    @Test
    void oldVersusNew() throws Exception {
        List<byte[]> items = new ArrayList<>();
        MetadataCorpus.all().forEach(item -> items.add(item.cbor()));

        StringBuilder report = new StringBuilder();
        report.append("| Path | Corpus | Old median (µs) | New median (µs) | Old p99 (µs) | New p99 (µs) | Old alloc (B/op) | New alloc (B/op) | Median new/old |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        compare(report, "`CBORMetadata.deserialize(byte[])`", items.size() + " metadata", items,
                bytes -> unchecked(() -> CBORMetadata.deserialize((Map) CborDecoder.decode(bytes).get(0))),
                bytes -> unchecked(() -> CBORMetadata.deserialize(bytes)));

        report.append("\nJVM: ").append(System.getProperty("java.vm.name")).append(' ').append(System.getProperty("java.version"))
                .append(", ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
                .append(". ").append(ROUNDS).append(" measured rounds after ").append(WARMUP_NANOS / 1_000_000_000L)
                .append(" s warm-up per path; one sample per item per round, each the mean of enough repetitions to last about 2 µs.\n");
        Path out = Path.of("build", "metadata-benchmark.md");
        Files.createDirectories(out.getParent());
        Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(report);
    }

    private interface Body {
        Object run() throws Exception;
    }

    private static void unchecked(Body body) {
        try {
            body.run();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }
}
