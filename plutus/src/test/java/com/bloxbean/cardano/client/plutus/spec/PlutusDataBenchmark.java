package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.CborDecoder;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
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
 * Old (recursive conversion on cbor-java's decoder) against new (iterative {@link PlutusDataCodec}) over every datum,
 * redeemer and inline datum of the real transactions and blocks. Reports median and p99 time and allocated bytes per
 * item.
 * <p>
 * Excluded from normal builds; run with {@code CCL_BENCHMARK=true ./gradlew :plutus:test --tests '*PlutusDataBenchmark'}.
 * The report is written to {@code plutus/build/plutus-data-benchmark.md}.
 */
@EnabledIfEnvironmentVariable(named = "CCL_BENCHMARK", matches = "true")
class PlutusDataBenchmark {
    private static final long WARMUP_NANOS = 5_000_000_000L;
    private static final int ROUNDS = 300;

    @Test
    void oldVersusNew() throws Exception {
        List<byte[]> datums = new ArrayList<>();
        Map<byte[], PlutusData> models = new IdentityHashMap<>();
        for (PlutusDataCorpus.Datum datum : PlutusDataCorpus.all()) {
            if (PlutusDataCorpus.tooDeepForRecursiveCode(datum.cbor()))
                continue; // the old path overflows
            datums.add(datum.cbor());
            models.put(datum.cbor(), PlutusData.deserialize(datum.cbor()));
        }

        StringBuilder report = new StringBuilder();
        report.append("| Path | Corpus | Old median (µs) | New median (µs) | Old p99 (µs) | New p99 (µs) | Old alloc (B/op) | New alloc (B/op) | Median new/old |\n");
        report.append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        compare(report, "`PlutusData.deserialize(byte[])`", datums.size() + " datums/redeemers", datums,
                bytes -> unchecked(() -> LegacyPlutusData.deserialize(CborDecoder.decode(bytes).get(0))),
                bytes -> unchecked(() -> PlutusData.deserialize(bytes)));
        compare(report, "`getDatumHash` (serialize + hash)", datums.size() + " datums/redeemers", datums,
                bytes -> unchecked(() -> Blake2bUtil.blake2bHash256(CborSerializationUtil.serialize(LegacyPlutusData.serialize(models.get(bytes))))),
                bytes -> unchecked(() -> models.get(bytes).getDatumHashAsBytes()));

        report.append("\nJVM: ").append(System.getProperty("java.vm.name")).append(' ').append(System.getProperty("java.version"))
                .append(", ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
                .append(". ").append(ROUNDS).append(" measured rounds after ").append(WARMUP_NANOS / 1_000_000_000L)
                .append(" s warm-up per path; one sample per item per round, each the mean of enough repetitions to last about 2 µs. The old getDatumHash path serializes the")
                .append(" model with the old recursive conversion and the current encoder.\n");
        Path out = Path.of("build", "plutus-data-benchmark.md");
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
