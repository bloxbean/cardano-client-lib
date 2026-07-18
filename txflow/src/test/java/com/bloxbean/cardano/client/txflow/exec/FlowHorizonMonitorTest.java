package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.txflow.FlowStep;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.bloxbean.cardano.client.txflow.exec.ScriptedChainBackend.Observation.absent;
import static com.bloxbean.cardano.client.txflow.exec.ScriptedChainBackend.Observation.included;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class FlowHorizonMonitorTest {
    @Test
    void detectsRollbackOfAnEarlierAttemptAtTheTerminalHorizon() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "a"), absent(101), absent(102));
        ConfirmationConfig config = ConfirmationConfig.builder().minConfirmations(0)
                .checkInterval(Duration.ofSeconds(1)).timeout(Duration.ofSeconds(5)).build();
        ConfirmationTracker tracker = new ConfirmationTracker(chain, config, scheduler);
        assertEquals(ConfirmationStatus.CONFIRMED, tracker.checkStatus("producer").getStatus());

        FlowStep step = FlowStep.builder("producer").withTxContext(builder -> null).build();
        FlowHorizonMonitor monitor = new FlowHorizonMonitor(tracker);
        monitor.track(step, "producer");
        FlowHorizonMonitor.HorizonResult result = monitor.verify(() -> false);

        assertEquals("producer", result.step().getId());
        assertEquals(ConfirmationStatus.ROLLED_BACK, result.confirmation().getStatus());
    }

    @Test
    void returnsNoFailureWhenEveryAttemptRemainsConfirmed() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "a"), included(101, 100, "a"));
        ConfirmationConfig config = ConfirmationConfig.builder().minConfirmations(0).build();
        ConfirmationTracker tracker = new ConfirmationTracker(chain, config, scheduler);
        assertEquals(ConfirmationStatus.CONFIRMED, tracker.checkStatus("producer").getStatus());
        FlowStep step = FlowStep.builder("producer").withTxContext(builder -> null).build();

        assertNull(new FlowHorizonMonitor(tracker)
                .verify(List.of(step), List.of("producer"), () -> false));
    }

    @Test
    void reconcilesMultipleHashesUnderOneBudgetAndOneTipReadPerPass() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        CountingChainBackend chain = new CountingChainBackend();
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(0)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(3))
                .build();
        ConfirmationTracker tracker = new ConfirmationTracker(chain, config, scheduler);
        assertEquals(ConfirmationStatus.CONFIRMED, tracker.checkStatus("one").getStatus());
        assertEquals(ConfirmationStatus.CONFIRMED, tracker.checkStatus("two").getStatus());
        chain.setAbsent("one", "two");
        chain.resetCounts();

        List<FlowStep> steps = List.of(
                FlowStep.builder("one").withTxContext(builder -> null).build(),
                FlowStep.builder("two").withTxContext(builder -> null).build());
        FlowHorizonMonitor.HorizonResult result = new FlowHorizonMonitor(tracker)
                .verify(steps, List.of("one", "two"), () -> false);

        assertInstanceOf(ReconciliationUncertainException.class,
                result.confirmation().getError());
        assertEquals(3, chain.tipQueries.get());
        assertEquals(3, chain.transactionQueries.get("one").get());
        assertEquals(3, chain.transactionQueries.get("two").get());
        assertEquals(Duration.ofSeconds(3), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    private static final class CountingChainBackend
            implements ChainDataSupplier, TransactionObservationCapabilities {
        private final Set<String> absent = new HashSet<>();
        private final AtomicInteger tipQueries = new AtomicInteger();
        private final java.util.Map<String, AtomicInteger> transactionQueries =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public long getChainTipHeight() {
            tipQueries.incrementAndGet();
            return 100;
        }

        @Override
        public Optional<TransactionInfo> getTransactionInfo(String txHash) throws ApiException {
            transactionQueries.computeIfAbsent(txHash, ignored -> new AtomicInteger())
                    .incrementAndGet();
            if (absent.contains(txHash)) return Optional.empty();
            return Optional.of(TransactionInfo.builder().txHash(txHash)
                    .blockHeight(100L).blockHash("block").build());
        }

        @Override
        public boolean supportsAuthoritativeAbsence() {
            return false;
        }

        void setAbsent(String... hashes) {
            absent.addAll(List.of(hashes));
        }

        void resetCounts() {
            tipQueries.set(0);
            transactionQueries.values().forEach(counter -> counter.set(0));
        }
    }
}
