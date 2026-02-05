# Virtual Threads Support in TxFlow

This guide explains how to use Java 21+ virtual threads with TxFlow for improved scalability.

## Overview

TxFlow already supports virtual threads through the existing `withExecutor(Executor)` API. No library changes are needed - Java 21+ applications can immediately benefit from virtual threads for high-concurrency transaction processing.

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

// Create FlowExecutor with virtual thread executor
FlowExecutor executor = FlowExecutor.create(backendService)
    .withExecutor(Executors.newVirtualThreadPerTaskExecutor())
    .withConfirmationConfig(ConfirmationConfig.defaults())
    .withListener(myListener);

// Each execute() call runs on its own virtual thread
FlowHandle handle1 = executor.execute(flow1);
FlowHandle handle2 = executor.execute(flow2);
FlowHandle handle3 = executor.execute(flow3);
// ... can handle thousands more
```

### Spring Boot Integration (Java 21+)

```java
@Configuration
public class TxFlowConfig {

    @Bean
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public FlowExecutor flowExecutor(BackendService backendService,
                                     Executor virtualThreadExecutor) {
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

Executor executor = Executors.newThreadPerTaskExecutor(factory);

FlowExecutor flowExecutor = FlowExecutor.create(backendService)
    .withExecutor(executor);
```

## Scaling Characteristics

| Scenario | Platform Threads | Virtual Threads |
|----------|-----------------|-----------------|
| 10 concurrent flows | Works fine | Works fine |
| 100 concurrent flows | May need tuning | Works fine |
| 1000 concurrent flows | Thread pool limits | Works fine |
| 10000 concurrent flows | Difficult | Works fine |

## Best Practices

### 1. One Executor Per Application

```java
// Good: Share executor across all FlowExecutor instances
@Bean
public Executor sharedVirtualExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}

// Use the same executor for all flows
flowExecutor1.withExecutor(sharedVirtualExecutor);
flowExecutor2.withExecutor(sharedVirtualExecutor);
```

### 2. Avoid Pinning

Virtual threads can be "pinned" to carrier threads in certain situations. TxFlow avoids common pinning causes:
- No synchronized blocks in hot paths
- Uses `Thread.sleep()` which releases the carrier thread

### 3. Monitor with JFR

Java Flight Recorder supports virtual thread events:

```bash
java -XX:StartFlightRecording:filename=recording.jfr \
     -XX:+UnlockDiagnosticVMOptions \
     -XX:+DebugNonSafepoints \
     -jar your-app.jar
```

## Java 11 Compatibility

TxFlow targets Java 11 for maximum compatibility. The library itself doesn't use virtual threads, but applications running on Java 21+ can provide a virtual thread executor.

### Java 11 Alternative

For Java 11 applications needing high concurrency:

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

    public PaymentService(BackendService backendService) {
        this.executor = FlowExecutor.create(backendService)
            .withExecutor(Executors.newVirtualThreadPerTaskExecutor())
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
