package com.bloxbean.cardano.client.txflow.result;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlowResultTest {

    // ==================== FlowResult Tests ====================

    @Test
    void testIsSuccessful_completedStatus() {
        FlowResult result = FlowResult.builder("flow-1")
                .withStatus(FlowStatus.COMPLETED)
                .build();

        assertTrue(result.isSuccessful());
    }

    @Test
    void testIsSuccessful_failedStatus() {
        FlowResult result = FlowResult.builder("flow-1")
                .withStatus(FlowStatus.FAILED)
                .build();

        assertFalse(result.isSuccessful());
    }

    @Test
    void testIsFailed_failedStatus() {
        FlowResult result = FlowResult.builder("flow-1")
                .withStatus(FlowStatus.FAILED)
                .build();

        assertTrue(result.isFailed());
    }

    @Test
    void testIsFailed_completedStatus() {
        FlowResult result = FlowResult.builder("flow-1")
                .withStatus(FlowStatus.COMPLETED)
                .build();

        assertFalse(result.isFailed());
    }

    @Test
    void testGetDuration_withTimestamps() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-01T00:01:30Z");

        FlowResult result = FlowResult.builder("flow-1")
                .startedAt(start)
                .completedAt(end)
                .build();

        assertEquals(Duration.ofSeconds(90), result.getDuration());
    }

    @Test
    void testGetDuration_nullTimestamps_returnsZero() {
        FlowResult result = FlowResult.builder("flow-1").build();

        assertEquals(Duration.ZERO, result.getDuration());
    }

    @Test
    void testGetDuration_nullStartedAt_returnsZero() {
        FlowResult result = FlowResult.builder("flow-1")
                .completedAt(Instant.now())
                .build();

        assertEquals(Duration.ZERO, result.getDuration());
    }

    @Test
    void testGetDuration_nullCompletedAt_returnsZero() {
        FlowResult result = FlowResult.builder("flow-1")
                .startedAt(Instant.now())
                .build();

        assertEquals(Duration.ZERO, result.getDuration());
    }

    @Test
    void testGetCompletedStepCount_mixedResults() {
        FlowResult result = FlowResult.builder("flow-1")
                .addStepResult(FlowStepResult.success("step-1", "tx-1", List.of()))
                .addStepResult(FlowStepResult.failure("step-2", new RuntimeException("fail")))
                .addStepResult(FlowStepResult.success("step-3", "tx-3", List.of()))
                .build();

        assertEquals(2, result.getCompletedStepCount());
    }

    @Test
    void testGetTotalStepCount() {
        FlowResult result = FlowResult.builder("flow-1")
                .addStepResult(FlowStepResult.success("step-1", "tx-1", List.of()))
                .addStepResult(FlowStepResult.failure("step-2", new RuntimeException("fail")))
                .addStepResult(FlowStepResult.success("step-3", "tx-3", List.of()))
                .build();

        assertEquals(3, result.getTotalStepCount());
    }

    @Test
    void testGetStepResult_found() {
        FlowStepResult step = FlowStepResult.success("step-2", "tx-2", List.of());
        FlowResult result = FlowResult.builder("flow-1")
                .addStepResult(FlowStepResult.success("step-1", "tx-1", List.of()))
                .addStepResult(step)
                .build();

        assertTrue(result.getStepResult("step-2").isPresent());
        assertEquals("tx-2", result.getStepResult("step-2").get().getTransactionHash());
    }

    @Test
    void testGetStepResult_notFound() {
        FlowResult result = FlowResult.builder("flow-1")
                .addStepResult(FlowStepResult.success("step-1", "tx-1", List.of()))
                .build();

        assertFalse(result.getStepResult("nonexistent").isPresent());
    }

    @Test
    void testGetTransactionHashes_filtersSuccessful() {
        FlowResult result = FlowResult.builder("flow-1")
                .addStepResult(FlowStepResult.success("step-1", "tx-aaa", List.of()))
                .addStepResult(FlowStepResult.failure("step-2", new RuntimeException("fail")))
                .addStepResult(FlowStepResult.success("step-3", "tx-ccc", List.of()))
                .build();

        List<String> hashes = result.getTransactionHashes();
        assertEquals(2, hashes.size());
        assertEquals("tx-aaa", hashes.get(0));
        assertEquals("tx-ccc", hashes.get(1));
    }

    @Test
    void testGetFailedStep_found() {
        FlowResult result = FlowResult.builder("flow-1")
                .addStepResult(FlowStepResult.success("step-1", "tx-1", List.of()))
                .addStepResult(FlowStepResult.failure("step-2", new RuntimeException("boom")))
                .build();

        assertTrue(result.getFailedStep().isPresent());
        assertEquals("step-2", result.getFailedStep().get().getStepId());
    }

    @Test
    void testGetFailedStep_noneFound() {
        FlowResult result = FlowResult.builder("flow-1")
                .addStepResult(FlowStepResult.success("step-1", "tx-1", List.of()))
                .build();

        assertFalse(result.getFailedStep().isPresent());
    }

    @Test
    void testBuilderSuccess_setsStatusAndCompletedAt() {
        FlowResult result = FlowResult.builder("flow-1")
                .startedAt(Instant.now())
                .success();

        assertEquals(FlowStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void testBuilderFailure_setsStatusErrorAndCompletedAt() {
        RuntimeException error = new RuntimeException("something went wrong");
        FlowResult result = FlowResult.builder("flow-1")
                .startedAt(Instant.now())
                .failure(error);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertEquals(error, result.getError());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void testStepResultsImmutable() {
        FlowResult result = FlowResult.builder("flow-1")
                .addStepResult(FlowStepResult.success("step-1", "tx-1", List.of()))
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                result.getStepResults().add(FlowStepResult.success("step-2", "tx-2", List.of()))
        );
    }

    @Test
    void testWithStepResults_replacesExisting() {
        FlowStepResult step1 = FlowStepResult.success("step-1", "tx-1", List.of());
        FlowStepResult step2 = FlowStepResult.success("step-2", "tx-2", List.of());

        FlowResult result = FlowResult.builder("flow-1")
                .addStepResult(FlowStepResult.failure("old-step", new RuntimeException("old")))
                .withStepResults(List.of(step1, step2))
                .build();

        assertEquals(2, result.getTotalStepCount());
        assertEquals("step-1", result.getStepResults().get(0).getStepId());
    }

    // ==================== FlowStepResult Tests ====================

    @Test
    void testSuccessFactory_withSpentInputs() {
        List<Utxo> utxos = List.of(
                Utxo.builder().txHash("tx-1").outputIndex(0).build()
        );
        List<TransactionInput> spentInputs = List.of(
                new TransactionInput("abcd1234", 0)
        );

        FlowStepResult result = FlowStepResult.success("step-1", "tx-hash", utxos, spentInputs);

        assertTrue(result.isSuccessful());
        assertEquals(FlowStatus.COMPLETED, result.getStatus());
        assertEquals("step-1", result.getStepId());
        assertEquals("tx-hash", result.getTransactionHash());
        assertEquals(1, result.getOutputUtxos().size());
        assertEquals(1, result.getSpentInputs().size());
        assertNull(result.getError());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void testSuccessFactory_withoutSpentInputs() {
        List<Utxo> utxos = List.of(
                Utxo.builder().txHash("tx-1").outputIndex(0).build()
        );

        FlowStepResult result = FlowStepResult.success("step-1", "tx-hash", utxos);

        assertTrue(result.isSuccessful());
        assertEquals(FlowStatus.COMPLETED, result.getStatus());
        assertTrue(result.getSpentInputs().isEmpty());
    }

    @Test
    void testFailureFactory_setsFieldsCorrectly() {
        RuntimeException error = new RuntimeException("tx failed");
        FlowStepResult result = FlowStepResult.failure("step-1", error);

        assertFalse(result.isSuccessful());
        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertEquals("step-1", result.getStepId());
        assertNull(result.getTransactionHash());
        assertTrue(result.getOutputUtxos().isEmpty());
        assertTrue(result.getSpentInputs().isEmpty());
        assertEquals(error, result.getError());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void testIsSuccessful_derivedFromStatus() {
        FlowStepResult completed = new FlowStepResult("step-1", FlowStatus.COMPLETED, "tx-1", List.of());
        FlowStepResult failed = new FlowStepResult("step-2", FlowStatus.FAILED, null, List.of());
        FlowStepResult pending = new FlowStepResult("step-3", FlowStatus.PENDING, null, List.of());

        assertTrue(completed.isSuccessful());
        assertFalse(failed.isSuccessful());
        assertFalse(pending.isSuccessful());
    }

    @Test
    void testOutputUtxosImmutable() {
        List<Utxo> utxos = new ArrayList<>();
        utxos.add(Utxo.builder().txHash("tx-1").outputIndex(0).build());

        FlowStepResult result = FlowStepResult.success("step-1", "tx-hash", utxos);

        assertThrows(UnsupportedOperationException.class, () ->
                result.getOutputUtxos().add(Utxo.builder().txHash("tx-2").outputIndex(1).build())
        );
    }

    @Test
    void testSpentInputsImmutable() {
        List<TransactionInput> inputs = new ArrayList<>();
        inputs.add(new TransactionInput("abcd1234", 0));

        FlowStepResult result = FlowStepResult.success("step-1", "tx-hash", List.of(), inputs);

        assertThrows(UnsupportedOperationException.class, () ->
                result.getSpentInputs().add(new TransactionInput("efgh5678", 1))
        );
    }

    @Test
    void testNullOutputUtxos_defaultsToEmptyList() {
        FlowStepResult result = new FlowStepResult("step-1", FlowStatus.COMPLETED, "tx-1", null);

        assertNotNull(result.getOutputUtxos());
        assertTrue(result.getOutputUtxos().isEmpty());
    }

    @Test
    void testNullSpentInputs_defaultsToEmptyList() {
        FlowStepResult result = new FlowStepResult("step-1", FlowStatus.COMPLETED, "tx-1", null, null);

        assertNotNull(result.getSpentInputs());
        assertTrue(result.getSpentInputs().isEmpty());
    }
}
