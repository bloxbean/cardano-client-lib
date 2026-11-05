package com.bloxbean.cardano.client.tools.readerdiff;

import com.bloxbean.cardano.client.transaction.raw.RawBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * ADR 0003 I10's baseline: the bare parsers of one side on one thread, over the same blocks, with no JSON. The
 * transactions are split before any measuring. Each pass is one round; the JSON gives every round, so two sides can be
 * compared knowing how far a run varies by itself.
 */
final class Bench {
    private static final int WARM_UP_ROUNDS = 2;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static volatile Object sink;

    private Bench() {
    }

    static void run(Path sideDir, Path blocksFile, int rounds, Path outFile) throws Exception {
        List<byte[]> blocks = new ArrayList<>();
        List<byte[]> txs = new ArrayList<>();
        long[] totalBytes = new long[1];
        Blocks.read(blocksFile, block -> {
            blocks.add(block);
            totalBytes[0] += block.length;
            RawBlock raw = RawBlock.of(block);
            for (int i = 0; i < raw.txCount(); i++)
                txs.add(raw.txBytes(i));
        });
        Map<String, List<byte[]>> inputs = Map.of("yaci-block", blocks, "ccl-tx", txs);

        Map<String, Object> result = new LinkedHashMap<>();
        try (Side side = Side.open(sideDir)) {
            result.put("side", side.identity());
            result.put("blocksFile", blocksFile.toString());
            result.put("blocks", blocks.size());
            result.put("blockBytes", totalBytes[0]);
            result.put("txs", txs.size());
            result.put("warmUpRounds", WARM_UP_ROUNDS);
            result.put("rounds", rounds);
            result.put("java", System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
            result.put("jvmArgs", ManagementFactory.getRuntimeMXBean().getInputArguments());
            result.put("loadAverageAtStart", ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
            Map<String, Object> probes = new LinkedHashMap<>();
            for (Map.Entry<String, Function<byte[], Object>> parser : side.parsers().entrySet())
                probes.put(parser.getKey(), measure(parser.getKey(), parser.getValue(), inputs.get(parser.getKey()),
                        blocks.size(), rounds));
            result.put("probes", probes);
        }
        Files.createDirectories(outFile.toAbsolutePath().getParent());
        JSON.writeValue(outFile.toFile(), result);
        System.out.println(JSON.writeValueAsString(result));
    }

    private static Map<String, Object> measure(String name, Function<byte[], Object> parser, List<byte[]> inputs,
                                               int blocks, int rounds) {
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int i = 0; i < WARM_UP_ROUNDS; i++) {
            long rejected = pass(parser, inputs);
            System.out.printf("%s warm-up %d done, %d rejected%n", name, i + 1, rejected);
        }
        double[] wallPerBlock = new double[rounds];
        double[] allocatedPerBlock = new double[rounds];
        long rejected = 0;
        for (int i = 0; i < rounds; i++) {
            System.gc();
            long allocatedBefore = threads.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            rejected = pass(parser, inputs);
            wallPerBlock[i] = (double) (System.nanoTime() - start) / blocks;
            allocatedPerBlock[i] = (double) (threads.getThreadAllocatedBytes(thread) - allocatedBefore) / blocks;
            System.out.printf("%s round %d: %.0f ns/block, %.0f bytes/block%n", name, i + 1, wallPerBlock[i],
                    allocatedPerBlock[i]);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inputs", inputs.size());
        result.put("rejected", rejected);
        result.put("wallNanosPerBlock", spread(wallPerBlock));
        result.put("allocatedBytesPerBlock", spread(allocatedPerBlock));
        return result;
    }

    private static long pass(Function<byte[], Object> parser, List<byte[]> inputs) {
        long rejected = 0;
        for (byte[] input : inputs) {
            try {
                sink = parser.apply(input);
            } catch (RuntimeException e) {
                rejected++;
            }
        }
        return rejected;
    }

    /** @return the rounds, and how far they spread: {@code spreadPercent} is (max - min) / mean */
    static Map<String, Object> spread(double[] rounds) {
        double mean = Arrays.stream(rounds).average().orElseThrow();
        double min = Arrays.stream(rounds).min().orElseThrow();
        double max = Arrays.stream(rounds).max().orElseThrow();
        double variance = Arrays.stream(rounds).map(x -> (x - mean) * (x - mean)).sum() / rounds.length;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rounds", Arrays.stream(rounds).map(Math::rint).toArray());
        result.put("mean", Math.rint(mean));
        result.put("min", Math.rint(min));
        result.put("max", Math.rint(max));
        result.put("stddev", Math.rint(Math.sqrt(variance)));
        result.put("spreadPercent", Math.round((max - min) / mean * 1000) / 10.0);
        return result;
    }
}
