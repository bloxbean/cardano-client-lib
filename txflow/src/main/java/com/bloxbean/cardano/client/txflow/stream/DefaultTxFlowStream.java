package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default MVP implementation of {@link TxFlowStream}.
 * <p>
 * The implementation owns a bounded queue, a single worker thread, count/time
 * windowing, stream-level state updates, and execution of planner-generated
 * bounded flows through {@link FlowExecutionRunner}. Serial execution is the
 * stream-level UTXO coordination strategy for the MVP.
 */
final class DefaultTxFlowStream implements TxFlowStream {
    private final String streamId;
    private final TxWorkSource source;
    private final WindowPolicy windowPolicy;
    private final TxStreamPlanner planner;
    private final TxStreamStateStore stateStore;
    private final TxStreamEventListener eventListener;
    private final UtxoReservationPolicy reservationPolicy;
    private final FlowExecutionRunner runner;
    private final BlockingQueue<WorkEntry> queue;
    private final ExecutorService worker;
    private final Object lifecycleMonitor = new Object();
    private final Object submitMonitor = new Object();

    private final AtomicLong batchSequence = new AtomicLong();
    private final AtomicLong acceptedCount = new AtomicLong();
    private final AtomicLong plannedCount = new AtomicLong();
    private final AtomicLong submittedCount = new AtomicLong();
    private final AtomicLong confirmedCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong cancelledCount = new AtomicLong();
    private final AtomicLong generatedFlowCount = new AtomicLong();

    private volatile boolean started;
    private volatile boolean accepting;
    private volatile boolean paused;
    private volatile boolean drainRequested;
    private volatile boolean shutdownRequested;
    private volatile boolean closed;
    private volatile boolean flushRequested;
    private volatile int currentWindowSize;
    private volatile int activeBatchCount;

    DefaultTxFlowStream(String streamId,
                        TxWorkSource source,
                        WindowPolicy windowPolicy,
                        TxStreamPlanner planner,
                        TxStreamStateStore stateStore,
                        TxStreamEventListener eventListener,
                        UtxoReservationPolicy reservationPolicy,
                        int maxBufferSize,
                        FlowExecutionRunner runner) {
        this.streamId = streamId;
        this.source = source;
        this.windowPolicy = windowPolicy;
        this.planner = planner;
        this.stateStore = stateStore;
        this.eventListener = eventListener;
        this.reservationPolicy = reservationPolicy;
        this.runner = runner;
        this.queue = new ArrayBlockingQueue<>(maxBufferSize);
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "txflow-stream-" + streamId);
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (closed) {
                throw new IllegalStateException("Stream is closed");
            }
            if (started) {
                return;
            }
            started = true;
            accepting = true;
        }

        worker.execute(this::runWorker);
        eventListener.onStreamStarted(streamId);
        source.start(this::submit);
    }

    @Override
    public void pause() {
        synchronized (lifecycleMonitor) {
            paused = true;
        }
        source.pause();
    }

    @Override
    public void resume() {
        synchronized (lifecycleMonitor) {
            if (!closed && !drainRequested && !shutdownRequested) {
                paused = false;
            }
        }
        source.resume();
    }

    @Override
    public void flush() {
        synchronized (lifecycleMonitor) {
            flushRequested = true;
            lifecycleMonitor.notifyAll();
        }
    }

    @Override
    public void drain() {
        synchronized (lifecycleMonitor) {
            accepting = false;
            drainRequested = true;
            flushRequested = true;
            lifecycleMonitor.notifyAll();
        }
        awaitDrain();
        eventListener.onStreamDrained(streamId);
    }

    @Override
    public void awaitDrain() {
        synchronized (lifecycleMonitor) {
            while (!isDrained()) {
                try {
                    lifecycleMonitor.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for stream drain", e);
                }
            }
        }
    }

    @Override
    public void awaitDrain(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (lifecycleMonitor) {
            while (!isDrained()) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new IllegalStateException(new TimeoutException("Timed out waiting for stream drain"));
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(lifecycleMonitor, remainingNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for stream drain", e);
                }
            }
        }
    }

    @Override
    public void shutdown() {
        synchronized (lifecycleMonitor) {
            accepting = false;
            drainRequested = true;
            shutdownRequested = true;
            flushRequested = true;
            lifecycleMonitor.notifyAll();
        }

        awaitDrain();
        worker.shutdown();
        try {
            if (!worker.awaitTermination(30, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    @Override
    public TxStreamReceipt submit(TxWorkItem item) {
        synchronized (submitMonitor) {
            WorkEntry entry = prepareEntry(item);
            try {
                queue.put(entry);
                notifyLifecycle();
                return entry.receipt;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                TxStreamItemResult failed = itemResult(item.getItemId(), TxStreamItemStatus.FAILED)
                        .failure(e)
                        .build();
                updateReceipt(entry.receipt, failed);
                throw new IllegalStateException("Interrupted while submitting stream item", e);
            }
        }
    }

    @Override
    public EmitResult trySubmit(TxWorkItem item) {
        if (!canAccept()) {
            if (closed || shutdownRequested || drainRequested) {
                return EmitResult.closed();
            }
            return EmitResult.paused();
        }

        synchronized (submitMonitor) {
            if (queue.remainingCapacity() == 0) {
                return EmitResult.full();
            }
            WorkEntry entry = prepareEntry(item);
            if (!queue.offer(entry)) {
                return EmitResult.full();
            }
            notifyLifecycle();
            return EmitResult.ok(entry.receipt);
        }
    }

    @Override
    public Optional<TxStreamItemResult> getItemStatus(String itemId) {
        return stateStore.getItem(streamId, itemId);
    }

    @Override
    public Optional<TxStreamBatchResult> getBatchStatus(String batchId) {
        return stateStore.getBatch(streamId, batchId);
    }

    @Override
    public TxStreamStats getStats() {
        return new TxStreamStats(
                acceptedCount.get(),
                plannedCount.get(),
                submittedCount.get(),
                confirmedCount.get(),
                failedCount.get(),
                cancelledCount.get(),
                batchSequence.get(),
                generatedFlowCount.get(),
                queue.size(),
                currentWindowSize);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        try {
            shutdown();
        } finally {
            source.close();
            runner.close();
            synchronized (lifecycleMonitor) {
                closed = true;
                lifecycleMonitor.notifyAll();
            }
            eventListener.onStreamClosed(streamId);
        }
    }

    private WorkEntry prepareEntry(TxWorkItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        if (!canAccept()) {
            throw new IllegalStateException("Stream is not accepting work");
        }

        TxStreamItemResult accepted = itemResult(item.getItemId(), TxStreamItemStatus.ACCEPTED).build();
        TxStreamReceipt receipt = new TxStreamReceipt(streamId, item.getItemId(), accepted);
        stateStore.recordItem(accepted);
        acceptedCount.incrementAndGet();
        eventListener.onItemAccepted(item, receipt);
        eventListener.onItemUpdated(accepted);
        return new WorkEntry(item, receipt);
    }

    private boolean canAccept() {
        return started && accepting && !paused && !closed && !drainRequested && !shutdownRequested;
    }

    private void runWorker() {
        List<WorkEntry> window = new ArrayList<>();
        Instant windowStartedAt = null;

        while (true) {
            try {
                WorkEntry entry = queue.poll(100, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    if (window.isEmpty()) {
                        windowStartedAt = Instant.now();
                    }
                    window.add(entry);
                    drainAdditionalEntries(window);
                    currentWindowSize = window.size();
                }

                if (!window.isEmpty() && shouldExecuteWindow(window, windowStartedAt)) {
                    List<WorkEntry> toExecute = new ArrayList<>(window);
                    window.clear();
                    currentWindowSize = 0;
                    flushRequested = false;
                    executeWindow(toExecute);
                    windowStartedAt = null;
                    notifyLifecycle();
                }

                if (shutdownRequested && queue.isEmpty() && window.isEmpty() && activeBatchCount == 0) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failWindow(window, "interrupted", e);
                break;
            } catch (Exception e) {
                failWindow(window, "worker-error", e);
                window.clear();
                currentWindowSize = 0;
                notifyLifecycle();
            }
        }

        currentWindowSize = 0;
        notifyLifecycle();
    }

    private void drainAdditionalEntries(List<WorkEntry> window) {
        int remainingCapacity = windowPolicy.getMaxItems() - window.size();
        while (remainingCapacity > 0) {
            WorkEntry next = queue.poll();
            if (next == null) {
                return;
            }
            window.add(next);
            remainingCapacity--;
        }
    }

    private boolean shouldExecuteWindow(List<WorkEntry> window, Instant windowStartedAt) {
        if (window.size() >= windowPolicy.getMaxItems()) {
            return true;
        }
        if (flushRequested || drainRequested || shutdownRequested) {
            return true;
        }
        if (windowStartedAt == null) {
            return false;
        }
        return Duration.between(windowStartedAt, Instant.now()).compareTo(windowPolicy.getMaxAge()) >= 0;
    }

    private void executeWindow(List<WorkEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        activeBatchCount++;
        String batchId = "batch-" + batchSequence.incrementAndGet();
        List<String> itemIds = itemIds(entries);

        try {
            TxStreamPlanningContext context = new TxStreamPlanningContext(streamId, batchId, workItems(entries));
            TxStreamPlan plan = planner.plan(context);
            List<String> flowIds = flowIds(plan);
            recordBatch(batchId, TxStreamBatchStatus.PLANNED, itemIds, flowIds, null);

            Map<String, WorkEntry> entriesByItemId = entriesByItemId(entries);
            for (TxStreamPlannedFlow plannedFlow : plan.getFlows()) {
                for (TxStreamPlannedItem plannedItem : plannedFlow.getItems()) {
                    WorkEntry entry = entriesByItemId.get(plannedItem.getItemId());
                    if (entry != null) {
                        TxStreamItemResult planned = itemResult(entry.item.getItemId(), TxStreamItemStatus.PLANNED)
                                .batchId(batchId)
                                .flowId(plannedFlow.getFlow().getId())
                                .stepId(plannedItem.getStepId())
                                .build();
                        updateReceipt(entry.receipt, planned);
                        plannedCount.incrementAndGet();
                    }
                }
            }

            recordBatch(batchId, TxStreamBatchStatus.SUBMITTED, itemIds, flowIds, null);
            boolean allSuccessful = true;
            Throwable batchFailure = null;

            for (TxStreamPlannedFlow plannedFlow : plan.getFlows()) {
                markSubmitted(entriesByItemId, batchId, plannedFlow);
                generatedFlowCount.incrementAndGet();
                FlowResult result = runner.execute(plannedFlow.getFlow());
                if (!completePlannedFlow(entriesByItemId, batchId, plannedFlow, result)) {
                    allSuccessful = false;
                    batchFailure = result.getError();
                }
            }

            recordBatch(batchId,
                    allSuccessful ? TxStreamBatchStatus.COMPLETED : TxStreamBatchStatus.FAILED,
                    itemIds,
                    flowIds,
                    batchFailure);
        } catch (Exception e) {
            failEntries(entries, batchId, e);
            recordBatch(batchId, TxStreamBatchStatus.FAILED, itemIds, List.of(), e);
        } finally {
            activeBatchCount--;
            notifyLifecycle();
        }
    }

    private void markSubmitted(Map<String, WorkEntry> entriesByItemId,
                               String batchId,
                               TxStreamPlannedFlow plannedFlow) {
        for (TxStreamPlannedItem plannedItem : plannedFlow.getItems()) {
            WorkEntry entry = entriesByItemId.get(plannedItem.getItemId());
            if (entry != null) {
                TxStreamItemResult submitted = itemResult(entry.item.getItemId(), TxStreamItemStatus.SUBMITTED)
                        .batchId(batchId)
                        .flowId(plannedFlow.getFlow().getId())
                        .stepId(plannedItem.getStepId())
                        .build();
                updateReceipt(entry.receipt, submitted);
                submittedCount.incrementAndGet();
            }
        }
    }

    private boolean completePlannedFlow(Map<String, WorkEntry> entriesByItemId,
                                        String batchId,
                                        TxStreamPlannedFlow plannedFlow,
                                        FlowResult flowResult) {
        boolean allSuccessful = flowResult.isSuccessful();
        for (TxStreamPlannedItem plannedItem : plannedFlow.getItems()) {
            WorkEntry entry = entriesByItemId.get(plannedItem.getItemId());
            if (entry == null) {
                continue;
            }

            Optional<FlowStepResult> stepResult = flowResult.getStepResult(plannedItem.getStepId());
            if (stepResult.isPresent() && stepResult.get().isSuccessful()) {
                TxStreamItemResult confirmed = itemResult(entry.item.getItemId(), TxStreamItemStatus.CONFIRMED)
                        .batchId(batchId)
                        .flowId(plannedFlow.getFlow().getId())
                        .stepId(plannedItem.getStepId())
                        .transactionHash(stepResult.get().getTransactionHash())
                        .build();
                updateReceipt(entry.receipt, confirmed);
                confirmedCount.incrementAndGet();
            } else {
                allSuccessful = false;
                Throwable failure = stepResult.map(FlowStepResult::getError)
                        .orElse(flowResult.getError() != null
                                ? flowResult.getError()
                                : new IllegalStateException("Generated flow did not return a result for step "
                                        + plannedItem.getStepId()));
                TxStreamItemResult failed = itemResult(entry.item.getItemId(), TxStreamItemStatus.FAILED)
                        .batchId(batchId)
                        .flowId(plannedFlow.getFlow().getId())
                        .stepId(plannedItem.getStepId())
                        .failure(failure)
                        .build();
                updateReceipt(entry.receipt, failed);
                failedCount.incrementAndGet();
            }
        }
        return allSuccessful;
    }

    private void failEntries(List<WorkEntry> entries, String batchId, Throwable failure) {
        for (WorkEntry entry : entries) {
            TxStreamItemResult failed = itemResult(entry.item.getItemId(), TxStreamItemStatus.FAILED)
                    .batchId(batchId)
                    .failure(failure)
                    .build();
            updateReceipt(entry.receipt, failed);
            failedCount.incrementAndGet();
        }
    }

    private void failWindow(List<WorkEntry> window, String batchId, Throwable failure) {
        if (!window.isEmpty()) {
            failEntries(window, batchId, failure);
            window.clear();
        }
    }

    private void updateReceipt(TxStreamReceipt receipt, TxStreamItemResult result) {
        stateStore.recordItem(result);
        receipt.update(result);
        eventListener.onItemUpdated(result);
    }

    private void recordBatch(String batchId,
                             TxStreamBatchStatus status,
                             List<String> itemIds,
                             List<String> flowIds,
                             Throwable failure) {
        TxStreamBatchResult result = TxStreamBatchResult.builder(streamId, batchId, status)
                .itemIds(itemIds)
                .flowIds(flowIds)
                .failure(failure)
                .build();
        stateStore.recordBatch(result);
        eventListener.onBatchUpdated(result);
    }

    private TxStreamItemResult.Builder itemResult(String itemId, TxStreamItemStatus status) {
        return TxStreamItemResult.builder(streamId, itemId, status);
    }

    private List<TxWorkItem> workItems(List<WorkEntry> entries) {
        List<TxWorkItem> items = new ArrayList<>(entries.size());
        for (WorkEntry entry : entries) {
            items.add(entry.item);
        }
        return items;
    }

    private List<String> itemIds(List<WorkEntry> entries) {
        List<String> itemIds = new ArrayList<>(entries.size());
        for (WorkEntry entry : entries) {
            itemIds.add(entry.item.getItemId());
        }
        return itemIds;
    }

    private List<String> flowIds(TxStreamPlan plan) {
        List<String> flowIds = new ArrayList<>();
        for (TxStreamPlannedFlow flow : plan.getFlows()) {
            flowIds.add(flow.getFlow().getId());
        }
        return flowIds;
    }

    private Map<String, WorkEntry> entriesByItemId(List<WorkEntry> entries) {
        Map<String, WorkEntry> result = new HashMap<>();
        for (WorkEntry entry : entries) {
            result.put(entry.item.getItemId(), entry);
        }
        return result;
    }

    private boolean isDrained() {
        return queue.isEmpty() && currentWindowSize == 0 && activeBatchCount == 0;
    }

    private void notifyLifecycle() {
        synchronized (lifecycleMonitor) {
            lifecycleMonitor.notifyAll();
        }
    }

    @SuppressWarnings("unused")
    private UtxoReservationPolicy getReservationPolicy() {
        return reservationPolicy;
    }

    private static final class WorkEntry {
        private final TxWorkItem item;
        private final TxStreamReceipt receipt;

        private WorkEntry(TxWorkItem item, TxStreamReceipt receipt) {
            this.item = item;
            this.receipt = receipt;
        }
    }
}
