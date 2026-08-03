package com.bloxbean.cardano.client.txflow.soak;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Periodic resource sampling — the part of a soak that a short run cannot tell you.
 *
 * <p>Correctness is provable in minutes; leaks, unbounded store growth and throughput decay are
 * only visible over hours. Each sample is appended to a CSV so a run can be analysed after the
 * fact, and a coarse trend is computed at the end.
 *
 * <p><b>Heap is sampled after collection, not live.</b> Live heap usage sawtooths with
 * allocation and says nothing about retention; the old generation's <em>collection usage</em>
 * is what actually grows when something is being retained. That is the number worth trending.
 *
 * <p>Even so, treat the trend as a hint. The sharp instrument is running with a constrained
 * {@code -Xmx}: a genuine leak becomes an OutOfMemoryError, which is unambiguous, while a slope
 * on a graph is an argument.
 */
public final class SoakMetrics implements AutoCloseable {

    /** One point in time. */
    public record Sample(long elapsedSeconds, long heapAfterGcBytes, int threads,
                         long accepted, long confirmed, long failed, int inFlight,
                         long storeRows) {
    }

    /** Values the runner supplies each tick; kept as a lambda so metrics stay decoupled. */
    @FunctionalInterface
    public interface Gauges {
        Sample sample(long elapsedSeconds, long heapAfterGcBytes, int threads);
    }

    private final List<Sample> samples = new ArrayList<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "soak-metrics");
                t.setDaemon(true);
                return t;
            });
    private final Path csv;
    private final Gauges gauges;
    private final long startNanos = System.nanoTime();
    private PrintWriter writer;

    public SoakMetrics(Path csv, Gauges gauges) {
        this.csv = csv;
        this.gauges = gauges;
    }

    public void start(long intervalSeconds) {
        try {
            Files.createDirectories(csv.toAbsolutePath().getParent());
            writer = new PrintWriter(Files.newBufferedWriter(csv));
            writer.println("elapsed_s,heap_after_gc_mb,threads,accepted,confirmed,failed,in_flight,store_rows");
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open metrics file " + csv, e);
        }
        scheduler.scheduleAtFixedRate(this::tick, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    private void tick() {
        try {
            long elapsed = (System.nanoTime() - startNanos) / 1_000_000_000L;
            Sample s = gauges.sample(elapsed, heapAfterGc(), Thread.activeCount());
            synchronized (samples) {
                samples.add(s);
                writer.printf("%d,%.1f,%d,%d,%d,%d,%d,%d%n",
                        s.elapsedSeconds(), s.heapAfterGcBytes() / 1024.0 / 1024.0, s.threads(),
                        s.accepted(), s.confirmed(), s.failed(), s.inFlight(), s.storeRows());
                writer.flush();
            }
        } catch (Exception e) {
            // A sampling failure must never take the soak down.
            System.err.println("[metrics] sample failed: " + e);
        }
    }

    /**
     * Old-generation usage as of the last collection. Falls back to total heap usage when no
     * such pool is exposed (some collectors do not report collection usage).
     */
    public static long heapAfterGc() {
        long best = -1;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) continue;
            String name = pool.getName().toLowerCase();
            if (!(name.contains("old") || name.contains("tenured"))) continue;
            MemoryUsage usage = pool.getCollectionUsage();
            // Zero means no collection has happened yet, not an empty heap.
            if (usage != null && usage.getUsed() > 0) best = Math.max(best, usage.getUsed());
        }
        if (best > 0) return best;
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    public List<Sample> samples() {
        synchronized (samples) {
            return List.copyOf(samples);
        }
    }

    /**
     * Least-squares slope of post-GC heap, in MB per hour. Near zero is what a healthy run
     * looks like; a clear positive slope sustained over hours is worth investigating.
     */
    public double heapSlopeMbPerHour() {
        List<Sample> data = samples();
        // Extrapolating MB/hour from a couple of minutes produces confident nonsense.
        if (data.size() < 3 || !hasEnoughSpanForTrend()) return Double.NaN;
        double n = data.size(), sx = 0, sy = 0, sxy = 0, sxx = 0;
        for (Sample s : data) {
            double x = s.elapsedSeconds() / 3600.0;
            double y = s.heapAfterGcBytes() / 1024.0 / 1024.0;
            sx += x; sy += y; sxy += x * y; sxx += x * x;
        }
        double denom = n * sxx - sx * sx;
        return denom == 0 ? 0.0 : (n * sxy - sx * sy) / denom;
    }

    /** A heap trend is only meaningful once the run has covered a real span of time. */
    public boolean hasEnoughSpanForTrend() {
        List<Sample> data = samples();
        if (data.size() < 6) return false;
        long span = data.get(data.size() - 1).elapsedSeconds() - data.get(0).elapsedSeconds();
        return span >= 900;      // 15 minutes
    }

    /** Throughput over the run, in confirmations per minute. */
    public double confirmationsPerMinute() {
        List<Sample> data = samples();
        if (data.size() < 2) return 0.0;
        Sample first = data.get(0), last = data.get(data.size() - 1);
        double minutes = (last.elapsedSeconds() - first.elapsedSeconds()) / 60.0;
        return minutes <= 0 ? 0.0 : (last.confirmed() - first.confirmed()) / minutes;
    }

    /**
     * Confirmations per minute in the first vs last third of the run. A large drop is the
     * signature of degradation under accumulated state.
     */
    public double[] throughputFirstVsLastThird() {
        List<Sample> data = samples();
        if (data.size() < 6) return new double[]{0.0, 0.0};
        int third = data.size() / 3;
        return new double[]{
                rate(data.get(0), data.get(third)),
                rate(data.get(data.size() - 1 - third), data.get(data.size() - 1))
        };
    }

    private static double rate(Sample from, Sample to) {
        double minutes = (to.elapsedSeconds() - from.elapsedSeconds()) / 60.0;
        return minutes <= 0 ? 0.0 : (to.confirmed() - from.confirmed()) / minutes;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        if (writer != null) writer.close();
    }
}
