# Spring Boot Integration Guide

This guide shows how to integrate `FlowEngine` with Spring Boot applications for production use.

## Basic Setup

### Gradle Dependencies

```gradle
def cclVersion = "<version>"

dependencies {
    implementation "com.bloxbean.cardano:cardano-client-txflow:${cclVersion}"
    implementation "com.bloxbean.cardano:cardano-client-backend-blockfrost:${cclVersion}"

    // Optional: add for durable restart recovery (H2 / PostgreSQL)
    implementation "com.bloxbean.cardano:cardano-client-txflow-store-rdbms:${cclVersion}"
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

    @Bean(destroyMethod = "shutdown")
    public ExecutorService txFlowTaskExecutor() {
        return Executors.newFixedThreadPool(16);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService txFlowMaintenanceExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    @Bean
    public FlowExecutionStore flowExecutionStore(DataSource dataSource) {
        return RdbmsFlowExecutionStore.builder()
            .dataSource(dataSource)
            .dialect(PostgresDialect.INSTANCE)
            .schemaManagement(SchemaManagement.VALIDATE)
            .build();
    }

    @Bean
    public FlowEngine flowEngine(BackendService backendService,
                                 SignerRegistry signerRegistry,
                                 FlowExecutionStore flowExecutionStore,
                                 ExecutorService txFlowTaskExecutor,
                                 ExecutorService txFlowMaintenanceExecutor) {
        return FlowEngine.builder(backendService)
            .executor(txFlowTaskExecutor)
            .maintenanceExecutor(txFlowMaintenanceExecutor)
            .signerRegistry(signerRegistry)
            .store(flowExecutionStore)
            .build();
    }
}
```

### Application Properties

```yaml
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

    private final FlowEngine flowEngine;
    private final FlowExecutionStore store;
    private final ConcurrentMap<String, FlowExecutionHandle> activeHandles = new ConcurrentHashMap<>();

    public TransactionService(FlowEngine flowEngine, FlowExecutionStore store) {
        this.flowEngine = flowEngine;
        this.store = store;
    }

    /**
     * Submit a payment and return immediately with an execution handle.
     */
    public FlowExecutionHandle submitPayment(String orderId, String senderRef,
                                             String toAddress, BigInteger amount) {
        TxPlan plan = TxPlan.from(new Tx()
                .payToAddress(toAddress, Amount.lovelace(amount))
                .fromRef(senderRef))
            .withSigner(senderRef);

        TxFlow flow = TxFlow.builder("payment")
            .withDescription("Payment of " + amount + " lovelace")
            .withConfirmationConfig(ConfirmationConfig.defaults())
            .addStep(FlowStep.builder("send")
                .withDescription("Send payment")
                .withTxPlan(plan)
                .build())
            .build();

        String executionId = "payment-" + orderId;
        FlowExecutionRequest request = FlowExecutionRequest.builder(flow)
            .executionId(executionId)
            .idempotency("payments", orderId)
            .spendingResource(senderRef)
            .build();

        FlowExecutionHandle handle = flowEngine.start(request);
        activeHandles.put(executionId, handle);
        handle.completion().whenComplete((result, error) -> activeHandles.remove(executionId));
        return handle;
    }

    /**
     * Look up the durable snapshot for an execution.
     */
    public Optional<FlowExecutionSnapshot> getExecution(String executionId) {
        return store.get(executionId);
    }

    /**
     * Read ordered execution events from an in-flight handle.
     */
    public List<FlowEvent> getEvents(String executionId, long afterSequence) {
        FlowExecutionHandle handle = activeHandles.get(executionId);
        return handle != null ? handle.getEventsAfter(afterSequence) : List.of();
    }

    /**
     * Wait for an active execution to complete (with timeout).
     */
    public FlowExecutionResult awaitCompletion(String executionId, Duration timeout)
            throws Exception {
        FlowExecutionHandle handle = activeHandles.get(executionId);
        if (handle == null) {
            throw new IllegalArgumentException("Active execution not found: " + executionId);
        }
        return handle.completion()
            .toCompletableFuture()
            .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
```

## REST Controller

```java
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/payment")
    public ResponseEntity<Map<String, String>> submitPayment(@RequestBody PaymentRequest request) {
        FlowExecutionHandle handle = transactionService.submitPayment(
            request.getOrderId(),
            request.getSenderRef(),
            request.getToAddress(),
            request.getAmount()
        );

        return ResponseEntity.accepted().body(Map.of(
            "executionId", handle.getExecutionId()
        ));
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<FlowExecutionSnapshot> getExecution(@PathVariable String executionId) {
        return transactionService.getExecution(executionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{executionId}/await")
    public ResponseEntity<FlowExecutionResult> awaitCompletion(
            @PathVariable String executionId,
            @RequestParam(defaultValue = "60") int timeoutSeconds) throws Exception {
        try {
            FlowExecutionResult result = transactionService.awaitCompletion(
                executionId, Duration.ofSeconds(timeoutSeconds));
            return ResponseEntity.ok(result);
        } catch (TimeoutException e) {
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build();
        }
    }
}
```

## Metrics from Terminal Results and Events

Record metrics from `FlowExecutionResult` (and ordered `FlowEvent` entries from
`FlowExecutionHandle.getEventsAfter(cursor)` or `FlowExecutionStore.readEvents(...)`):

```java
public void recordOutcome(MeterRegistry meterRegistry, FlowExecutionResult result) {
    meterRegistry.counter("txflow.executions.terminal",
        "state", result.state().name()).increment();

    FlowError error = result.error();
    if (error != null) {
        meterRegistry.counter("txflow.executions.error",
            "code", error.code(),
            "category", error.category().name()).increment();
    }
}
```

## Async Processing with Virtual Threads (Java 21+)

```java
@Configuration
public class AsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService txFlowTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService txFlowMaintenanceExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public FlowEngine flowEngine(BackendService backendService,
                                 SignerRegistry signerRegistry,
                                 FlowExecutionStore store,
                                 ExecutorService txFlowTaskExecutor,
                                 ExecutorService txFlowMaintenanceExecutor) {
        return FlowEngine.builder(backendService)
            .executor(txFlowTaskExecutor)
            .maintenanceExecutor(txFlowMaintenanceExecutor)
            .signerRegistry(signerRegistry)
            .store(store)
            .build();
    }
}
```

## Health Check

```java
@Component
public class CardanoHealthIndicator implements HealthIndicator {

    private final BackendService backendService;

    public CardanoHealthIndicator(BackendService backendService) {
        this.backendService = backendService;
    }

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

## Multi-Step Flow Example

```java
@Service
public class EscrowService {

    private final FlowEngine flowEngine;

    public EscrowService(FlowEngine flowEngine) {
        this.flowEngine = flowEngine;
    }

    public FlowExecutionHandle createEscrow(String orderId, String buyerRef, String escrowRef,
                                            String escrowAddress, String sellerAddress,
                                            BigInteger amount) {
        TxPlan depositPlan = TxPlan.from(new Tx()
                .payToAddress(escrowAddress, Amount.lovelace(amount))
                .fromRef(buyerRef))
            .withSigner(buyerRef);

        TxPlan releasePlan = TxPlan.from(new Tx()
                .payToAddress(sellerAddress, Amount.lovelace(amount))
                .fromRef(escrowRef))
            .withSigner(escrowRef);

        TxFlow flow = TxFlow.builder("escrow")
            .withDescription("Escrow workflow")
            .addStep(FlowStep.builder("deposit")
                .withDescription("Buyer deposits to escrow")
                .withTxPlan(depositPlan)
                .build())
            .addStep(FlowStep.builder("release")
                .withDescription("Release from escrow to seller")
                .needs("deposit")
                .fundsFrom("deposit")
                .withTxPlan(releasePlan)
                .build())
            .build();

        return flowEngine.start(FlowExecutionRequest.builder(flow)
            .executionId("escrow-" + orderId)
            .idempotency("escrow", orderId)
            .spendingResource(buyerRef)
            .spendingResource(escrowRef)
            .build());
    }
}
```

## See Also

- [Virtual Threads Support](VIRTUAL_THREADS.md)
- [Durable Runtime Contract](DURABLE_RUNTIME.md)
