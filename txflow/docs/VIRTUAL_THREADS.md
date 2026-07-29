# Virtual Threads Support in TxFlow

This guide explains how to use Java 21+ virtual threads with TxFlow for improved scalability.

## Overview

TxFlow supports virtual threads through executor abstractions. It never constructs or shuts down an application executor. Java 21+ applications can supply a virtual-thread executor to `FlowExecutor.withExecutor(...)`, `FlowEngine.Builder.executor(...)`, cleanup builders, and durable-runtime maintenance scheduling.

## Why Virtual Threads?

Virtual threads are lightweight threads managed by the JVM, not the OS. Benefits for TxFlow:

- **Massive Concurrency**: Handle thousands of concurrent flows without thread pool tuning
- **I/O Efficiency**: Confirmation tracking involves polling - virtual threads handle this efficiently
- **Simple Code**: No reactive programming complexity needed
- **Same API**: Works with existing `FlowExecutor` API

## Usage

### Basic Setup (Java 21+)

```java
import java.util.concurrent.Executors;

try (var virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
    FlowExecutor executor = FlowExecutor.create(backendService)
        .withExecutor(virtualThreadExecutor)
        .withConfirmationConfig(ConfirmationConfig.defaults())
        .withListener(myListener);

    // Each execute() call runs on its own virtual thread
    FlowHandle handle1 = executor.execute(flow1);
    FlowHandle handle2 = executor.execute(flow2);
    FlowHandle handle3 = executor.execute(flow3);
    // Await the handles before leaving the executor's application-managed scope.
}
```

### Spring Boot Integration (Java 21+)

```java
@Configuration
public class TxFlowConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public FlowExecutor flowExecutor(BackendService backendService,
                                     ExecutorService virtualThreadExecutor) {
        return FlowExecutor.create(backendService)
            .withExecutor(virtualThreadExecutor)
            .withConfirmationConfig(ConfirmationConfig.defaults());
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

FlowExecutor flowExecutor = FlowExecutor.create(backendService)
    .withExecutor(executor);

// Shut down executor when the application component that owns it stops.
```

## Scaling Characteristics

| Scenario | Platform Threads | Virtual Threads |
|----------|-----------------|-----------------|
| 10 concurrent flows | Works fine | Works fine |
| 100 concurrent flows | May need tuning | Works fine |
| 1000 concurrent flows | Thread pool limits | Works fine |
| 10000 concurrent flows | Difficult | Works fine |

## Executor ownership

TxFlow accepts `Executor` rather than creating a pool. The application owns its executor lifecycle:

- Java 17 applications can supply an appropriately bounded platform-thread executor.
- Java 21 applications can supply `Executors.newVirtualThreadPerTaskExecutor()`.
- Durable `FlowEngine` instances (those configured with a store) must pass a separate executor to
  `FlowEngine.Builder.maintenanceExecutor(...)`, so blocked flow tasks cannot starve lease renewal;
  it may also be a virtual-thread executor.
- `InMemoryFlowRegistry` and `InMemoryFlowStateStore` auto-cleanup requires an explicit
  application-managed executor via `withCleanupExecutor(...)`; neither falls back to the common pool.
- Close or shut down executor services only from the application component that created them.

All retry, confirmation, cancellation, contention, and lease-renewal work stays behind executor and
scheduler abstractions. The runtime does not create a raw thread, executor service, or scheduled pool.

## Best Practices

### 1. One Executor Per Application

```java
// Good: Share executor across all FlowExecutor instances
@Bean(destroyMethod = "shutdown")
public ExecutorService sharedVirtualExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}

// Use the same executor for all flows
flowExecutor1.withExecutor(sharedVirtualExecutor);
flowExecutor2.withExecutor(sharedVirtualExecutor);
```

### 2. Keep application callbacks short

Virtual threads make blocking backend calls inexpensive, but listeners and resource resolvers should still
avoid long critical sections. TxFlow's waits go through its scheduler seam, and spending contention uses
interruptible lock waits; neither choice requires TxFlow to know whether the caller supplied platform or
virtual threads.

### 3. Monitor with JFR

Java Flight Recorder supports virtual thread events:

```bash
java -XX:StartFlightRecording:filename=recording.jfr \
     -XX:+UnlockDiagnosticVMOptions \
     -XX:+DebugNonSafepoints \
     -jar your-app.jar
```

## Java 17 Baseline

This TxFlow runtime is built and tested with Java 17. The library does not reference Java 21 APIs, so applications running on Java 21+ can provide a virtual-thread executor without reflection or a second TxFlow artifact.

### Java 17 Alternative

For Java 17 applications needing high concurrency:

```java
// Use a cached thread pool with reasonable limits
ExecutorService executor = new ThreadPoolExecutor(
    10,                      // core pool size
    1000,                    // max pool size
    60L, TimeUnit.SECONDS,   // keep-alive
    new SynchronousQueue<>(),
    new ThreadPoolExecutor.CallerRunsPolicy()
);

FlowExecutor flowExecutor = FlowExecutor.create(backendService)
    .withExecutor(executor);
```

## Example: High-Throughput Payment Processing

```java
@Service
public class PaymentService {

    private final FlowExecutor executor;
    private final AtomicInteger activeFlows = new AtomicInteger(0);

    public PaymentService(BackendService backendService,
                          ExecutorService applicationVirtualThreadExecutor) {
        this.executor = FlowExecutor.create(backendService)
            .withExecutor(applicationVirtualThreadExecutor)
            .withConfirmationConfig(ConfirmationConfig.defaults())
            .withListener(new FlowListener() {
                @Override
                public void onFlowStarted(TxFlow flow) {
                    activeFlows.incrementAndGet();
                }

                @Override
                public void onFlowCompleted(TxFlow flow, FlowResult result) {
                    activeFlows.decrementAndGet();
                }

                @Override
                public void onFlowFailed(TxFlow flow, FlowResult result) {
                    activeFlows.decrementAndGet();
                }
            });
    }

    public CompletableFuture<FlowResult> processPayment(PaymentRequest request) {
        TxFlow flow = buildPaymentFlow(request);
        FlowHandle handle = executor.execute(flow);
        return handle.getResultFuture();
    }

    public int getActiveFlowCount() {
        return activeFlows.get();
    }
}
```

## See Also

- [Spring Boot Integration](SPRING_BOOT_INTEGRATION.md)
- [FlowListener Patterns](FLOWLISTENER_PATTERNS.md)
- [Java Virtual Threads Documentation](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
