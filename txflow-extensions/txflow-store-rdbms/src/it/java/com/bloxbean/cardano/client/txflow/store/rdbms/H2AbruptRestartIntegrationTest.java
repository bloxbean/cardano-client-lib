package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Verifies committed H2 state after the writer JVM is killed without closing the database. */
class H2AbruptRestartIntegrationTest {
    private static final Instant RECOVERY_TIME = Instant.parse("2026-07-14T01:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest
    @EnumSource(value = AttemptState.class, names = {
            "SIGNED", "SUBMITTING", "SUBMITTED", "IN_BLOCK", "CONFIRMED"
    })
    @Timeout(30)
    @SuppressWarnings("unchecked")
    void committedAttemptSurvivesForcibleWriterTermination(AttemptState expectedState)
            throws Exception {
        Path database = temporaryDirectory.resolve(
                "txflow-crash-" + expectedState.name().toLowerCase());
        Path marker = temporaryDirectory.resolve(expectedState.name() + ".ready");
        String jdbcUrl = "jdbc:h2:file:" + database.toAbsolutePath();
        Process child = startWriter(jdbcUrl, expectedState, marker);
        waitForCommitMarker(child, marker);
        child.destroyForcibly();
        assertTrue(child.waitFor(10, TimeUnit.SECONDS), "writer JVM did not terminate");

        String executionId = "crash-" + expectedState.name().toLowerCase();
        try (RdbmsFlowExecutionStore recovered = RdbmsFlowExecutionStore.builder()
                .jdbcUrl(jdbcUrl)
                .schemaManagement(SchemaManagement.VALIDATE)
                .clock(Clock.fixed(RECOVERY_TIME, ZoneOffset.UTC))
                .build()) {
            FlowExecutionSnapshot snapshot = recovered.get(executionId).orElseThrow();
            Map<String, FlowAttemptSnapshot> attempts =
                    (Map<String, FlowAttemptSnapshot>) snapshot.data().get("attempts");
            assertEquals(expectedState, attempts.get("step#1").state());
            assertEquals(expectedState == AttemptState.CONFIRMED
                            ? com.bloxbean.cardano.client.txflow.exec.FlowExecutionState.COMPLETED
                            : com.bloxbean.cardano.client.txflow.exec.FlowExecutionState.RUNNING,
                    snapshot.state());
            assertEquals(expectedState.name(), recovered.readEvents(executionId, 0, 10)
                    .events().get(0).details().get("attempt_state"));

            long previousEpoch = (Long) snapshot.data().get("execution_lease_epoch");
            ExecutionLease takeover = recovered.acquireExecutionLease(
                    executionId, "recovery-owner", RECOVERY_TIME, Duration.ofMinutes(1));
            assertTrue(takeover.epoch() > previousEpoch);
        }
    }

    private Process startWriter(String jdbcUrl, AttemptState state, Path marker)
            throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        return new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                H2CrashWriter.class.getName(), jdbcUrl, state.name(), marker.toString())
                .redirectErrorStream(true)
                .start();
    }

    private void waitForCommitMarker(Process child, Path marker) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!Files.exists(marker) && child.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        if (Files.exists(marker)) return;
        if (child.isAlive()) child.destroyForcibly();
        child.waitFor(5, TimeUnit.SECONDS);
        String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        fail("writer JVM did not commit before termination; output=" + output);
    }
}
