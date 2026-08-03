package com.bloxbean.cardano.client.txflow.soak;

import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.store.rdbms.RdbmsTxStreamStateStore;
import com.bloxbean.cardano.client.txflow.stream.LanePolicy;
import com.bloxbean.cardano.client.txflow.stream.ResolvedLane;
import com.bloxbean.cardano.client.txflow.stream.TxFlowStream;
import com.bloxbean.cardano.client.txflow.stream.TxStreamPlanner;
import com.bloxbean.cardano.client.txflow.stream.WindowPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One or more {@link TxFlowStream} instances sharing a stream id.
 *
 * <p>With a single instance this is just a holder. With two it models the active/standby
 * deployment: both share the durable store and stream id, exactly one holds the epoch-fenced
 * ownership lease and dispatches, and failover chaos aborts the active one so the standby has to
 * take over. A replacement standby is then started, the way a restarted pod would.
 *
 * <p>Submissions always go to whichever instance currently reports itself active — routing to a
 * standby would just collect {@code PAUSED} results, which tests nothing.
 */
public final class StreamCluster implements AutoCloseable {

    private final String streamId;
    private final FlowEngine engine;
    private final String jdbcUrl;
    private final List<FundingPlan.Lane> lanes;
    private final Executor streamExecutor;
    private final ScheduledExecutorService maintenance;
    private final String plannerName;
    private final int windowSize;
    private final int maxInFlight;
    private final int maxBuffer;
    private final boolean ownershipEnabled;

    private final List<TxFlowStream> instances = new ArrayList<>();
    private final Map<TxFlowStream, String> tokens = new ConcurrentHashMap<>();
    private final AtomicInteger tokenSeq = new AtomicInteger();

    public StreamCluster(String streamId, FlowEngine engine, String jdbcUrl,
                         List<FundingPlan.Lane> lanes, Executor streamExecutor,
                         ScheduledExecutorService maintenance, String plannerName,
                         int windowSize, int maxInFlight, int maxBuffer, boolean ownershipEnabled) {
        this.streamId = streamId;
        this.engine = engine;
        this.jdbcUrl = jdbcUrl;
        this.lanes = lanes;
        this.streamExecutor = streamExecutor;
        this.maintenance = maintenance;
        this.plannerName = plannerName;
        this.windowSize = windowSize;
        this.maxInFlight = maxInFlight;
        this.maxBuffer = maxBuffer;
        this.ownershipEnabled = ownershipEnabled;
    }

    public void startInstances(int count) {
        for (int i = 0; i < count; i++) {
            TxFlowStream instance = build();
            instance.start();
            instances.add(instance);
        }
    }

    private TxFlowStream build() {
        TxFlowStream.Builder builder = TxFlowStream.builder(streamId, engine)
                .stateStore(RdbmsTxStreamStateStore.builder().jdbcUrl(jdbcUrl).build())
                .executor(streamExecutor)
                .maintenanceExecutor(maintenance)
                .reconciliationInterval(Duration.ofSeconds(30))
                .maxInFlight(maxInFlight)
                .maxBufferSize(maxBuffer);

        if (lanes.size() == 1) {
            FundingPlan.Lane only = lanes.get(0);
            builder = builder.lane(ResolvedLane.ofFundingRef(only.name(), only.ref()));
        } else {
            // Each item names its lane; the resolver maps that name to its funding account.
            Map<String, ResolvedLane> byName = new ConcurrentHashMap<>();
            for (FundingPlan.Lane lane : lanes) {
                byName.put(lane.name(), ResolvedLane.ofFundingRef(lane.name(), lane.ref()));
            }
            builder = builder.lanes(LanePolicy.explicit()).laneResolver(byName::get);
        }

        if ("batching".equalsIgnoreCase(plannerName)) {
            builder = builder.planner(TxStreamPlanner.batching()).window(WindowPolicy.count(windowSize));
        } else if ("perwindow".equalsIgnoreCase(plannerName)) {
            builder = builder.planner(TxStreamPlanner.perWindow()).window(WindowPolicy.count(windowSize));
        } else {
            builder = builder.planner(TxStreamPlanner.perItem());
        }

        if (ownershipEnabled) {
            String token = "soak-instance-" + tokenSeq.incrementAndGet();
            builder = builder.ownership(token, Duration.ofSeconds(30));
        }
        return builder.build();
    }

    /** The instance currently holding the ownership lease, or the only instance. */
    public TxFlowStream active() {
        if (instances.size() == 1) return instances.get(0);
        for (TxFlowStream instance : instances) {
            try {
                if (instance.ownership().isActive()) return instance;
            } catch (Exception ignored) {
                // an instance mid-transition simply is not the one to submit through
            }
        }
        return instances.isEmpty() ? null : instances.get(0);
    }

    /**
     * Abort the active instance and let a standby take the lease, then start a replacement
     * standby so the next failover has somewhere to go.
     *
     * @return a short description of what happened, for the run log
     */
    public String failover() {
        if (instances.size() < 2) return "failover skipped (needs --instances=2)";
        TxFlowStream victim = active();
        if (victim == null) return "failover skipped (no active instance)";

        victim.abort("soak failover chaos");
        instances.remove(victim);

        // Give the fenced lease time to lapse and a standby to pick it up.
        String outcome = "no standby became active";
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            TxFlowStream candidate = active();
            if (candidate != null) {
                try {
                    if (candidate.ownership().isActive()) {
                        outcome = "standby took over";
                        break;
                    }
                } catch (Exception ignored) {
                    // keep waiting
                }
            }
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        TxFlowStream replacement = build();
        replacement.start();
        instances.add(replacement);
        return outcome + "; replacement standby started";
    }

    public int size() {
        return instances.size();
    }

    public void drain(Duration timeout) {
        for (TxFlowStream instance : instances) {
            try {
                instance.awaitDrain(timeout);
            } catch (Exception e) {
                System.err.println("[cluster] drain: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        for (TxFlowStream instance : instances) {
            try {
                instance.close(Duration.ofMinutes(2));
            } catch (Exception ignored) {
                // closing on the way out must never mask the run's real outcome
            }
        }
    }
}
