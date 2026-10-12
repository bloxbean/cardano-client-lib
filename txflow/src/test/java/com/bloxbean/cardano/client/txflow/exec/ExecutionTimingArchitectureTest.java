package com.bloxbean.cardano.client.txflow.exec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ExecutionTimingArchitectureTest {

    @Test
    void executorAndTrackerDoNotBypassFlowScheduler() throws IOException {
        assertDoesNotContain("FlowExecutor.java", "Thread.sleep", "Instant.now", "System.currentTimeMillis");
        assertDoesNotContain("ConfirmationTracker.java", "Thread.sleep", "Instant.now", "System.currentTimeMillis");
        assertDoesNotContain("SpendingResourceCoordinator.java", "Thread.sleep", "System.nanoTime");
    }

    @Test
    void allExecutionInfrastructureUsesTheTimingSeam() throws IOException {
        Path sourceRoot = Path.of("src/main/java/com/bloxbean/cardano/client/txflow");
        try (Stream<Path> sources = Files.walk(sourceRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String fileName = source.getFileName().toString();
                if (fileName.equals("FlowScheduler.java")
                        || fileName.equals("TxStreamScheduler.java")) continue;
                String code = sourceCode(source);
                assertFalse(code.contains("Instant.now("), source + " must use an injected clock/scheduler");
                assertFalse(code.contains("System.currentTimeMillis("),
                        source + " must use an injected clock/scheduler");
                assertFalse(code.contains("System.nanoTime("),
                        source + " must use an injected monotonic clock/scheduler");
                assertFalse(code.contains("Thread.sleep("), source + " must use FlowScheduler");
            }
        }
    }

    @Test
    void productionCodeDoesNotConstructThreadsOrExecutorServices() throws IOException {
        Path sourceRoot = Path.of("src/main/java/com/bloxbean/cardano/client/txflow");
        try (Stream<Path> sources = Files.walk(sourceRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (source.getFileName().toString().equals("FlowRuntime.java")) {
                    // The optional managed runtime is the one explicit owner of
                    // the executors/threads it creates (ADR 0005 Decision 13).
                    continue;
                }
                String code = sourceCode(source);
                assertFalse(code.contains("new Thread("), source + " must not construct a thread");
                assertFalse(code.matches("(?s).*Executors\\.new[A-Z].*"),
                        source + " must accept executor abstractions instead of creating a pool");
                assertFalse(code.contains("ForkJoinPool.commonPool("),
                        source + " must require an application-managed executor");
            }
        }
    }

    private String sourceCode(Path source) throws IOException {
        return Files.readString(source).replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    @Test
    void systemWaitChecksCancellationInBoundedSlices() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        boolean completed = new SystemFlowScheduler(Clock.systemUTC())
                .sleep(Duration.ofSeconds(10), () -> checks.incrementAndGet() > 1);
        assertFalse(completed);
    }

    private void assertDoesNotContain(String fileName, String... forbiddenTokens) throws IOException {
        Path source = Path.of("src/main/java/com/bloxbean/cardano/client/txflow/exec", fileName);
        String content = Files.readString(source);
        for (String token : forbiddenTokens) {
            assertFalse(content.contains(token), source + " must not call " + token + " directly");
        }
    }
}
