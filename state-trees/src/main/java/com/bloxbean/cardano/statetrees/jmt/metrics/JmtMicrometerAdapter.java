package com.bloxbean.cardano.statetrees.jmt.metrics;

import com.bloxbean.cardano.statetrees.jmt.JmtMetrics;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Optional Micrometer bridge for {@link JmtMetrics} without adding a compile-time dependency.
 *
 * Usage:
 *   Object registry = io.micrometer.core.instrument.Metrics.globalRegistry();
 *   JmtMetrics metrics = JmtMicrometerAdapter.create(registry, "jmt");
 *   JellyfishMerkleTreeStoreConfig cfg = JellyfishMerkleTreeStoreConfig.builder().metrics(metrics).build();
 */
public final class JmtMicrometerAdapter implements JmtMetrics {

    private final Object registry;
    private final String prefix;

    private final Method counterMethod;
    private final Method timerMethod;
    private final Method counterIncrementMethod;
    private final Method timerRecordMethod;

    private final Object commitTimer;
    private final Object pruneTimer;

    private final Object nodeCacheHit;
    private final Object nodeCacheMiss;
    private final Object valueCacheHit;
    private final Object valueCacheMiss;

    private final Object commitCount;
    private final Object commitPuts;
    private final Object commitDeletes;
    private final Object pruneCount;
    private final Object pruneNodes;
    private final Object pruneCacheEvicted;

    private JmtMicrometerAdapter(Object registry,
                                 String prefix,
                                 Method counterMethod,
                                 Method timerMethod,
                                 Method counterIncrementMethod,
                                 Method timerRecordMethod) throws Exception {
        this.registry = registry;
        this.prefix = prefix == null || prefix.isEmpty() ? "jmt" : prefix;
        this.counterMethod = counterMethod;
        this.timerMethod = timerMethod;
        this.counterIncrementMethod = counterIncrementMethod;
        this.timerRecordMethod = timerRecordMethod;

        this.commitTimer = timer(name("commit.latency"));
        this.pruneTimer = timer(name("prune.latency"));

        this.nodeCacheHit = counter(name("node.cache.hit"));
        this.nodeCacheMiss = counter(name("node.cache.miss"));
        this.valueCacheHit = counter(name("value.cache.hit"));
        this.valueCacheMiss = counter(name("value.cache.miss"));

        this.commitCount = counter(name("commit.count"));
        this.commitPuts = counter(name("commit.puts"));
        this.commitDeletes = counter(name("commit.deletes"));
        this.pruneCount = counter(name("prune.count"));
        this.pruneNodes = counter(name("prune.nodes"));
        this.pruneCacheEvicted = counter(name("prune.cacheEvicted"));
    }

    public static JmtMetrics create(Object meterRegistry, String prefix) {
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        try {
            Class<?> regClazz = Class.forName("io.micrometer.core.instrument.MeterRegistry");
            if (!regClazz.isInstance(meterRegistry)) {
                return JmtMetrics.NOOP;
            }
            Method counterMethod = regClazz.getMethod("counter", String.class, String[].class);
            Method timerMethod = regClazz.getMethod("timer", String.class, String[].class);

            Class<?> counterClazz = Class.forName("io.micrometer.core.instrument.Counter");
            Method counterIncrement = counterClazz.getMethod("increment", double.class);

            Class<?> timerClazz = Class.forName("io.micrometer.core.instrument.Timer");
            Method timerRecord = timerClazz.getMethod("record", long.class, TimeUnit.class);

            return new JmtMicrometerAdapter(meterRegistry, prefix, counterMethod, timerMethod, counterIncrement, timerRecord);
        } catch (Throwable t) {
            return JmtMetrics.NOOP;
        }
    }

    private String name(String suffix) {
        return prefix + "." + suffix;
    }

    private Object counter(String name) throws Exception {
        return counterMethod.invoke(registry, name, new String[0]);
    }

    private Object timer(String name) throws Exception {
        return timerMethod.invoke(registry, name, new String[0]);
    }

    private void inc(Object counter, double amount) {
        try {
            counterIncrementMethod.invoke(counter, amount);
        } catch (Throwable ignored) {
        }
    }

    private void record(Object timer, long millis) {
        try {
            timerRecordMethod.invoke(timer, millis, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void nodeCacheHit(boolean hit) {
        inc(hit ? nodeCacheHit : nodeCacheMiss, 1.0);
    }

    @Override
    public void valueCacheHit(boolean hit) {
        inc(hit ? valueCacheHit : valueCacheMiss, 1.0);
    }

    @Override
    public void recordCommit(long millis, int puts, int deletes) {
        inc(commitCount, 1.0);
        if (puts > 0) inc(commitPuts, puts);
        if (deletes > 0) inc(commitDeletes, deletes);
        if (millis >= 0) record(commitTimer, millis);
    }

    @Override
    public void recordPrune(long millis, int nodesPruned, int cacheEvicted) {
        inc(pruneCount, 1.0);
        if (nodesPruned > 0) inc(pruneNodes, nodesPruned);
        if (cacheEvicted > 0) inc(pruneCacheEvicted, cacheEvicted);
        if (millis >= 0) record(pruneTimer, millis);
    }
}

