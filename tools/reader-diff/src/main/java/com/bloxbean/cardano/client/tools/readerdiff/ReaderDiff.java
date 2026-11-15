package com.bloxbean.cardano.client.tools.readerdiff;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;


/**
 * Compares two builds of CCL and Yaci in one JVM over real blocks and the CBOR dataset (ADR 0003 I5, I6, I10). See the
 * README.
 */
public final class ReaderDiff {
    private static final Logger log = LoggerFactory.getLogger(ReaderDiff.class);

    private static final String USAGE = """
            usage:
              reader-diff dump --host=<host> --port=<port> --network=<mainnet|preprod|preview> --stop-slot=<slot>
                               [--every=<n>] [--limit=<n>] --out=<file>
              reader-diff diff --old=<sideDir> --new=<sideDir> --out=<dir> [--allow=<file>] [--workers=<n>]
                               (--blocks=<file> | --stream --host=<host> --port=<port> --network=<network>
                                --stop-slot=<slot>)
              reader-diff diff-dataset --old=<sideDir> --new=<sideDir> --dataset=<dir>/conway --out=<dir>
                               [--allow=<file>] [--workers=<n>]
              reader-diff bench --side=<sideDir> --blocks=<file> --out=<file> [--rounds=<n>]
            --every keeps one block in n per era, --limit at most n blocks per era. diff and diff-dataset exit with 1
            when there are differences.
            """;

    private ReaderDiff() {
    }

    public static void main(String[] args) {
        // always System.exit: the node connection's threads may be parked (BlockStream)
        int exitCode = 2;
        try {
            exitCode = run(args);
        } catch (Throwable t) {
            log.error("failed", t);
        }
        System.exit(exitCode);
    }

    static int run(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.print(USAGE);
            return 2;
        }
        Map<String, String> options = options(args);
        switch (args[0]) {
            case "dump" -> dump(options);
            case "diff" -> {
                return diff(options);
            }
            case "diff-dataset" -> {
                return diffDataset(options);
            }
            case "bench" -> Bench.run(Path.of(required(options, "side")), Path.of(required(options, "blocks")),
                    positive(options, "rounds", 5), Path.of(required(options, "out")));
            default -> {
                System.err.print(USAGE);
                return 2;
            }
        }
        return 0;
    }

    private static void dump(Map<String, String> options) throws Exception {
        int every = positive(options, "every", 1);
        int limit = positive(options, "limit", Integer.MAX_VALUE);
        Path file = Path.of(required(options, "out"));
        Map<String, long[]> perEra = new TreeMap<>(); // seen, written, bytes
        long[] nextLog = {System.currentTimeMillis() + 60_000};
        try (DataOutputStream out = Blocks.writer(file)) {
            BlockStream.stream(required(options, "host"), Integer.parseInt(required(options, "port")),
                    required(options, "network"), Long.parseLong(required(options, "stop-slot")), block -> {
                        long[] era = perEra.computeIfAbsent(Blocks.eraName(block), k -> new long[3]);
                        if (era[0]++ % every == 0 && era[1] < limit) {
                            Blocks.write(out, block);
                            era[1]++;
                            era[2] += block.length;
                        }
                        if (System.currentTimeMillis() > nextLog[0]) {
                            log.info("slot {}, {}", Blocks.slot(block), describe(perEra));
                            nextLog[0] = System.currentTimeMillis() + 60_000;
                        }
                    });
        }
        System.out.println("wrote " + file + ": " + describe(perEra));
    }

    private static String describe(Map<String, long[]> perEra) {
        StringBuilder text = new StringBuilder();
        perEra.forEach((era, n) -> text.append(era).append(" seen ").append(n[0]).append(" kept ").append(n[1])
                .append(" (").append(n[2] / 1024).append(" KiB)  "));
        return text.toString();
    }

    private static int diff(Map<String, String> options) throws Exception {
        try (Side oldSide = Side.open(Path.of(required(options, "old")));
             Side newSide = Side.open(Path.of(required(options, "new")));
             Report report = report(options, oldSide, newSide)) {
            Differ differ = new Differ(oldSide, newSide, allow(options), report);
            try (Workers workers = new Workers(workers(options)); Progress ignored = new Progress(report)) {
                Blocks.BlockConsumer perBlock = block -> workers.submit(() -> differ.block(block));
                if (options.containsKey("stream")) {
                    BlockStream.stream(required(options, "host"), Integer.parseInt(required(options, "port")),
                            required(options, "network"), Long.parseLong(required(options, "stop-slot")), perBlock);
                } else {
                    Blocks.read(Path.of(required(options, "blocks")), perBlock);
                }
            }
            return finish(report);
        }
    }

    private static int diffDataset(Map<String, String> options) throws Exception {
        Path dataset = Path.of(required(options, "dataset"));
        try (Side oldSide = Side.open(Path.of(required(options, "old")));
             Side newSide = Side.open(Path.of(required(options, "new")));
             Report report = report(options, oldSide, newSide)) {
            Differ differ = new Differ(oldSide, newSide, allow(options), report);
            Map<String, Function<byte[], String>> probes = oldSide.probes();
            List<Path> samples;
            try (Stream<Path> files = Files.walk(dataset)) {
                samples = files.filter(f -> f.getFileName().toString().endsWith(".input.cbor")).sorted().toList();
            }
            Map<String, Long> unmapped = new TreeMap<>();
            try (Workers workers = new Workers(workers(options)); Progress ignored = new Progress(report)) {
                for (Path sample : samples) {
                    Path relative = dataset.relativize(sample);
                    String rule = relative.getName(0).toString();
                    List<String> decoders = Stream.of("ccl:" + rule, "yaci:" + rule).filter(probes::containsKey).toList();
                    if (decoders.isEmpty()) {
                        unmapped.merge(rule, 1L, Long::sum);
                        continue;
                    }
                    String category = relative.getName(1).toString();
                    String group = category.equals("valid") || category.equals("manual-valid") ? "valid" : "invalid";
                    workers.submit(() -> {
                        byte[] input = Files.readAllBytes(sample);
                        for (String decoder : decoders)
                            differ.probe(decoder, group, relative.toString(), input);
                        report.inputDone();
                    });
                }
            }
            report.note("samples " + samples.size() + "; rules with no decoder, with their samples: " + unmapped);
            return finish(report);
        }
    }

    private static Report report(Map<String, String> options, Side oldSide, Side newSide) throws Exception {
        return new Report(Path.of(required(options, "out")),
                "old " + oldSide.identity() + "\nnew " + newSide.identity() + "\n");
    }

    private static Allow allow(Map<String, String> options) throws Exception {
        return options.containsKey("allow") ? Allow.load(Path.of(options.get("allow"))) : Allow.NONE;
    }

    private static int workers(Map<String, String> options) {
        return positive(options, "workers", Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }

    private static int finish(Report report) throws Exception {
        System.out.print(report.finish());
        return report.differences() == 0 ? 0 : 1;
    }

    private static final class Progress implements AutoCloseable {
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "progress");
            thread.setDaemon(true);
            return thread;
        });

        Progress(Report report) {
            timer.scheduleAtFixedRate(() -> log.info(report.progress()), 30, 30, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            timer.shutdownNow();
        }
    }

    /** {@code --key=value} options and {@code --flag}s, after the command. */
    static Map<String, String> options(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--"))
                throw new IllegalArgumentException("unexpected argument " + arg + "\n" + USAGE);
            int eq = arg.indexOf('=');
            options.put(eq < 0 ? arg.substring(2) : arg.substring(2, eq), eq < 0 ? "" : arg.substring(eq + 1));
        }
        return options;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null)
            throw new IllegalArgumentException("--" + key + " is required\n" + USAGE);
        return value;
    }

    private static int positive(Map<String, String> options, String key, int defaultValue) {
        String value = options.get(key);
        if (value == null)
            return defaultValue;
        int number = Integer.parseInt(value);
        if (number <= 0)
            throw new IllegalArgumentException("--" + key + " must be positive, not " + value);
        return number;
    }
}
