# FlowListener Patterns

This guide documents all FlowListener callbacks and common usage patterns.

## Available Callbacks

FlowListener provides 16 callback methods organized by category:

### Flow-Level Callbacks

| Callback | When Called | Use Case |
|----------|-------------|----------|
| `onFlowStarted(flow)` | Flow execution begins | Logging, metrics |
| `onFlowCompleted(flow, result)` | Flow completes successfully | Cleanup, notifications |
| `onFlowFailed(flow, result)` | Flow fails | Error handling, alerts |
| `onFlowRestarting(flow, attempt, max, reason)` | Flow restarts after rollback | Audit logging |

### Step-Level Callbacks

| Callback | When Called | Use Case |
|----------|-------------|----------|
| `onStepStarted(step, index, total)` | Step execution begins | Progress tracking |
| `onStepCompleted(step, result)` | Step completes successfully | State updates |
| `onStepFailed(step, result)` | Step fails | Error handling |
| `onStepRetry(step, attempt, max, error)` | Before step retry | Retry tracking |
| `onStepRetryExhausted(step, attempts, error)` | All retries failed | Escalation |
| `onStepRebuilding(step, attempt, max, reason)` | Step rebuilding after rollback | Audit logging |

### Transaction-Level Callbacks

| Callback | When Called | Use Case |
|----------|-------------|----------|
| `onTransactionSubmitted(step, txHash)` | Tx submitted to network | Recording tx hash |
| `onTransactionConfirmed(step, txHash)` | Tx reaches confirmation threshold | Completion handling |
| `onTransactionInBlock(step, txHash, height)` | Tx included in block | Block tracking |
| `onConfirmationDepthChanged(step, txHash, depth, status)` | Confirmation depth updates | Progress UI |
| `onTransactionFinalized(step, txHash)` | Tx reaches finality | Final confirmation |
| `onTransactionRolledBack(step, txHash, height)` | Chain reorg removes tx | Alert, recovery |

## Basic Usage

### Simple Logging Listener

```java
FlowListener loggingListener = new FlowListener() {
    @Override
    public void onFlowStarted(TxFlow flow) {
        log.info("Flow started: {}", flow.getId());
    }

    @Override
    public void onFlowCompleted(TxFlow flow, FlowResult result) {
        log.info("Flow completed: {} with {} transactions",
            flow.getId(), result.getTransactionHashes().size());
    }

    @Override
    public void onFlowFailed(TxFlow flow, FlowResult result) {
        log.error("Flow failed: {} - {}",
            flow.getId(),
            result.getError() != null ? result.getError().getMessage() : "unknown");
    }

    @Override
    public void onTransactionSubmitted(FlowStep step, String txHash) {
        log.info("Step '{}' submitted tx: {}", step.getId(), txHash);
    }
};

FlowExecutor executor = FlowExecutor.create(backendService)
    .withListener(loggingListener);
```

### Progress Tracking Listener

```java
public class ProgressListener implements FlowListener {
    private final Consumer<ProgressUpdate> progressConsumer;

    public ProgressListener(Consumer<ProgressUpdate> consumer) {
        this.progressConsumer = consumer;
    }

    @Override
    public void onStepStarted(FlowStep step, int index, int total) {
        progressConsumer.accept(new ProgressUpdate(
            "Step " + (index + 1) + "/" + total + ": " + step.getDescription(),
            (index * 100) / total
        ));
    }

    @Override
    public void onConfirmationDepthChanged(FlowStep step, String txHash,
                                           int depth, ConfirmationStatus status) {
        progressConsumer.accept(new ProgressUpdate(
            "Confirmation: " + depth + " blocks (" + status + ")",
            -1  // Indeterminate
        ));
    }

    @Override
    public void onFlowCompleted(TxFlow flow, FlowResult result) {
        progressConsumer.accept(new ProgressUpdate("Complete!", 100));
    }
}

// Usage with UI callback
FlowHandle handle = executor
    .withListener(new ProgressListener(update -> {
        Platform.runLater(() -> progressBar.setProgress(update.percent / 100.0));
    }))
    .execute(flow);
```

## Composite Listener Pattern

Combine multiple listeners for different concerns:

```java
// Metrics listener
FlowListener metricsListener = new FlowListener() {
    @Override
    public void onFlowStarted(TxFlow flow) {
        metrics.counter("flows.started").increment();
    }
    // ... other metrics callbacks
};

// Audit listener
FlowListener auditListener = new FlowListener() {
    @Override
    public void onTransactionSubmitted(FlowStep step, String txHash) {
        auditLog.record("TX_SUBMITTED", txHash, step.getId());
    }
    // ... other audit callbacks
};

// Alert listener
FlowListener alertListener = new FlowListener() {
    @Override
    public void onTransactionRolledBack(FlowStep step, String txHash, long height) {
        alertService.sendAlert("Transaction rolled back: " + txHash);
    }
    // ... other alert callbacks
};

// Combine using FlowListener.composite()
FlowListener combinedListener = FlowListener.composite(
    metricsListener,
    auditListener,
    alertListener
);

FlowExecutor executor = FlowExecutor.create(backendService)
    .withListener(combinedListener);
```

**Note**: `FlowListener.composite()` catches exceptions from individual listeners, so one failing listener won't affect others.

## Rollback Handling Pattern

```java
public class RollbackAwareListener implements FlowListener {
    private final AtomicInteger rollbackCount = new AtomicInteger(0);
    private final List<String> rolledBackTxHashes = new CopyOnWriteArrayList<>();

    @Override
    public void onTransactionRolledBack(FlowStep step, String txHash,
                                        long previousBlockHeight) {
        rollbackCount.incrementAndGet();
        rolledBackTxHashes.add(txHash);

        log.warn("ROLLBACK DETECTED: tx={} was in block {} but is now gone",
            txHash, previousBlockHeight);
    }

    @Override
    public void onFlowRestarting(TxFlow flow, int attempt, int maxAttempts,
                                 String reason) {
        log.info("Flow '{}' restarting (attempt {}/{}): {}",
            flow.getId(), attempt, maxAttempts, reason);
    }

    @Override
    public void onStepRebuilding(FlowStep step, int attempt, int maxAttempts,
                                 String reason) {
        log.info("Step '{}' rebuilding (attempt {}/{}): {}",
            step.getId(), attempt, maxAttempts, reason);
    }

    public int getRollbackCount() {
        return rollbackCount.get();
    }

    public List<String> getRolledBackTransactions() {
        return new ArrayList<>(rolledBackTxHashes);
    }
}
```

## State Machine Pattern

Track flow state transitions:

```java
public class StateMachineListener implements FlowListener {
    private final Map<String, FlowState> flowStates = new ConcurrentHashMap<>();

    public enum FlowState {
        STARTED, SUBMITTING, CONFIRMING, COMPLETED, FAILED, RESTARTING
    }

    @Override
    public void onFlowStarted(TxFlow flow) {
        flowStates.put(flow.getId(), FlowState.STARTED);
    }

    @Override
    public void onTransactionSubmitted(FlowStep step, String txHash) {
        // Flow is in submitting state when transactions are being submitted
        flowStates.computeIfPresent(getFlowId(step), (k, v) -> FlowState.SUBMITTING);
    }

    @Override
    public void onConfirmationDepthChanged(FlowStep step, String txHash,
                                           int depth, ConfirmationStatus status) {
        flowStates.computeIfPresent(getFlowId(step), (k, v) -> FlowState.CONFIRMING);
    }

    @Override
    public void onFlowCompleted(TxFlow flow, FlowResult result) {
        flowStates.put(flow.getId(), FlowState.COMPLETED);
    }

    @Override
    public void onFlowFailed(TxFlow flow, FlowResult result) {
        flowStates.put(flow.getId(), FlowState.FAILED);
    }

    @Override
    public void onFlowRestarting(TxFlow flow, int attempt, int max, String reason) {
        flowStates.put(flow.getId(), FlowState.RESTARTING);
    }

    public FlowState getState(String flowId) {
        return flowStates.getOrDefault(flowId, null);
    }

    private String getFlowId(FlowStep step) {
        // Extract flow ID from step context if available
        return step.getId().split("-")[0];  // Adjust based on your ID scheme
    }
}
```

## Webhook Notification Pattern

```java
public class WebhookListener implements FlowListener {
    private final WebClient webClient;
    private final String webhookUrl;

    public WebhookListener(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.webClient = WebClient.create();
    }

    @Override
    public void onTransactionConfirmed(FlowStep step, String txHash) {
        sendWebhook(new WebhookEvent("TX_CONFIRMED", Map.of(
            "stepId", step.getId(),
            "txHash", txHash,
            "timestamp", Instant.now().toString()
        )));
    }

    @Override
    public void onFlowCompleted(TxFlow flow, FlowResult result) {
        sendWebhook(new WebhookEvent("FLOW_COMPLETED", Map.of(
            "flowId", flow.getId(),
            "txHashes", result.getTransactionHashes(),
            "timestamp", Instant.now().toString()
        )));
    }

    @Override
    public void onFlowFailed(TxFlow flow, FlowResult result) {
        sendWebhook(new WebhookEvent("FLOW_FAILED", Map.of(
            "flowId", flow.getId(),
            "error", result.getError() != null ? result.getError().getMessage() : "unknown",
            "timestamp", Instant.now().toString()
        )));
    }

    private void sendWebhook(WebhookEvent event) {
        webClient.post()
            .uri(webhookUrl)
            .bodyValue(event)
            .retrieve()
            .toBodilessEntity()
            .subscribe(
                response -> log.debug("Webhook sent: {}", event.type),
                error -> log.error("Webhook failed: {}", error.getMessage())
            );
    }
}
```

## Database Audit Pattern

```java
@Component
public class AuditListener implements FlowListener {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void onFlowStarted(TxFlow flow) {
        jdbcTemplate.update(
            "INSERT INTO flow_audit (flow_id, event, timestamp) VALUES (?, ?, ?)",
            flow.getId(), "STARTED", Instant.now()
        );
    }

    @Override
    public void onTransactionSubmitted(FlowStep step, String txHash) {
        jdbcTemplate.update(
            "INSERT INTO tx_audit (tx_hash, step_id, event, timestamp) VALUES (?, ?, ?, ?)",
            txHash, step.getId(), "SUBMITTED", Instant.now()
        );
    }

    @Override
    public void onTransactionConfirmed(FlowStep step, String txHash) {
        jdbcTemplate.update(
            "UPDATE tx_audit SET confirmed_at = ? WHERE tx_hash = ?",
            Instant.now(), txHash
        );
    }

    @Override
    public void onTransactionRolledBack(FlowStep step, String txHash, long height) {
        jdbcTemplate.update(
            "INSERT INTO tx_audit (tx_hash, step_id, event, block_height, timestamp) VALUES (?, ?, ?, ?, ?)",
            txHash, step.getId(), "ROLLED_BACK", height, Instant.now()
        );
    }
}
```

## Exception Safety

Listeners are called synchronously on the executor thread. Keep callbacks fast and handle exceptions:

```java
public class SafeListener implements FlowListener {
    private final FlowListener delegate;

    public SafeListener(FlowListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onFlowStarted(TxFlow flow) {
        try {
            delegate.onFlowStarted(flow);
        } catch (Exception e) {
            log.error("Listener error in onFlowStarted: {}", e.getMessage());
            // Don't rethrow - allow flow to continue
        }
    }

    // Wrap all other methods similarly...
}

// Or use the built-in composite() which handles this
FlowListener safeListener = FlowListener.composite(unsafeListener);
```

## Best Practices

1. **Keep Callbacks Fast**: Listeners run on the executor thread. Offload heavy work to separate threads.

2. **Handle Exceptions**: Use `FlowListener.composite()` or wrap callbacks in try-catch.

3. **Use Thread-Safe Collections**: Callbacks may be called concurrently.

4. **Don't Block**: Avoid blocking operations in callbacks.

5. **Composite for Multiple Concerns**: Separate metrics, logging, and business logic into different listeners.

## See Also

- [Virtual Threads Support](VIRTUAL_THREADS.md)
- [Spring Boot Integration](SPRING_BOOT_INTEGRATION.md)
