package com.bloxbean.cardano.client.common.cbor;

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
 * Runs {@link DataItemCodecDepthFixtures} (the codec on deeply nested input) on a platform thread with the default stack, in a forked interpreted-only JVM and on
 * a virtual thread. No test sets {@code -Xss}: the walker must not depend on the stack size.
 */
class DataItemCodecDepthTest {

    @Test
    void onPlatformThreadWithDefaultStack() throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> runFixtures(failure), "cbor-codec-depth-default-stack");
        long start = System.nanoTime();
        thread.start();
        thread.join();
        rethrow(failure);
        // nesting must not make the codec super-linear: all fixtures together take well under a second when warm
        assertThat(System.nanoTime() - start).as("depth fixtures took too long").isLessThan(30_000_000_000L);
    }

    @Test
    void inForkedInterpretedJvm() throws IOException, InterruptedException {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process process = new ProcessBuilder(List.of(java, "-Xint", "-cp", System.getProperty("java.class.path"),
                DataItemCodecDepthFixtures.class.getName()))
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished)
            process.destroyForcibly();
        assertThat(finished).as("forked -Xint JVM finished in time").isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("ok: definite list (200,000 levels)").contains("ok: tx preprod trigger");
    }

    // Created reflectively so the class compiles on Java 17; the JDK 21 CI job runs it. (The JUnit 5.9 API on the
    // compile classpath has no JRE.JAVA_21, hence @EnabledIf.)
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
            DataItemCodecDepthFixtures.all().forEach(DataItemCodecDepthFixtures::verify);
            DataItemCodecDepthFixtures.real().forEach(DataItemCodecDepthFixtures::verifyReal);
        } catch (Throwable t) {
            failure.set(t);
        }
    }

    private static void rethrow(AtomicReference<Throwable> failure) throws Throwable {
        if (failure.get() != null)
            throw failure.get();
    }
}
