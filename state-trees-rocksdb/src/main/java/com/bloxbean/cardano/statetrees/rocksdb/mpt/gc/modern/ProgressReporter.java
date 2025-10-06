package com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.modern;

/**
 * Functional interface for reporting garbage collection progress.
 *
 * <p>This interface enables GC strategies to report their progress to
 * external monitoring systems, UIs, or logging frameworks. It follows
 * the functional interface pattern to enable easy lambda expression usage.</p>
 *
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Simple lambda reporter
 * ProgressReporter reporter = progress ->
 *     System.out.printf("GC Progress: %.1f%% (%d/%d)%n",
 *         progress.getPercentComplete(),
 *         progress.getCompletedWork(),
 *         progress.getTotalWork());
 *
 * // Integration with monitoring system
 * ProgressReporter reporter = progress -> {
 *     metrics.gauge("gc.progress.percent", progress.getPercentComplete());
 *     metrics.counter("gc.nodes.processed", progress.getCompletedWork());
 * };
 *
 * // Conditional reporting (e.g., every 10%)
 * ProgressReporter reporter = progress -> {
 *     if (progress.getPercentComplete() % 10 == 0) {
 *         logger.info("GC {}% complete", progress.getPercentComplete());
 *     }
 * };
 * }</pre>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
@FunctionalInterface
public interface ProgressReporter {

    /**
     * Reports progress for a garbage collection operation.
     *
     * <p>Implementations should be lightweight and non-blocking as they
     * may be called frequently during GC execution. Any expensive operations
     * (like network calls) should be performed asynchronously.</p>
     *
     * @param progress the current progress information
     */
    void reportProgress(GcProgress progress);

    /**
     * Creates a progress reporter that logs to System.out.
     *
     * @return a simple console progress reporter
     */
    static ProgressReporter console() {
        return progress -> System.out.printf(
                "GC Progress: %.1f%% (%d/%d) - %s%n",
                progress.getPercentComplete(),
                progress.getCompletedWork(),
                progress.getTotalWork(),
                progress.getCurrentPhase()
        );
    }

    /**
     * Creates a progress reporter that only reports at specified intervals.
     *
     * @param baseReporter    the underlying reporter to delegate to
     * @param intervalPercent the percentage interval for reporting (e.g., 10 for every 10%)
     * @return a throttled progress reporter
     */
    static ProgressReporter throttled(ProgressReporter baseReporter, double intervalPercent) {
        return new ThrottledProgressReporter(baseReporter, intervalPercent);
    }

    /**
     * Creates a progress reporter that combines multiple reporters.
     *
     * @param reporters the reporters to combine
     * @return a composite progress reporter
     */
    static ProgressReporter composite(ProgressReporter... reporters) {
        return progress -> {
            for (ProgressReporter reporter : reporters) {
                try {
                    reporter.reportProgress(progress);
                } catch (Exception e) {
                    // Don't let one reporter failure break others
                    System.err.println("Progress reporter failed: " + e.getMessage());
                }
            }
        };
    }

    /**
     * Creates a no-op progress reporter that ignores all progress updates.
     *
     * @return a no-op progress reporter
     */
    static ProgressReporter noop() {
        return progress -> { /* do nothing */ };
    }
}

/**
 * Implementation of throttled progress reporting.
 */
class ThrottledProgressReporter implements ProgressReporter {
    private final ProgressReporter delegate;
    private final double intervalPercent;
    private double lastReportedPercent = -1;

    ThrottledProgressReporter(ProgressReporter delegate, double intervalPercent) {
        this.delegate = delegate;
        this.intervalPercent = Math.max(0.1, intervalPercent); // Minimum 0.1%
    }

    @Override
    public void reportProgress(GcProgress progress) {
        double currentPercent = progress.getPercentComplete();

        // Always report 0% and 100%
        if (currentPercent == 0.0 || currentPercent >= 100.0) {
            delegate.reportProgress(progress);
            lastReportedPercent = currentPercent;
            return;
        }

        // Report if we've crossed an interval threshold
        if (currentPercent - lastReportedPercent >= intervalPercent) {
            delegate.reportProgress(progress);
            lastReportedPercent = currentPercent;
        }
    }
}
