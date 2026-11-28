# Virtual Threads Support in TxFlow

This guide explains how to use Java 21+ virtual threads with `FlowEngine` for improved scalability.

## Overview

TxFlow supports virtual threads through standard `Executor` abstractions. `FlowEngine` never
constructs or shuts down an application executor. Java 21+ applications can supply a virtual-thread
executor to `FlowEngine.Builder.executor(...)` and `FlowEngine.Builder.maintenanceExecutor(...)`.
For scripts and small applications, `FlowRuntime` manages its own executors automatically.

## Why Virtual Threads?

Virtual threads are lightweight threads managed by the JVM, not the OS. Benefits for TxFlow:

- **Massive Concurrency**: Handle thousands of concurrent flows without thread pool tuning
- **I/O Efficiency**: Confirmation tracking involves polling — virtual threads handle this efficiently
- **Simple Code**: No reactive programming complexity needed
- **Same API**: Works with `FlowEngine` without changing TxFlow code or dependencies

## Usage

### Basic Setup (Java 21+)

```java
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionHandle;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import java.util.concurrent.Executors;

try (var virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
    FlowEngine engine = FlowEngine.builder(backendService)
        .executor(virtualThreadExecutor)
        .signerRegistry(signerRegistry)
        .build();

    // Each start() call runs on its own virtual thread
    FlowExecutionHandle handle1 = engine.start(FlowExecutionRequest.builder(flow1).build());
    FlowExecutionHandle handle2 = engine.start(FlowExecutionRequest.builder(flow2).build());
    FlowExecutionHandle handle3 = engine.start(FlowExecutionRequest.builder(flow3).build());

    // Await the handles before leaving the executor's application-managed scope.
    handle1.await();
    handle2.await();
    handle3.await();
}
```

### Spring Boot Integration (Java 21+)

```java
@Configuration
public class TxFlowConfig {

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

### With Custom Thread Factory

```java
// Named virtual threads for debugging
ThreadFactory factory = Thread.ofVirtual()
    .name("txflow-", 0)
    .factory();

ExecutorService executor = Executors.newThreadPerTaskExecutor(factory);

FlowEngine engine = FlowEngine.builder(backendService)
    .executor(executor)
    .signerRegistry(signerRegistry)
    .build();

// Shut down executor when the application component that owns it stops.
```

## Scaling Characteristics

| Scenario | Platform Threads | Virtual Threads |
|----------|-----------------|-----------------|
| 10 concurrent flows | Works fine | Works fine |
| 100 concurrent flows | May need tuning | Works fine |
| 1000 concurrent flows | Thread pool limits | Works fine |
| 10000 concurrent flows | Difficult | Works fine |

## Executor Ownership

`FlowEngine` accepts `Executor` rather than creating a pool. The application owns its executor lifecycle:

- Java 17 applications can supply an appropriately bounded platform-thread executor.
- Java 21 applications can supply `Executors.newVirtualThreadPerTaskExecutor()`.
- Durable `FlowEngine` instances (those configured with a `FlowExecutionStore`) must pass a separate
  executor to `FlowEngine.Builder.maintenanceExecutor(...)`, so blocked flow tasks cannot starve
  lease renewal; it may also be a virtual-thread executor.
- Close or shut down executor services only from the application component that created them.

All retry, confirmation, cancellation, contention, and lease-renewal work stays behind executor and
scheduler abstractions. `FlowEngine` does not create raw threads, executor services, or scheduled pools.

## Best Practices

### 1. Share the Engine and Executors Across the Application

```java
// Good: Share one immutable FlowEngine and its executors across the application
@Bean(destroyMethod = "shutdown")
public ExecutorService sharedVirtualExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

### 2. Keep Application Callbacks Short

Virtual threads make blocking backend calls inexpensive, but custom resource catalogs, signers, and
event consumers should still avoid long critical sections. TxFlow's waits go through its scheduler
seam, and spending contention uses interruptible lock waits; neither choice requires TxFlow to know
whether the caller supplied platform or virtual threads.

### 3. Monitor with JFR

Java Flight Recorder supports virtual thread events:

```bash
java -XX:StartFlightRecording:filename=recording.jfr \
     -XX:+UnlockDiagnosticVMOptions \
     -XX:+DebugNonSafepoints \
     -jar your-app.jar
```

## Java 17 Baseline

This TxFlow runtime is built and tested with Java 17. The library does not reference Java 21 APIs,
so applications running on Java 21+ can provide a virtual-thread executor without reflection or a
second TxFlow artifact.

### Java 17 Alternative

For Java 17 applications needing high concurrency:

```java
ExecutorService executor = new ThreadPoolExecutor(
    10,                      // core pool size
    1000,                    // max pool size
    60L, TimeUnit.SECONDS,   // keep-alive
    new SynchronousQueue<>(),
    new ThreadPoolExecutor.CallerRunsPolicy()
);

FlowEngine engine = FlowEngine.builder(backendService)
    .executor(executor)
    .signerRegistry(signerRegistry)
    .build();
```

## Example: High-Throughput Payment Processing

```java
@Service
public class PaymentService {

    private final FlowEngine engine;
    private final AtomicInteger activeFlows = new AtomicInteger(0);

    public PaymentService(FlowEngine engine) {
        this.engine = engine;
    }

    public CompletionStage<FlowExecutionResult> processPayment(PaymentRequest request) {
        TxFlow flow = buildPaymentFlow(request);
        FlowExecutionRequest execRequest = FlowExecutionRequest.builder(flow)
            .executionId("payment-" + request.getOrderId())
            .idempotency("payments", request.getOrderId())
            .build();

        activeFlows.incrementAndGet();
        FlowExecutionHandle handle = engine.start(execRequest);
        return handle.completion().whenComplete((result, error) -> activeFlows.decrementAndGet());
    }

    public int getActiveFlowCount() {
        return activeFlows.get();
    }
}
```

## See Also

- [Spring Boot Integration](SPRING_BOOT_INTEGRATION.md)
- [Durable Runtime Contract](DURABLE_RUNTIME.md)
- [Java Virtual Threads Documentation](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
