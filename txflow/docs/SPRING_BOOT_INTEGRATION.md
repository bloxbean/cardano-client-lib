# Spring Boot Integration Guide

This guide shows how to integrate TxFlow with Spring Boot applications for production use.

## Basic Setup

### Maven/Gradle Dependencies

```gradle
// build.gradle
dependencies {
    implementation 'com.bloxbean.cardano:cardano-client-lib-txflow:${version}'
    implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:${version}'
}
```

### Configuration Class

```java
@Configuration
public class CardanoConfig {

    @Value("${cardano.backend.url}")
    private String backendUrl;

    @Value("${cardano.backend.apiKey}")
    private String apiKey;

    @Bean
    public BackendService backendService() {
        return new BFBackendService(backendUrl, apiKey);
    }

    @Bean
    public FlowExecutor flowExecutor(BackendService backendService) {
        return FlowExecutor.create(backendService)
            .withConfirmationConfig(ConfirmationConfig.defaults())
            .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW);
    }
}
```

### Application Properties

```yaml
# application.yml
cardano:
  backend:
    url: https://cardano-mainnet.blockfrost.io/api/v0
    apiKey: ${BLOCKFROST_API_KEY}
```

## Service Layer Pattern

### Transaction Service

```java
@Service
@Slf4j
public class TransactionService {

    private final FlowExecutor flowExecutor;
    private final BackendService backendService;

    // Track active flows in-memory (use FlowRegistry when available)
    private final ConcurrentMap<String, FlowHandle> activeFlows = new ConcurrentHashMap<>();

    public TransactionService(FlowExecutor flowExecutor, BackendService backendService) {
        this.flowExecutor = flowExecutor;
        this.backendService = backendService;
    }

    /**
     * Submit a payment and return immediately with a tracking handle.
     */
    public FlowHandle submitPayment(String fromAddress, String toAddress,
                                    BigInteger amount, Account signer) {
        TxFlow flow = TxFlow.builder("payment-" + UUID.randomUUID())
            .withDescription("Payment of " + amount + " lovelace")
            .addStep(FlowStep.builder("send")
                .withDescription("Send payment")
                .withTxContext(builder -> builder
                    .compose(new Tx()
                        .payToAddress(toAddress, Amount.lovelace(amount))
                        .from(fromAddress))
                    .withSigner(SignerProviders.signerFrom(signer)))
                .build())
            .build();

        FlowHandle handle = flowExecutor.execute(flow);
        activeFlows.put(flow.getId(), handle);

        // Auto-remove when complete
        handle.getResultFuture().whenComplete((result, error) -> {
            activeFlows.remove(flow.getId());
        });

        return handle;
    }

    /**
     * Get status of a specific flow.
     */
    public Optional<FlowStatus> getFlowStatus(String flowId) {
        return Optional.ofNullable(activeFlows.get(flowId))
            .map(FlowHandle::getStatus);
    }

    /**
     * Get all active flows.
     */
    public List<FlowSummary> getActiveFlows() {
        return activeFlows.entrySet().stream()
            .map(entry -> new FlowSummary(
                entry.getKey(),
                entry.getValue().getStatus(),
                entry.getValue().getCompletedStepCount(),
                entry.getValue().getTotalStepCount()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Wait for a flow to complete (with timeout).
     */
    public FlowResult awaitCompletion(String flowId, Duration timeout)
            throws TimeoutException {
        FlowHandle handle = activeFlows.get(flowId);
        if (handle == null) {
            throw new IllegalArgumentException("Flow not found: " + flowId);
        }
        return handle.await(timeout);
    }
}

@Data
@AllArgsConstructor
public class FlowSummary {
    private String flowId;
    private FlowStatus status;
    private int completedSteps;
    private int totalSteps;
}
```

## REST Controller

```java
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/payment")
    public ResponseEntity<PaymentResponse> submitPayment(
            @RequestBody PaymentRequest request) {

        FlowHandle handle = transactionService.submitPayment(
            request.getFromAddress(),
            request.getToAddress(),
            request.getAmount(),
            request.getSigner()
        );

        return ResponseEntity.accepted().body(new PaymentResponse(
            handle.getFlow().getId(),
            handle.getStatus()
        ));
    }

    @GetMapping("/{flowId}/status")
    public ResponseEntity<FlowStatus> getStatus(@PathVariable String flowId) {
        return transactionService.getFlowStatus(flowId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    public ResponseEntity<List<FlowSummary>> getActiveFlows() {
        return ResponseEntity.ok(transactionService.getActiveFlows());
    }

    @PostMapping("/{flowId}/await")
    public ResponseEntity<FlowResult> awaitCompletion(
            @PathVariable String flowId,
            @RequestParam(defaultValue = "60") int timeoutSeconds) {
        try {
            FlowResult result = transactionService.awaitCompletion(
                flowId, Duration.ofSeconds(timeoutSeconds));
            return ResponseEntity.ok(result);
        } catch (TimeoutException e) {
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build();
        }
    }
}
```

## Metrics with FlowListener

```java
@Component
public class MetricsFlowListener implements FlowListener {

    private final MeterRegistry meterRegistry;
    private final Counter flowsStarted;
    private final Counter flowsCompleted;
    private final Counter flowsFailed;
    private final Counter transactionsSubmitted;
    private final Counter rollbacksDetected;

    public MetricsFlowListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.flowsStarted = meterRegistry.counter("txflow.flows.started");
        this.flowsCompleted = meterRegistry.counter("txflow.flows.completed");
        this.flowsFailed = meterRegistry.counter("txflow.flows.failed");
        this.transactionsSubmitted = meterRegistry.counter("txflow.transactions.submitted");
        this.rollbacksDetected = meterRegistry.counter("txflow.rollbacks.detected");
    }

    @Override
    public void onFlowStarted(TxFlow flow) {
        flowsStarted.increment();
    }

    @Override
    public void onFlowCompleted(TxFlow flow, FlowResult result) {
        flowsCompleted.increment();
    }

    @Override
    public void onFlowFailed(TxFlow flow, FlowResult result) {
        flowsFailed.increment();
    }

    @Override
    public void onTransactionSubmitted(FlowStep step, String transactionHash) {
        transactionsSubmitted.increment();
    }

    @Override
    public void onTransactionRolledBack(FlowStep step, String txHash, long height) {
        rollbacksDetected.increment();
    }
}

// Configuration to use the metrics listener
@Configuration
public class CardanoConfig {

    @Bean
    public FlowExecutor flowExecutor(BackendService backendService,
                                     MetricsFlowListener metricsListener) {
        return FlowExecutor.create(backendService)
            .withConfirmationConfig(ConfirmationConfig.defaults())
            .withListener(metricsListener);
    }
}
```

## Error Handling

```java
@ControllerAdvice
public class TxFlowExceptionHandler {

    @ExceptionHandler(FlowExecutionException.class)
    public ResponseEntity<ErrorResponse> handleFlowException(
            FlowExecutionException ex) {
        return ResponseEntity.badRequest().body(
            new ErrorResponse("FLOW_ERROR", ex.getMessage())
        );
    }

    @ExceptionHandler(ConfirmationTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(
            ConfirmationTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(
            new ErrorResponse("CONFIRMATION_TIMEOUT",
                "Transaction " + ex.getTxHash() + " did not confirm in time")
        );
    }
}
```

## Async Processing with Virtual Threads (Java 21+)

```java
@Configuration
public class AsyncConfig {

    @Bean
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public FlowExecutor flowExecutor(BackendService backendService,
                                     Executor virtualThreadExecutor,
                                     MetricsFlowListener metricsListener) {
        return FlowExecutor.create(backendService)
            .withExecutor(virtualThreadExecutor)  // Use virtual threads
            .withConfirmationConfig(ConfirmationConfig.defaults())
            .withListener(metricsListener);
    }
}
```

## Health Check

```java
@Component
public class CardanoHealthIndicator implements HealthIndicator {

    private final BackendService backendService;

    @Override
    public Health health() {
        try {
            var result = backendService.getBlockService().getLatestBlock();
            if (result.isSuccessful()) {
                return Health.up()
                    .withDetail("blockHeight", result.getValue().getHeight())
                    .build();
            }
            return Health.down()
                .withDetail("error", result.getResponse())
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

## Multi-Step Transaction Example

```java
@Service
public class EscrowService {

    private final FlowExecutor flowExecutor;

    public FlowHandle createEscrow(Account buyer, Account seller,
                                   String escrowAddress, BigInteger amount) {
        TxFlow flow = TxFlow.builder("escrow-" + UUID.randomUUID())
            .withDescription("Escrow transaction")
            .addStep(FlowStep.builder("deposit")
                .withDescription("Buyer deposits to escrow")
                .withTxContext(builder -> builder
                    .compose(new Tx()
                        .payToAddress(escrowAddress, Amount.lovelace(amount))
                        .from(buyer.baseAddress()))
                    .withSigner(SignerProviders.signerFrom(buyer)))
                .build())
            .addStep(FlowStep.builder("release")
                .withDescription("Release from escrow to seller")
                .dependsOn("deposit", SelectionStrategy.ALL)
                .withTxContext(builder -> builder
                    .compose(new Tx()
                        .payToAddress(seller.baseAddress(), Amount.lovelace(amount))
                        .from(escrowAddress))
                    .withSigner(SignerProviders.signerFrom(/* escrow signer */)))
                .build())
            .build();

        return flowExecutor.execute(flow);
    }
}
```

## See Also

- [Virtual Threads Support](VIRTUAL_THREADS.md)
- [FlowListener Patterns](FLOWLISTENER_PATTERNS.md)
