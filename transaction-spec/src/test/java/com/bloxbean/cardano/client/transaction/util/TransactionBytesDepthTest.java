package com.bloxbean.cardano.client.transaction.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link TransactionBytesDepthFixtures} on a platform thread with the default stack, in a forked interpreted-only
 * JVM and on a virtual thread. No test sets {@code -Xss}.
 */
class TransactionBytesDepthTest {

    @Test
    void onPlatformThreadWithDefaultStack() throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> runFixtures(failure), "tx-bytes-depth-default-stack");
        thread.start();
        thread.join();
        rethrow(failure);
    }

    @Test
    void inForkedInterpretedJvm() throws IOException, InterruptedException {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process process = new ProcessBuilder(List.of(java, "-Xint", "-cp", System.getProperty("java.class.path"),
                TransactionBytesDepthFixtures.class.getName()))
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished)
            process.destroyForcibly();
        assertThat(finished).as("forked -Xint JVM finished in time").isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("ok: preprod trigger");
    }

    // Created reflectively so the class compiles on Java 17; the JDK 21 CI job runs it.
    @Test
    @EnabledIf("javaAtLeast21")
    void onVirtualThread() throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = (Thread) Thread.class.getMethod("startVirtualThread", Runnable.class)
                .invoke(null, (Runnable) () -> runFixtures(failure));
        thread.join();
        rethrow(failure);
    }

    static boolean javaAtLeast21() {
        return Runtime.version().feature() >= 21;
    }

    private static void runFixtures(AtomicReference<Throwable> failure) {
        try {
            TransactionBytesDepthFixtures.all().forEach(TransactionBytesDepthFixtures::verify);
        } catch (Throwable t) {
            failure.set(t);
        }
    }

    private static void rethrow(AtomicReference<Throwable> failure) throws Throwable {
        if (failure.get() != null)
            throw failure.get();
    }
}
