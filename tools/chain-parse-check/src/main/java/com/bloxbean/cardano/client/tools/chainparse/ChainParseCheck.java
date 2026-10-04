package com.bloxbean.cardano.client.tools.chainparse;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.core.common.Constants;
import com.bloxbean.cardano.yaci.core.model.BlockHeader;
import com.bloxbean.cardano.yaci.core.model.byron.ByronBlockHead;
import com.bloxbean.cardano.yaci.core.model.byron.ByronEbHead;
import com.bloxbean.cardano.yaci.core.network.TCPNodeClient;
import com.bloxbean.cardano.yaci.core.protocol.Message;
import com.bloxbean.cardano.yaci.core.protocol.blockfetch.BlockfetchAgent;
import com.bloxbean.cardano.yaci.core.protocol.blockfetch.BlockfetchAgentListener;
import com.bloxbean.cardano.yaci.core.protocol.blockfetch.messages.MsgBlock;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Tip;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.n2n.ChainSyncAgentListener;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.n2n.ChainsyncAgent;
import com.bloxbean.cardano.yaci.core.protocol.handshake.HandshakeAgent;
import com.bloxbean.cardano.yaci.core.protocol.handshake.HandshakeAgentListener;
import com.bloxbean.cardano.yaci.core.protocol.handshake.util.N2NVersionTableConstant;
import com.bloxbean.cardano.yaci.core.protocol.keepalive.KeepAliveAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongToIntFunction;
import java.util.stream.Stream;

/**
 * Streams every block from the first Shelley block to the tip over node-to-node block-fetch (one connection) and runs
 * the CCL raw-view and model checks on each. Run with {@code --help} for the options.
 */
public final class ChainParseCheck {
    private static final Logger log = LoggerFactory.getLogger(ChainParseCheck.class);

    private static final String USAGE = """
            usage: chain-parse-check --network=<mainnet|preprod|preview> [options]
              --host=<host> --port=<port>  node to read from (default: the network's public relay)
              --run-dir=<dir>              state and results (default: runs/<network>)
              --fresh                      discard the saved state and issues of a previous run
              --workers=<n>                parser threads (default 1)
              --max-blocks=<n>             stop after n blocks in this session (n > 0)
              --stop-slot=<slot>           stop after the last block at or before this slot
              --koios=<url>                Koios API for cost models (default: the network's public Koios)
              --no-script-data-hash        skip the script data hash check (no Koios requests)
            """;

    record Net(String name, String host, int port, long magic, Point intersect, String koios,
               LongToIntFunction epochOfSlot) {}

    static Net net(String name) {
        return switch (name) {
            // intersect at the last Byron block; the next header is the first Shelley block (slot 4492800)
            case "mainnet" -> new Net(name, Constants.MAINNET_PUBLIC_RELAY_ADDR, Constants.MAINNET_PUBLIC_RELAY_PORT,
                    Constants.MAINNET_PROTOCOL_MAGIC,
                    new Point(4492799, "f8084c61b6a238acec985b59310b6ecec49c0ab8352249afd7268da5cff2a457"),
                    "https://api.koios.rest/api/v1", s -> (int) (208 + (s - 4492800) / 432000));
            case "preprod" -> new Net(name, Constants.PREPROD_PUBLIC_RELAY_ADDR, Constants.PREPROD_PUBLIC_RELAY_PORT,
                    Constants.PREPROD_PROTOCOL_MAGIC, Point.ORIGIN,
                    "https://preprod.koios.rest/api/v1", s -> (int) (4 + (s - 86400) / 432000));
            case "preview" -> new Net(name, Constants.PREVIEW_PUBLIC_RELAY_ADDR, Constants.PREVIEW_PUBLIC_RELAY_PORT,
                    Constants.PREVIEW_PROTOCOL_MAGIC, Point.ORIGIN,
                    "https://preview.koios.rest/api/v1", s -> (int) (s / 86400));
            default -> throw new IllegalArgumentException("--network must be mainnet, preprod or preview");
        };
    }

    private static final byte[] DONE = new byte[0];
    private static final String NETWORK = "network";
    private static final String IDENTITY = "identity";
    private static final String ISSUES = "issues.jsonl";
    private static final String SUMMARY_TXT = "summary.txt";
    private static final String FAILED_BLOCKS = "failed-blocks";

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);
        if (opts.containsKey("help") || !opts.containsKey(NETWORK)) {
            System.out.print(USAGE);
            System.exit(opts.containsKey("help") ? 0 : 2);
        }
        Net net = net(opts.get(NETWORK));
        String host = opts.getOrDefault("host", net.host());
        int port = Integer.parseInt(opts.getOrDefault("port", String.valueOf(net.port())));
        boolean fresh = opts.containsKey("fresh");
        long maxBlocks = positive(opts, "max-blocks", Long.MAX_VALUE);
        long stopSlot = Long.parseLong(opts.getOrDefault("stop-slot", String.valueOf(Long.MAX_VALUE)));
        int workers = Math.toIntExact(positive(opts, "workers", 1));
        boolean scriptDataHash = !opts.containsKey("no-script-data-hash");
        Path dir = Paths.get(opts.getOrDefault("run-dir", "runs/" + net.name()));
        Files.createDirectories(dir);
        Path stateFile = dir.resolve("state.json");
        if (fresh) {
            for (String f : List.of("state.json", ISSUES, "status.json", "summary.json", SUMMARY_TXT))
                Files.deleteIfExists(dir.resolve(f));
            if (Files.exists(dir.resolve(FAILED_BLOCKS)))
                try (Stream<Path> files = Files.walk(dir.resolve(FAILED_BLOCKS))) {
                    for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
                }
        }

        Map<String, String> identity = identity(net.name(), scriptDataHash);
        State state;
        Point from;
        if (Files.exists(stateFile)) {
            try {
                state = State.resume(stateFile, identity);
            } catch (IllegalStateException e) {
                log.error("cannot resume: {}", e.getMessage());
                System.exit(2);
                return;
            }
            from = new Point(state.lastSlot, state.lastHash);
            log.info("resuming {} after slot {} block {}", net.name(), state.lastSlot, state.lastBlockNo);
        } else {
            state = new State();
            state.network = net.name();
            state.identity = identity;
            from = null;
        }
        log.info("reading {} from {}:{}, results in {}", net.name(), host, port, dir.toAbsolutePath());
        Point[] found = discover(host, port, net.magic(), from == null ? net.intersect() : from, from == null);
        if (from == null) from = found[0];
        Point tip = found[1];
        log.info("range {} .. tip {}", from, tip);

        CostModels costModels = null;
        if (!scriptDataHash) {
            log.info("script data hash check skipped (--no-script-data-hash)");
        } else {
            try {
                costModels = new CostModels(opts.getOrDefault("koios", net.koios()), dir.resolve("cost_models.json"));
            } catch (Exception e) {
                log.warn("cost models unavailable, script data hash check skipped: {}", e.toString());
            }
        }
        Issues issues = new Issues(state, dir.resolve(ISSUES), dir.resolve(FAILED_BLOCKS));
        CostModels cm = costModels;
        ThreadLocal<Checker> checkers = ThreadLocal.withInitial(() -> new Checker(state, issues, cm, net.epochOfSlot()));
        ExecutorService pool = Executors.newFixedThreadPool(workers);

        BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(512);
        AtomicLong lastProgress = new AtomicLong(System.currentTimeMillis());
        CompletableFuture<String> finished = new CompletableFuture<>();

        Thread dispatcher = new Thread(() -> finished.complete(
                dispatch(state, queue, tip, stopSlot, maxBlocks, checkers, pool, lastProgress, dir)), "dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();

        // block-fetch: one n2n connection; MsgBlock bytes go straight to the queue (Yaci's block parser is bypassed)
        HandshakeAgent handshake = new HandshakeAgent(N2NVersionTableConstant.v4AndAbove(net.magic()));
        KeepAliveAgent keepAlive = new KeepAliveAgent();
        RawBlockfetch fetch = new RawBlockfetch(queue, tip);
        fetch.resetPoints(from, tip);
        fetch.addListener(new BlockfetchAgentListener() {
            @Override
            public void batchDone() {
                put(queue, DONE);
            }

            @Override
            public void noBlockFound(Point f, Point t) {
                log.warn("no blocks for range {} .. {}", f, t);
                put(queue, DONE);
            }
        });
        handshake.addListener(new HandshakeAgentListener() {
            @Override
            public void handshakeOk() {
                log.info("handshake ok, requesting blocks from {}", fetch.from());
                fetch.sendNextMessage();
            }
        });
        TCPNodeClient client = new TCPNodeClient(host, port, handshake, keepAlive, fetch);
        client.start();

        int cookie = 1;
        long lastKeepAlive = System.currentTimeMillis();
        while (!finished.isDone()) {
            Thread.sleep(1000);
            long now = System.currentTimeMillis();
            if (now - lastKeepAlive > 20_000) {
                cookie = cookie % 60000 + 1;
                try {
                    keepAlive.sendKeepAlive(cookie);
                } catch (Exception e) {
                    log.debug("keep-alive not sent: {}", e.toString()); // the stall check below reconnects if needed
                }
                lastKeepAlive = now;
            }
            if (now - lastProgress.get() > 180_000 && queue.isEmpty()) { // stalled: reconnect from the last block
                log.warn("no progress for 180 s; reconnecting from {}", fetch.from());
                lastProgress.set(now);
                client.restartSession();
            }
        }
        String outcome = finished.get();
        writeSummary(dir, state, tip, outcome);
        log.info("done: {}; summary in {}", outcome, dir.resolve(SUMMARY_TXT).toAbsolutePath());
        // no client.shutdown(): the event loop may be parked on the full queue; exiting closes the socket
        System.exit(outcome.startsWith("failed") ? 1 : 0);
    }

    /**
     * Runs the queued blocks in batches on the pool until the range ends, the stop slot or {@code maxBlocks} blocks in
     * this session; the resume point advances only after a whole batch is done. Returns the outcome.
     */
    static String dispatch(State state, BlockingQueue<byte[]> queue, Point tip, long stopSlot, long maxBlocks,
                           ThreadLocal<Checker> checkers, ExecutorService pool, AtomicLong lastProgress, Path dir) {
        Path stateFile = dir.resolve("state.json");
        long startMs = System.currentTimeMillis();
        long lastSave = startMs;
        long baseElapsed = state.elapsedMillis;
        long windowStart = startMs;
        long windowBlocks = state.processedBlocks;
        long windowTxs = txs(state);
        double recentBps = 0;
        double recentTps = 0;
        long dispatched = 0; // blocks of this session put into batches
        long dispatchedSlot = state.lastSlot;
        String outcome = null;
        try {
            while (outcome == null) {
                List<Callable<Void>> batch = new ArrayList<>();
                Point batchLast = null;
                long batchLastNo = 0;
                long batchSize = Math.min(256, maxBlocks - dispatched);
                while (batch.size() < batchSize) {
                    byte[] b = batch.isEmpty() ? queue.take() : queue.poll(200, TimeUnit.MILLISECONDS);
                    if (b == null) break;
                    if (b == DONE) {
                        outcome = dispatchedSlot >= tip.getSlot() ? "complete" : "range ended early";
                        break;
                    }
                    Point p = pointOf(b);
                    if (p != null) {
                        if (p.getSlot() <= dispatchedSlot) continue; // replay after a reconnect
                        if (p.getSlot() > stopSlot) { outcome = "stopped at --stop-slot"; break; }
                        dispatchedSlot = p.getSlot();
                        batchLast = p;
                        batchLastNo = blockNoOf(b);
                    }
                    batch.add(() -> {
                        try {
                            checkers.get().block(b);
                        } catch (Throwable t) {
                            log.error("block check error: {}", Checker.trace(t));
                            synchronized (state) { state.issueCounts.merge("block_harness_error", 1L, Long::sum); }
                        }
                        return null;
                    });
                }
                dispatched += batch.size();
                for (Future<Void> f : pool.invokeAll(batch)) f.get();
                if (batchLast != null) state.advance(batchLast.getSlot(), batchLast.getHash(), batchLastNo);
                lastProgress.set(System.currentTimeMillis());
                if (outcome == null && dispatched >= maxBlocks)
                    outcome = "stopped at --max-blocks";
                long now = System.currentTimeMillis();
                if (outcome == null && now - lastSave > 15_000) {
                    double secs = (now - windowStart) / 1000.0;
                    recentBps = (state.processedBlocks - windowBlocks) / secs;
                    recentTps = (txs(state) - windowTxs) / secs;
                    windowStart = now;
                    windowBlocks = state.processedBlocks;
                    windowTxs = txs(state);
                    state.elapsedMillis = baseElapsed + (now - startMs);
                    state.save(stateFile);
                    writeStatus(dir, state, tip, "running", recentBps, recentTps, queue.size());
                    log.info("slot {} ({}%) blocks {} txs {} - {} blocks/s, issues {}", state.lastSlot,
                            percent(state, tip), state.processedBlocks, txs(state), Math.round(recentBps),
                            state.issueCounts);
                    lastSave = now;
                }
            }
            state.elapsedMillis = baseElapsed + (System.currentTimeMillis() - startMs);
            state.save(stateFile);
            writeStatus(dir, state, tip, outcome, recentBps, recentTps, 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = "failed: interrupted";
        } catch (Throwable t) {
            outcome = "failed: " + t;
        }
        return outcome;
    }

    /** A positive number option, or the default when it is absent. */
    static long positive(Map<String, String> opts, String key, long defaultValue) {
        String v = opts.get(key);
        if (v == null) return defaultValue;
        long n;
        try {
            n = Long.parseLong(v);
        } catch (NumberFormatException e) {
            n = 0;
        }
        if (n <= 0) throw new IllegalArgumentException("--" + key + " must be a positive number, not '" + v + "'");
        return n;
    }

    /**
     * What a run's counters depend on, saved with its state: the network, the CCL under test (its version and a hash of
     * its jars, as a SNAPSHOT can be republished with other code) and the check options. A run resumes only with the
     * same identity.
     */
    static Map<String, String> identity(String network, boolean scriptDataHash) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(NETWORK, network);
        m.put("cclVersion", System.getProperty("ccl.version", "unknown"));
        m.put("cclJarsSha256", cclJarsSha256(System.getProperty("java.class.path")));
        m.put("scriptDataHash", String.valueOf(scriptDataHash));
        return m;
    }

    /** SHA-256 over the names and contents of the {@code cardano-client-*.jar}s on a class path, in name order. */
    static String cclJarsSha256(String classPath) throws IOException {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        List<Path> jars = Arrays.stream(classPath.split(File.pathSeparator)).map(Paths::get)
                .filter(p -> p.getFileName() != null && p.getFileName().toString().startsWith("cardano-client-")
                        && p.getFileName().toString().endsWith(".jar"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();
        for (Path jar : jars) {
            sha.update(jar.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            sha.update(Files.readAllBytes(jar));
        }
        return HexUtil.encodeHexString(sha.digest());
    }

    /** {@code --key=value} options and {@code --flag}s. */
    static Map<String, String> parse(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String a : args) {
            if (!a.startsWith("--")) throw new IllegalArgumentException("unexpected argument " + a + "\n" + USAGE);
            int eq = a.indexOf('=');
            m.put(eq < 0 ? a.substring(2) : a.substring(2, eq), eq < 0 ? "" : a.substring(eq + 1));
        }
        return m;
    }

    /** A block-fetch agent that hands each block's bytes over without decoding it, and tracks the resume point. */
    static final class RawBlockfetch extends BlockfetchAgent {
        private final BlockingQueue<byte[]> queue;
        private final Point to;
        private volatile Point last;

        RawBlockfetch(BlockingQueue<byte[]> queue, Point to) {
            this.queue = queue;
            this.to = to;
        }

        Point from() {
            return last;
        }

        @Override
        public void resetPoints(Point from, Point to) {
            super.resetPoints(from, to);
            last = from;
        }

        @Override
        public void processResponse(Message message) {
            if (message instanceof MsgBlock mb) {
                byte[] bytes = mb.getBytes();
                Point p = pointOf(bytes);
                if (p != null)
                    resetPoints(p, to); // a re-request after a reconnect starts at the last block received
                put(queue, bytes);
            } else {
                super.processResponse(message);
            }
        }
    }

    /** The point of a Shelley-or-later block, or null for Byron. */
    static Point pointOf(byte[] bytes) {
        try {
            CborSpan top = CborSpan.of(bytes);
            if (top.tag() == 24) top = top.embedded();
            if (top.get(0).asLong() <= 1) return null;
            CborSpan header = top.get(1).get(0);
            long slot = header.get(0).get(1).asLong();
            return new Point(slot, HexUtil.encodeHexString(Blake2bUtil.blake2bHash256(header.bytes())));
        } catch (Exception e) {
            return null;
        }
    }

    /** Chain-sync on a short-lived connection: the tip and, when asked, the first non-Byron block after the intersect. */
    static Point[] discover(String host, int port, long magic, Point intersect, boolean firstShelley) throws Exception {
        CompletableFuture<Point[]> result = new CompletableFuture<>();
        HandshakeAgent handshake = new HandshakeAgent(N2NVersionTableConstant.v4AndAbove(magic));
        ChainsyncAgent chainSync = new ChainsyncAgent(new Point[]{intersect});
        handshake.addListener(new HandshakeAgentListener() {
            @Override
            public void handshakeOk() {
                chainSync.sendNextMessage();
            }
        });
        chainSync.addListener(new ChainSyncAgentListener() {
            @Override
            public void intersactFound(Tip t, Point point) {
                if (!firstShelley) result.complete(new Point[]{point, t.getPoint()});
                else chainSync.sendNextMessage();
            }

            @Override
            public void intersactNotFound(Tip t) {
                result.completeExceptionally(new IllegalStateException("intersect not found: " + intersect + " tip " + t));
            }

            @Override
            public void rollbackward(Tip t, Point toPoint) {
                chainSync.sendNextMessage();
            }

            @Override
            public void rollforwardByronEra(Tip t, ByronBlockHead head) {
                chainSync.sendNextMessage();
            }

            @Override
            public void rollforwardByronEra(Tip t, ByronEbHead head) {
                chainSync.sendNextMessage();
            }

            @Override
            public void rollforward(Tip t, BlockHeader header) {
                result.complete(new Point[]{new Point(header.getHeaderBody().getSlot(),
                        header.getHeaderBody().getBlockHash()), t.getPoint()});
            }
        });
        TCPNodeClient client = new TCPNodeClient(host, port, handshake, chainSync);
        client.start();
        try {
            return result.get(15, TimeUnit.MINUTES);
        } finally {
            client.shutdown();
        }
    }

    static long blockNoOf(byte[] bytes) {
        CborSpan top = CborSpan.of(bytes);
        if (top.tag() == 24) top = top.embedded();
        return top.get(1).get(0).get(0).get(0).asLong();
    }

    static void put(BlockingQueue<byte[]> q, byte[] b) {
        try {
            q.put(b);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static long txs(State s) {
        return s.eras.values().stream().mapToLong(m -> m.getOrDefault("txs", 0L)).sum();
    }

    static double percent(State s, Point tip) {
        return s.lastSlot < 0 ? 0 : Math.round(1000.0 * s.lastSlot / tip.getSlot()) / 10.0;
    }

    static void writeStatus(Path dir, State s, Point tip, String status, double bps, double tps, int queued)
            throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(NETWORK, s.network);
        m.put("status", status);
        m.put(IDENTITY, s.identity);
        m.put("updated", Instant.now().toString());
        m.put("pid", ProcessHandle.current().pid());
        m.put("lastSlot", s.lastSlot);
        m.put("lastBlockNo", s.lastBlockNo);
        m.put("lastHash", s.lastHash);
        m.put("tipSlot", tip.getSlot());
        m.put("percentBySlot", percent(s, tip));
        m.put("processedBlocks", s.processedBlocks);
        m.put("txs", txs(s));
        m.put("recentBlocksPerSec", Math.round(bps));
        m.put("recentTxsPerSec", Math.round(tps));
        m.put("avgBlocksPerSec", s.elapsedMillis == 0 ? 0 : Math.round(s.processedBlocks * 1000.0 / s.elapsedMillis));
        m.put("queued", queued);
        m.put("issueCounts", s.issueCounts);
        State.JSON.writeValue(dir.resolve("status.json").toFile(), m);
    }

    static void writeSummary(Path dir, State s, Point tip, String outcome) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(NETWORK, s.network);
        m.put("outcome", outcome);
        m.put(IDENTITY, s.identity);
        m.put("finished", Instant.now().toString());
        m.put("tipAtStart", tip.toString());
        m.put("lastSlot", s.lastSlot);
        m.put("lastBlockNo", s.lastBlockNo);
        m.put("processedBlocks", s.processedBlocks);
        m.put("txs", txs(s));
        m.put("elapsedSeconds", s.elapsedMillis / 1000);
        m.put("blocksPerSec", s.elapsedMillis == 0 ? 0 : Math.round(s.processedBlocks * 1000.0 / s.elapsedMillis));
        m.put("txsPerSec", s.elapsedMillis == 0 ? 0 : Math.round(txs(s) * 1000.0 / s.elapsedMillis));
        m.put("issueCounts", s.issueCounts);
        m.put("maxima", s.maxima);
        m.put("eras", s.eras);
        State.JSON.writeValue(dir.resolve("summary.json").toFile(), m);
        Files.writeString(dir.resolve(SUMMARY_TXT), summaryText(dir, s, outcome));
    }

    private static final List<String> TABLE_KEYS = List.of("blocks", "txs", "invalid_txs", "deserialize_ok", "aux",
            "witness_native_scripts", "witness_plutus_scripts", "aux_native_scripts", "aux_plutus_scripts",
            "ref_native_scripts", "ref_plutus_scripts", "witness_datums", "inline_datums", "redeemers", "sdh_checked",
            "sdh_matched", "native_eval_witness_true");

    /** A readable summary: counters per era, issue counts and the first three examples of each issue kind. */
    static String summaryText(Path dir, State s, String outcome) throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("network ").append(s.network).append("  outcome ").append(outcome).append('\n');
        out.append("identity ").append(s.identity).append('\n');
        out.append("last slot ").append(s.lastSlot).append(" block ").append(s.lastBlockNo).append("  blocks ")
                .append(s.processedBlocks).append("  elapsed ").append(s.elapsedMillis / 1000).append(" s\n\n");
        out.append(pad("era", 9, false));
        TABLE_KEYS.forEach(k -> out.append(pad(k.substring(0, Math.min(14, k.length())), 15, true)));
        out.append('\n');
        s.eras.forEach((era, m) -> {
            out.append(pad(era, 9, false));
            TABLE_KEYS.forEach(k -> out.append(pad(String.valueOf(m.getOrDefault(k, 0L)), 15, true)));
            out.append('\n');
        });
        out.append("\nother counters:\n");
        s.eras.forEach((era, m) -> {
            Map<String, Long> extra = new LinkedHashMap<>(m);
            extra.keySet().removeAll(TABLE_KEYS);
            if (!extra.isEmpty()) out.append("  ").append(era).append(": ").append(extra).append('\n');
        });
        out.append("\nmaxima: ").append(s.maxima).append("\n\nissue counts:\n");
        s.issueCounts.forEach((k, v) -> out.append("  ").append(k).append(": ").append(v).append('\n'));
        out.append("\nfirst examples per check (issues.jsonl has detail and txCbor):\n");
        Map<String, List<Map<?, ?>>> first = new LinkedHashMap<>();
        Path issues = dir.resolve(ISSUES);
        if (Files.exists(issues)) {
            try (BufferedReader r = Files.newBufferedReader(issues)) {
                for (String line; (line = r.readLine()) != null; ) {
                    Map<?, ?> e = State.JSON.readValue(line, Map.class);
                    List<Map<?, ?>> l = first.computeIfAbsent(String.valueOf(e.get("check")), k -> new ArrayList<>());
                    if (l.size() < 3) l.add(e);
                }
            }
        }
        first.forEach((check, es) -> {
            out.append("  ").append(check).append('\n');
            for (Map<?, ?> e : es) {
                String detail = String.valueOf(e.get("detail"));
                out.append("    slot ").append(e.get("slot")).append(" block ").append(e.get("block"))
                        .append(" tx ").append(e.get("tx")).append('\n');
                out.append("      ").append(detail, 0, Math.min(400, detail.length())).append('\n');
            }
        });
        return out.toString();
    }

    private static String pad(String s, int width, boolean left) {
        String fill = " ".repeat(Math.max(0, width - s.length()));
        return left ? fill + s : s + fill;
    }
}
