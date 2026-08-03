package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.FlowStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Monitors earlier transaction attempts until the whole flow reaches its
 * terminal confirmation horizon.
 *
 * <p>Pipelined execution may continue after an earlier transaction first
 * reaches the required depth. This monitor retains those hashes and asks the
 * shared {@link ConfirmationTracker} to re-check them before later progress is
 * considered safe. It returns the first rolled-back or otherwise unconfirmed
 * attempt without discarding an inconclusive observation.</p>
 *
 * <p>The monitor performs polling on the calling task through the tracker's
 * {@link FlowScheduler}; it creates no background task.</p>
 */
final class FlowHorizonMonitor {
    private final ConfirmationTracker tracker;
    private final Map<String, FlowStep> stepByHash = new LinkedHashMap<>();

    FlowHorizonMonitor(ConfirmationTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    /** Adds an attempt to the live horizon, retaining its first step association. */
    void track(FlowStep step, String transactionHash) {
        if (step != null && transactionHash != null) {
            stepByHash.putIfAbsent(transactionHash, step);
        }
    }

    /**
     * Re-checks every tracked attempt.
     *
     * @return the first unsafe or uncertain attempt, or {@code null} when all
     *         tracked attempts remain confirmed
     */
    HorizonResult verify(BooleanSupplier cancelled) {
        return verifyTracked(new ArrayList<>(stepByHash.keySet()), stepByHash, cancelled);
    }

    HorizonResult verify(List<FlowStep> steps, List<String> transactionHashes,
                         BooleanSupplier cancelled) {
        int count = Math.min(steps.size(), transactionHashes.size());
        List<String> trackedHashes = new ArrayList<>();
        Map<String, FlowStep> suppliedSteps = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String transactionHash = transactionHashes.get(index);
            if (transactionHash == null) continue;
            trackedHashes.add(transactionHash);
            suppliedSteps.putIfAbsent(transactionHash, steps.get(index));
        }
        return verifyTracked(trackedHashes, suppliedSteps, cancelled);
    }

    private HorizonResult verifyTracked(List<String> trackedHashes,
                                        Map<String, FlowStep> trackedSteps,
                                        BooleanSupplier cancelled) {
        if (trackedHashes.isEmpty()) return null;
        Map<String, ConfirmationResult> results = tracker.waitForConfirmations(
                trackedHashes, ConfirmationStatus.CONFIRMED, cancelled);
        for (String transactionHash : trackedHashes) {
            ConfirmationResult result = results.get(transactionHash);
            if (result != null && result.isRolledBack()) {
                return new HorizonResult(trackedSteps.get(transactionHash), transactionHash, result);
            }
        }
        for (String transactionHash : trackedHashes) {
            ConfirmationResult result = results.get(transactionHash);
            if (result == null) continue;
            if (!result.hasReached(ConfirmationStatus.CONFIRMED)) {
                return new HorizonResult(trackedSteps.get(transactionHash), transactionHash, result);
            }
        }
        return null;
    }

    /** One tracked attempt that prevents the flow from crossing its horizon. */
    record HorizonResult(FlowStep step, String transactionHash,
                         ConfirmationResult confirmation) {
    }
}
