package com.bloxbean.cardano.client.txflow.soak;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultChainDataSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.store.rdbms.RdbmsFlowExecutionStore;
import com.bloxbean.cardano.client.txflow.stream.EmitResult;
import com.bloxbean.cardano.client.txflow.stream.TxFlowStream;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemResult;
import com.bloxbean.cardano.client.txflow.stream.TxStreamStats;
import com.bloxbean.cardano.client.txflow.stream.TxWorkItem;

import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sustained TxStream submission, with fault injection and end-of-run reconciliation.
 *
 * <p>Load alone answers "does it work when nothing goes wrong", which is the least interesting
 * question. Chaos is opt-in but is where the value is: crash the process mid-flight, reorg the
 * chain underneath it, fail the owning instance over — then check the books still balance.
 *
 * <p>See {@link SoakReconciler} for what "balance" means, {@link SoakJournal} for how a run
 * survives its own crashes, and {@link FundingPlan} for the lane model.
 */
public final class TxStreamSoak {

    private static final String DEFAULT_MNEMONIC =
            "test test test test test test test test test test test test "
                    + "test test test test test test test test test test test sauce";

    private static final String DEVKIT_URL = "http://localhost:8080/api/v1/";
    private static final String DEVKIT_ADMIN_URL = "http://localhost:10000/local-cluster/api";

    private static final int LANE_ACCOUNT_BASE = 900;
    private static final int RECIPIENT_BASE = 1000;

    public static void main(String[] args) throws Exception {
        SoakOptions options = SoakOptions.parse(args);
        if (options.has("help")) {
            printHelp();
            return;
        }

        // ---- network: options win, then environment, then devnet -----------------
        String backendUrl = firstNonBlank(
                options.string("url", null), System.getenv("CARDANO_BF_URL"), DEVKIT_URL);
        String projectId = firstNonBlank(
                options.string("project-id", null), System.getenv("BF_PROJECT_ID"), "dummy-project-id");
        String adminUrl = options.string("admin-url", DEVKIT_ADMIN_URL);
        String mnemonic = firstNonBlank(
                options.string("mnemonic", null), System.getenv("SOAK_MNEMONIC"), DEFAULT_MNEMONIC);
        Network network = "mainnet".equalsIgnoreCase(options.string("network", "testnet"))
                ? Networks.mainnet() : Networks.testnet();

        Duration duration = options.duration("duration", "10m");
        double rate = options.decimal("rate", 2.0);
        int laneCount = Math.max(1, options.integer("lanes", 1));
        int recipientCount = Math.max(1, options.integer("recipients", 20));
        String plannerName = options.string("planner", "peritem");
        int windowSize = options.integer("window", 10);
        int maxInFlight = options.integer("max-in-flight", 8);
        int maxBuffer = options.integer("max-buffer", 1000);
        int instances = Math.max(1, options.integer("instances", 1));
        int sampleSeconds = options.integer("sample-interval", 30);
        int maxTxChecks = options.integer("max-tx-checks", 500);
        double topupAda = options.decimal("topup-ada", 100_000);
        boolean force = options.flag("force", false);
        Path dataDir = options.path("data", "./soak-data");
        String streamId = options.string("stream-id", "soak-payouts");

        // Replaying interrupted work is safe under every planner, because the durable store
        // registers an item id in accept() — BEFORE the item is buffered, planned or merged
        // into a batch. So an id the store already knows is refused outright (CONFLICT, never
        // re-planned), and an id it does not know was never batched and cannot be duplicated.
        // The batching hazard is real but lives elsewhere: resubmitting the same logical
        // payment under a DIFFERENT item id, which is a new claim and a genuine second payment.
        String onRestart = options.string("on-restart", "resubmit");

        Duration crashEvery = options.has("chaos-crash") ? options.duration("chaos-crash", "10m") : null;
        Duration rollbackEvery = options.has("chaos-rollback") ? options.duration("chaos-rollback", "15m") : null;
        Duration failoverEvery = options.has("chaos-failover") ? options.duration("chaos-failover", "10m") : null;

        Files.createDirectories(dataDir);
        String jdbcUrl = "jdbc:h2:file:" + dataDir.toAbsolutePath().resolve("txflow-soak")
                + ";AUTO_SERVER=TRUE";

        BackendService backend = new BFBackendService(backendUrl, projectId);
        DevKitAdmin devkit = new DevKitAdmin(adminUrl);

        // The DevKit faucet and rollback only make sense when the BACKEND is that devkit.
        // Asking only "is an admin API reachable" is wrong: a developer machine often has a
        // devkit running alongside a preprod run, and taking the devnet branch there would
        // top up preprod addresses on the local devnet chain — funding nothing, and reporting
        // a shortfall that topping up can never fix.
        boolean localBackend = backendUrl.contains("localhost") || backendUrl.contains("127.0.0.1");
        boolean adminReachable = devkit.isReachable();
        boolean devnet = localBackend && adminReachable;
        if (adminReachable && !localBackend) {
            System.out.println("note            : a DevKit admin API is reachable, but the backend is"
                    + " not local — treating this as a public network (no faucet, no rollback)");
        }

        FundingPlan funding = FundingPlan.create(network, mnemonic, laneCount, LANE_ACCOUNT_BASE);
        SoakJournal journal = new SoakJournal(dataDir);
        boolean resuming = journal.hasPriorRun();
        ChaosSchedule chaos = new ChaosSchedule(crashEvery, rollbackEvery, failoverEvery);

        banner(backendUrl, devnet, duration, rate, plannerName, laneCount, recipientCount,
                instances, chaos, dataDir, resuming);

        if (crashEvery != null && !resuming) {
            System.out.println("NOTE: crash chaos halts this JVM on purpose. Drive restarts with a loop:");
            System.out.println("      while :; do java -jar <jar> txstream <same args>; done");
            System.out.println();
        }
        if (rollbackEvery != null && !devnet) {
            System.out.println("NOTE: rollback chaos needs Yaci DevKit and will be skipped on this network.");
            System.out.println();
        }
        if (failoverEvery != null && instances < 2) {
            System.out.println("NOTE: failover chaos needs --instances=2 and will be skipped.");
            System.out.println();
        }

        // ---- funding preflight ---------------------------------------------------
        long plannedItems = (long) Math.ceil(rate * duration.getSeconds());
        BigInteger avgPayment = BigInteger.valueOf(1_225_000);           // matches the loop below
        BigInteger feeHeadroom = BigInteger.valueOf(400_000);
        FundingPlan.Preflight preflight = funding.preflight(backend, plannedItems, avgPayment, feeHeadroom);

        System.out.println("funding preflight (" + plannedItems + " planned items across "
                + laneCount + " lane(s)):");
        preflight.report().forEach(System.out::println);

        if (!preflight.sufficient()) {
            if (devnet) {
                System.out.println("  topping up short lanes from the DevKit faucet...");
                for (FundingPlan.Lane lane : funding.lanes()) {
                    devkit.topup(lane.address(), topupAda);
                }
                Thread.sleep(4_000);
                FundingPlan.Preflight after = funding.preflight(backend, plannedItems, avgPayment, feeHeadroom);
                after.report().forEach(System.out::println);
                if (!after.sufficient() && !force) {
                    System.out.println();
                    System.out.println("Still short after topping up. Raise --topup-ada, or pass --force to run anyway.");
                    System.exit(2);
                }
            } else {
                System.out.println();
                System.out.println("  INSUFFICIENT FUNDS — there is no faucet on this network.");
                System.out.println("  Fund these addresses before starting:");
                preflight.shortfalls().forEach(s -> System.out.println("    " + s));
                System.out.println();
                System.out.println("  Then re-run. Pass --force to start anyway (the run will stop when it runs dry).");
                if (!force) System.exit(2);
            }
        }
        System.out.println();

        // ---- baselines: captured once for the whole run, reused across restarts ---
        ExpectedLedger ledger = new ExpectedLedger();
        if (resuming) {
            journal.readBaselines().forEach(ledger::captureBaseline);
            System.out.println("resuming        : " + journal.readIntents().size()
                    + " prior intents, baselines reloaded");
        } else {
            Map<Integer, BigInteger> baselines = new java.util.LinkedHashMap<>();
            for (int i = 0; i < recipientCount; i++) {
                baselines.put(i, FundingPlan.balanceOf(backend, recipientAddress(network, mnemonic, i)));
            }
            baselines.forEach(ledger::captureBaseline);
            journal.writeBaselines(baselines);
            System.out.println("baselines       : captured for " + recipientCount + " recipients");
        }
        journal.openIntents();

        // ---- engine + streams ----------------------------------------------------
        ExecutorService engineExec = Executors.newFixedThreadPool(Math.max(8, laneCount * 2));
        ExecutorService streamExec = Executors.newFixedThreadPool(Math.max(8, laneCount * 2));
        ScheduledExecutorService maintenance = Executors.newScheduledThreadPool(2);

        DefaultSignerRegistry signers = new DefaultSignerRegistry();
        for (FundingPlan.Lane lane : funding.lanes()) {
            signers.addAccount(lane.ref(), lane.account());
        }

        FlowEngine engine = FlowEngine.builder(
                        new DefaultUtxoSupplier(backend.getUtxoService()),
                        new DefaultProtocolParamsSupplier(backend.getEpochService()),
                        new DefaultTransactionProcessor(backend.getTransactionService()),
                        new DefaultChainDataSupplier(backend))
                .executor(engineExec)
                .maintenanceExecutor(maintenance)
                .store(RdbmsFlowExecutionStore.builder().jdbcUrl(jdbcUrl).build())
                .signerRegistry(signers)
                .build();

        SoakReconciler reconciler = new SoakReconciler(backend, jdbcUrl, maxTxChecks);
        AtomicBoolean stopping = new AtomicBoolean();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopping.set(true)));

        int snapshotCadence = options.integer("snapshot-cadence", 15);
        if (devnet && rollbackEvery != null) {
            // A rollback rewinds to the LAST snapshot, so snapshot cadence IS reorg depth.
            // Snapshotting once at the start would make every rollback erase the whole run —
            // which is not a reorg, it is a chain reset, and it invalidates the whole soak.
            devkit.takeSnapshot();
            maintenance.scheduleAtFixedRate(devkit::takeSnapshot,
                    snapshotCadence, snapshotCadence, java.util.concurrent.TimeUnit.SECONDS);
            System.out.println("rollback chaos  : snapshot every " + snapshotCadence
                    + "s, so each rewind is at most that deep");
        }

        int exitCode;
        AtomicLong submitted = new AtomicLong();
        try (StreamCluster cluster = new StreamCluster(streamId, engine, jdbcUrl, funding.lanes(),
                streamExec, maintenance, plannerName, windowSize, maxInFlight, maxBuffer,
                instances > 1);
             SoakMetrics metrics = new SoakMetrics(dataDir.resolve("samples.csv"),
                     (elapsed, heap, threads) -> {
                         TxFlowStream s = cluster.active();
                         TxStreamStats st = s == null ? null : s.getStats();
                         return new SoakMetrics.Sample(elapsed, heap, threads,
                                 st == null ? 0 : st.acceptedItemCount(),
                                 st == null ? 0 : st.confirmedItemCount(),
                                 st == null ? 0 : st.failedItemCount(),
                                 st == null ? 0 : st.inFlightCount(),
                                 reconciler.totalStoreRows());
                     })) {

            cluster.startInstances(instances);
            metrics.start(sampleSeconds);
            System.out.println("stream started  : " + streamId + " (" + instances + " instance(s))");

            if (resuming) {
                replayInterrupted(cluster, journal, funding, ledger, network, mnemonic,
                        onRestart, plannerName);
            }
            System.out.println();

            submitLoop(cluster, ledger, journal, funding, backend, devkit, devnet, network,
                    mnemonic, recipientCount, duration, rate, topupAda, chaos, stopping,
                    submitted, dataDir);

            System.out.println();
            System.out.println("draining...");
            cluster.drain(Duration.ofMinutes(30));

            // ---- reconcile the WHOLE run, from the journal ----------------------
            System.out.println("reconciling against the chain...");
            ExpectedLedger full = rebuildFromJournal(journal, cluster.active());
            SoakReconciler.Report report = reconciler.reconcile(full,
                    i -> recipientAddress(network, mnemonic, i));

            String rendered = render(report, metrics, duration, chaos, laneCount);
            System.out.println(rendered);
            Files.writeString(dataDir.resolve("report.txt"), rendered);
            System.out.println("report written  : " + dataDir.resolve("report.txt"));
            System.out.println("samples written : " + dataDir.resolve("samples.csv"));

            exitCode = report.isClean() ? 0 : 1;
        } finally {
            journal.close();
            engineExec.shutdown();
            streamExec.shutdown();
            maintenance.shutdown();
        }
        System.exit(exitCode);
    }

    // ------------------------------------------------------------------ submission

    private static void submitLoop(StreamCluster cluster, ExpectedLedger ledger,
                                   SoakJournal journal, FundingPlan funding,
                                   BackendService backend, DevKitAdmin devkit, boolean devnet,
                                   Network network, String mnemonic, int recipientCount,
                                   Duration duration, double rate, double topupAda,
                                   ChaosSchedule chaos, AtomicBoolean stopping,
                                   AtomicLong submittedCounter, Path dataDir) throws Exception {
        long intervalNanos = (long) (1_000_000_000L / Math.max(rate, 0.0001));
        Instant startedAt = Instant.now();
        Instant deadline = startedAt.plus(duration);
        long seq = journal.highestSequence();
        long backpressure = 0;
        long nextProgress = System.currentTimeMillis();
        long nextFunding = System.currentTimeMillis() + 60_000;

        while (Instant.now().isBefore(deadline) && !stopping.get()) {
            long tickStart = System.nanoTime();
            seq++;

            int recipientIndex = (int) Math.floorMod(seq, recipientCount);
            FundingPlan.Lane lane = funding.laneFor(seq);
            BigInteger lovelace = BigInteger.valueOf(1_200_000 + (seq % 50) * 1_000);
            String orderId = "SOAK-" + seq;

            // Journal BEFORE submitting: an intent we failed to record is indistinguishable
            // from work the stream lost.
            journal.recordIntent(new SoakJournal.Intent(orderId, recipientIndex, lovelace, lane.index()));
            ledger.recordSubmitted(orderId, recipientIndex, lovelace);
            submittedCounter.incrementAndGet();

            TxPlan plan = TxPlan.from(new Tx()
                            .payToAddress(recipientAddress(network, mnemonic, recipientIndex),
                                    Amount.lovelace(lovelace))
                            .fromRef(lane.ref()))
                    .withSigner(lane.ref());

            TxWorkItem.Builder item = TxWorkItem.builder(orderId)
                    .withTxPlan(plan)
                    .withIdempotencyKey(orderId);
            if (funding.size() > 1) {
                item = item.withLane(lane.name());
            }

            TxFlowStream stream = cluster.active();
            EmitResult emit = stream == null ? null : stream.trySubmit(item.build());

            if (emit == null) {
                ledger.recordRejected(orderId, "no active stream instance");
            } else {
                switch (emit.getStatus()) {
                    case OK:
                    case DUPLICATE_ATTACHED:
                        emit.getReceipt().completion().toCompletableFuture()
                                .thenAccept(r -> ledger.recordOutcome(orderId, r));
                        break;
                    case FULL:
                    case PAUSED:
                        backpressure++;
                        ledger.recordRejected(orderId, "not accepted: " + emit.getStatus());
                        Thread.sleep(500);
                        break;
                    default:
                        ledger.recordRejected(orderId, "not accepted: " + emit.getStatus());
                        break;
                }
            }

            // ---- chaos ---------------------------------------------------------
            ChaosSchedule.Fault fault = chaos.due();
            if (fault != null) {
                applyFault(fault, cluster, devkit, devnet, startedAt, dataDir);
            }

            // ---- progress ------------------------------------------------------
            if (System.currentTimeMillis() >= nextProgress) {
                TxFlowStream s = cluster.active();
                TxStreamStats st = s == null ? null : s.getStats();
                System.out.printf("  t+%-6s submitted=%-7d confirmed=%-7d failed=%-4d inflight=%-3d "
                                + "backpressure=%-5d heap=%.0fMB%n",
                        humanize(Duration.between(startedAt, Instant.now())),
                        submittedCounter.get(),
                        st == null ? 0 : st.confirmedItemCount(),
                        st == null ? 0 : st.failedItemCount(),
                        st == null ? 0 : st.inFlightCount(),
                        backpressure, SoakMetrics.heapAfterGc() / 1024.0 / 1024.0);
                nextProgress = System.currentTimeMillis() + 30_000;
            }

            // ---- keep the lanes funded (devnet only) ---------------------------
            if (devnet && System.currentTimeMillis() >= nextFunding) {
                for (FundingPlan.Lane l : funding.lanes()) {
                    if (FundingPlan.balanceOf(backend, l.address())
                            .compareTo(BigInteger.valueOf(2_000_000_000L)) < 0) {
                        System.out.println("  [funding] " + l.name() + " low, topping up");
                        devkit.topup(l.address(), topupAda);
                    }
                }
                nextFunding = System.currentTimeMillis() + 60_000;
            }

            long remaining = intervalNanos - (System.nanoTime() - tickStart);
            if (remaining > 0) {
                Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
            }
        }
        if (stopping.get()) {
            System.out.println();
            System.out.println("shutdown requested — stopping submission and draining");
        }
    }

    private static void applyFault(ChaosSchedule.Fault fault, StreamCluster cluster,
                                   DevKitAdmin devkit, boolean devnet, Instant startedAt,
                                   Path dataDir) throws Exception {
        switch (fault) {
            case ROLLBACK:
                if (!devnet) return;
                System.out.println();
                System.out.println("  [chaos] ROLLBACK — rewinding the chain under the stream");
                devkit.rollbackToSnapshot();
                Thread.sleep(15_000);              // the node restarts on rollback
                System.out.println("  [chaos] chain rewound; run continues");
                System.out.println();
                break;

            case FAILOVER:
                System.out.println();
                System.out.println("  [chaos] FAILOVER — aborting the active instance");
                System.out.println("  [chaos] " + cluster.failover());
                System.out.println();
                break;

            case CRASH:
                System.out.println();
                System.out.println("  [chaos] CRASH — Runtime.halt() after "
                        + humanize(Duration.between(startedAt, Instant.now())));
                System.out.println("  [chaos] no drain, no hooks. Restart with the same --data to resume.");
                System.out.flush();
                Files.writeString(dataDir.resolve("last-crash.txt"),
                        "crashed at " + Instant.now() + "\n");
                Runtime.getRuntime().halt(9);
                break;
            default:
                break;
        }
    }

    /**
     * Re-submit work the previous process journalled but never got into the store.
     *
     * <p>This is what a real system does after a crash, and it exercises the redelivery path:
     * the order id is unchanged, so the durable store either refuses it (already registered,
     * therefore already planned) or accepts it as work that never happened.
     *
     * <p>Safe under every planner. The store registers an item id in {@code accept()}, before
     * the item is buffered or merged into a batch, so replaying can never introduce a member
     * into a differently-composed batch. That hazard needs a <em>different</em> item id for the
     * same payment — which this deliberately never does.
     */
    private static void replayInterrupted(StreamCluster cluster, SoakJournal journal,
                                          FundingPlan funding, ExpectedLedger ledger,
                                          Network network, String mnemonic,
                                          String onRestart, String plannerName) {
        TxFlowStream stream = cluster.active();
        if (stream == null) return;

        List<SoakJournal.Intent> orphaned = new java.util.ArrayList<>();
        for (SoakJournal.Intent intent : journal.readIntents()) {
            try {
                if (stream.getItemStatus(intent.orderId()).isEmpty()) orphaned.add(intent);
            } catch (Exception ignored) {
                orphaned.add(intent);
            }
        }
        if (orphaned.isEmpty()) {
            System.out.println("restart         : no interrupted work found");
            return;
        }

        if (!"resubmit".equalsIgnoreCase(onRestart)) {
            System.out.println("restart         : " + orphaned.size()
                    + " interrupted item(s) skipped (--on-restart=skip)");
            return;
        }

        int replayed = 0, attached = 0, alreadyKnown = 0, rejected = 0;
        for (SoakJournal.Intent intent : orphaned) {
            FundingPlan.Lane lane = funding.lanes().get(
                    Math.floorMod(intent.lane(), funding.size()));
            TxPlan plan = TxPlan.from(new Tx()
                            .payToAddress(recipientAddress(network, mnemonic, intent.recipient()),
                                    Amount.lovelace(intent.lovelace()))
                            .fromRef(lane.ref()))
                    .withSigner(lane.ref());

            TxWorkItem.Builder item = TxWorkItem.builder(intent.orderId())
                    .withTxPlan(plan)
                    .withIdempotencyKey(intent.orderId());   // unchanged: that is the point
            if (funding.size() > 1) item = item.withLane(lane.name());

            EmitResult emit = stream.trySubmit(item.build());
            switch (emit.getStatus()) {
                case OK:
                    replayed++;
                    emit.getReceipt().completion().toCompletableFuture()
                            .thenAccept(r -> ledger.recordOutcome(intent.orderId(), r));
                    break;
                case DUPLICATE_ATTACHED:
                    attached++;
                    emit.getReceipt().completion().toCompletableFuture()
                            .thenAccept(r -> ledger.recordOutcome(intent.orderId(), r));
                    break;
                case CONFLICT:
                    // The store already registered this id, so it was planned before the crash.
                    // Refusing it here is the uniqueness guard working, not an error.
                    alreadyKnown++;
                    break;
                default:
                    rejected++;
                    break;
            }
        }
        System.out.println("restart         : of " + orphaned.size() + " interrupted item(s) — replayed "
                + replayed + ", attached " + attached + ", already known to the store "
                + alreadyKnown + ", rejected " + rejected);
    }

    // ------------------------------------------------------------------ reconcile

    /**
     * Rebuild the run's expectations from the journal and ask the durable store what became of
     * each one. Deliberately ignores in-memory state: after a crash there is none, and making
     * the store answer is itself part of the test.
     */
    private static ExpectedLedger rebuildFromJournal(SoakJournal journal, TxFlowStream stream) {
        ExpectedLedger full = new ExpectedLedger();
        journal.readBaselines().forEach(full::captureBaseline);
        for (SoakJournal.Intent intent : journal.readIntents()) {
            full.recordSubmitted(intent.orderId(), intent.recipient(), intent.lovelace());
            if (stream == null) continue;
            try {
                Optional<TxStreamItemResult> status = stream.getItemStatus(intent.orderId());
                status.ifPresent(r -> full.recordOutcome(intent.orderId(), r));
            } catch (Exception ignored) {
                // leave it non-terminal; the reconciler will report it
            }
        }
        return full;
    }

    // ------------------------------------------------------------------ rendering

    private static String render(SoakReconciler.Report r, SoakMetrics metrics, Duration planned,
                                 ChaosSchedule chaos, int laneCount) {
        StringBuilder out = new StringBuilder();
        String line = "=".repeat(78);
        double[] thirds = metrics.throughputFirstVsLastThird();

        out.append('\n').append(line).append('\n');
        out.append("  SOAK RECONCILIATION\n");
        out.append(line).append('\n');
        out.append(String.format("  planned duration     %s%n", humanize(planned)));
        out.append(String.format("  lanes                %d%n", laneCount));
        out.append(String.format("  chaos                %s%n", chaos.describe()));
        if (chaos.isEnabled()) {
            out.append(String.format("  rollbacks fired      %d%n", chaos.rollbacksFired()));
            out.append(String.format("  failovers fired      %d%n", chaos.failoversFired()));
        }
        out.append('\n');
        out.append(String.format("  submitted (journal)  %d%n", r.submitted()));
        out.append(String.format("  confirmed            %d%n", r.confirmed()));
        out.append(String.format("  failed               %d%n", r.failed()));
        out.append(String.format("  cancelled            %d%n", r.cancelled()));
        out.append(String.format("  recovery required    %d%n", r.recoveryRequired()));
        out.append(String.format("  non-terminal         %d%n", r.nonTerminal()));
        out.append(String.format("  never registered     %d   %s%n", r.neverRegistered(),
                r.neverRegistered() == 0 ? ""
                        : "(journalled, never reached the store — crash casualties)"));
        out.append('\n');
        out.append("  -- value conservation (the double-pay check) --\n");
        out.append(String.format("  expected total       %s lovelace%n", r.expectedTotalLovelace()));
        out.append(String.format("  actual total         %s lovelace%n", r.actualTotalLovelace()));
        out.append(String.format("  transactions checked %d on chain%n", r.transactionsChecked()));
        out.append('\n');
        out.append("  -- resources --\n");
        double slope = metrics.heapSlopeMbPerHour();
        out.append(String.format("  heap trend           %s%n", Double.isNaN(slope)
                ? "run too short to trend (needs 15m+)"
                : String.format("%+.1f MB/hour (post-GC)", slope)));
        out.append(String.format("  throughput           %.1f/min  (first third %.1f -> last third %.1f)%n",
                metrics.confirmationsPerMinute(), thirds[0], thirds[1]));
        out.append(String.format("  orphan leases        %d%n", r.orphanResourceLeases()));
        for (Map.Entry<String, Long> e : r.storeRows().entrySet()) {
            out.append(String.format("    %-34s %8d rows%n", e.getKey(), e.getValue()));
        }
        out.append('\n');

        appendFindings(out, "LOST / NOT ON CHAIN", r.missingOnChain());
        appendFindings(out, "DOUBLE PAID", r.doublePaid());
        appendFindings(out, "UNDER PAID", r.underPaid());

        out.append(line).append('\n');
        out.append(r.isClean()
                ? "  RESULT: CLEAN — nothing lost, nothing paid twice, nothing left behind\n"
                : "  RESULT: DISCREPANCIES FOUND — see above\n");
        out.append(line).append('\n');
        return out.toString();
    }

    private static void appendFindings(StringBuilder out, String title, List<String> findings) {
        if (findings.isEmpty()) {
            out.append(String.format("  %-22s none%n", title));
            return;
        }
        out.append(String.format("  %s (%d):%n", title, findings.size()));
        findings.stream().limit(20).forEach(f -> out.append("    - ").append(f).append('\n'));
        if (findings.size() > 20) {
            out.append(String.format("    ... and %d more%n", findings.size() - 20));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String recipientAddress(Network network, String mnemonic, int index) {
        return new com.bloxbean.cardano.client.account.Account(
                network, mnemonic, RECIPIENT_BASE + index).baseAddress();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String humanize(Duration d) {
        long s = Math.abs(d.getSeconds());
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m";
        return String.format("%dh%02dm", s / 3600, (s % 3600) / 60);
    }

    private static void banner(String url, boolean devnet, Duration duration, double rate,
                               String planner, int lanes, int recipients, int instances,
                               ChaosSchedule chaos, Path data, boolean resuming) {
        System.out.println("=".repeat(78));
        System.out.println("  TxStream soak" + (resuming ? "  (RESUMING a prior run)" : ""));
        System.out.println("=".repeat(78));
        System.out.println("backend         : " + url);
        System.out.println("network type    : " + (devnet ? "Yaci DevKit (faucet + rollback available)"
                : "public / no admin api (no faucet, no rollback)"));
        System.out.println("duration        : " + humanize(duration));
        System.out.println("target rate     : " + rate + " items/sec");
        System.out.println("planner         : " + planner);
        System.out.println("lanes           : " + lanes + " (one funding account each)");
        System.out.println("recipients      : " + recipients);
        System.out.println("instances       : " + instances);
        System.out.println("chaos           : " + chaos.describe());
        System.out.println("data dir        : " + data.toAbsolutePath());
        System.out.println("=".repeat(78));
        System.out.println();
    }

    private static void printHelp() {
        PrintWriter out = new PrintWriter(System.out, true);
        out.println("txstream — sustained TxStream submission with chaos and reconciliation");
        out.println();
        out.println("LOAD");
        out.println("  --duration=10m         run length: 90s, 30m, 12h, 2d (bare number = minutes)");
        out.println("  --rate=2               target submissions per second");
        out.println("  --lanes=1              funding accounts, one lane each. Concurrency comes");
        out.println("                         from lanes, not threads — 1 lane is ~1 tx/block.");
        out.println("  --recipients=20        size of the recipient pool");
        out.println("  --planner=perItem      perItem | perWindow | batching");
        out.println("  --window=10            window size for perWindow / batching");
        out.println("  --max-in-flight=8      concurrent executions cap");
        out.println("  --max-buffer=1000      stream buffer before backpressure");
        out.println("  --instances=1          stream instances sharing the stream id (2 = active/standby)");
        out.println("  --on-restart=resubmit  resubmit | skip   what to do with work a crash interrupted.");
        out.println("                         resubmit replays under the SAME order id. Safe under any");
        out.println("                         planner: the durable store registers an item id before it");
        out.println("                         is ever planned, so an id it already knows is refused");
        out.println("                         (CONFLICT) and one it does not know was never batched.");
        out.println();
        out.println("CHAOS (all off by default)");
        out.println("  --chaos-crash=10m      Runtime.halt() mid-flight. Restart with the same --data");
        out.println("                         to resume; drive it with:  while :; do java -jar ... ; done");
        out.println("  --chaos-rollback=15m   rewind the chain under the stream (Yaci DevKit only)");
        out.println("  --chaos-failover=10m   abort the active instance, standby takes over (--instances=2)");
        out.println();
        out.println("NETWORK  (defaults to a local Yaci DevKit)");
        out.println("  --url=...              backend url        [env CARDANO_BF_URL]");
        out.println("  --project-id=...       Blockfrost project [env BF_PROJECT_ID]");
        out.println("  --mnemonic=\"...\"       funding mnemonic   [env SOAK_MNEMONIC]");
        out.println("  --network=testnet      testnet | mainnet");
        out.println("  --admin-url=...        DevKit admin api (faucet + rollback)");
        out.println();
        out.println("  On a public network there is no faucet, so the run checks each lane's balance");
        out.println("  first and tells you exactly which address needs how much before starting.");
        out.println();
        out.println("  Example (preprod):");
        out.println("    export CARDANO_BF_URL=https://cardano-preprod.blockfrost.io/api/v0/");
        out.println("    export BF_PROJECT_ID=preprod...");
        out.println("    export SOAK_MNEMONIC=\"your funded test mnemonic ...\"");
        out.println("    java -jar cardano-client-txflow-soak.jar txstream --duration=2h --rate=0.5 --lanes=2");
        out.println();
        out.println("OTHER");
        out.println("  --data=./soak-data     H2 store, journal, samples.csv and report.txt");
        out.println("  --sample-interval=30   seconds between resource samples");
        out.println("  --max-tx-checks=500    cap on per-transaction on-chain verification");
        out.println("  --topup-ada=100000     faucet top-up size (devnet only)");
        out.println("  --snapshot-cadence=15  seconds between devnet snapshots = max reorg depth");
        out.println("  --force                start even if the funding preflight is short");
        out.println();
        out.println("Exit codes: 0 clean, 1 discrepancies found, 2 insufficient funding.");
        out.flush();
    }
}
