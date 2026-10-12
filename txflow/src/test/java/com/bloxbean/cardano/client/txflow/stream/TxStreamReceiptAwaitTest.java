package com.bloxbean.cardano.client.txflow.stream;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxStreamReceiptAwaitTest {

    @Test
    void awaitSettledReturnsEverySettlingOutcomeWithoutInterpretation() {
        for (TxStreamItemStatus status : List.of(TxStreamItemStatus.CONFIRMED,
                TxStreamItemStatus.FAILED, TxStreamItemStatus.CANCELLED,
                TxStreamItemStatus.RECOVERY_REQUIRED)) {
            TxStreamItemResult result = result(status, null, null);
            TxStreamReceipt receipt = settledReceipt(result);

            assertSame(result, receipt.awaitSettled());
            assertSame(result, receipt.awaitSettled(Duration.ofSeconds(1)));
        }
    }

    @Test
    void awaitConfirmedClassifiesOutcomesAndPreservesResultDetails() {
        TxStreamItemResult confirmed = result(TxStreamItemStatus.CONFIRMED, "tx-ok", null);
        assertSame(confirmed, settledReceipt(confirmed).awaitConfirmed());

        TxStreamException underlying = new TxStreamException(
                "TXSTREAM_DISPATCH_FAILED", "engine unavailable");
        TxStreamItemResult failed = result(TxStreamItemStatus.FAILED, "tx-failed",
                new IllegalStateException("wrapper", underlying));
        TxStreamFailedException failedException = assertThrows(TxStreamFailedException.class,
                () -> settledReceipt(failed).awaitConfirmed());
        assertEquals("TXSTREAM_DISPATCH_FAILED", failedException.getCode());
        assertSame(failed, failedException.result());

        TxStreamItemResult cancelled = result(TxStreamItemStatus.CANCELLED, null, null);
        TxStreamCancelledException cancelledException =
                assertThrows(TxStreamCancelledException.class,
                        () -> settledReceipt(cancelled).awaitConfirmed());
        assertEquals("TXSTREAM_ITEM_CANCELLED", cancelledException.getCode());
        assertSame(cancelled, cancelledException.result());

        TxStreamItemResult uncertain = result(
                TxStreamItemStatus.RECOVERY_REQUIRED, "tx-uncertain", null);
        TxStreamUncertainException uncertainException =
                assertThrows(TxStreamUncertainException.class,
                        () -> settledReceipt(uncertain).awaitConfirmed());
        assertEquals("TXSTREAM_RECOVERY_REQUIRED", uncertainException.getCode());
        assertTrue(uncertainException.getMessage().startsWith("DO NOT RESUBMIT:"));
        assertEquals("item-1", uncertainException.itemId());
        assertSame(uncertain, uncertainException.result());
        assertEquals("tx-uncertain", uncertainException.result().getTransactionHash());
        assertTrue(uncertain.isUncertain());
        assertFalse(confirmed.isUncertain());
    }

    @Test
    void failedOutcomeWithoutAStreamCauseUsesGenericCode() {
        TxStreamItemResult failed = result(TxStreamItemStatus.FAILED, null,
                new IllegalArgumentException("bad transaction"));

        TxStreamFailedException exception = assertThrows(TxStreamFailedException.class,
                () -> settledReceipt(failed).awaitConfirmed());

        assertEquals("TXSTREAM_ITEM_FAILED", exception.getCode());
        assertSame(failed.getError(), exception.getCause());
    }

    @Test
    void awaitConfirmedAlwaysClassifiesTheLatestProjectionNotTheSettledSnapshot() {
        for (TxStreamItemStatus repairedStatus : List.of(TxStreamItemStatus.CONFIRMED,
                TxStreamItemStatus.FAILED, TxStreamItemStatus.CANCELLED)) {
            TxStreamItemResult uncertain = result(
                    TxStreamItemStatus.RECOVERY_REQUIRED, "tx-uncertain", null);
            ItemProjection projection = ItemProjection.settled(uncertain);
            projection.advance(repairedStatus, builder -> builder,
                    StubEngineGateway.NOW.plusSeconds(1), true);
            TxStreamReceipt receipt = new TxStreamReceipt("payouts", "item-1", projection,
                    new AtomicLong());

            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    receipt.awaitSettled().getStatus(), "the promise remains point-in-time");
            if (repairedStatus == TxStreamItemStatus.CONFIRMED) {
                assertEquals(repairedStatus, receipt.awaitConfirmed().getStatus());
            } else if (repairedStatus == TxStreamItemStatus.FAILED) {
                assertEquals(repairedStatus, assertThrows(TxStreamFailedException.class,
                        receipt::awaitConfirmed).result().getStatus());
            } else {
                assertEquals(repairedStatus, assertThrows(TxStreamCancelledException.class,
                        receipt::awaitConfirmed).result().getStatus());
            }
        }
    }

    @Test
    void timedWaitValidatesTheBudgetAndDoesNotCancelThePromise() {
        ItemProjection projection = new ItemProjection(
                result(TxStreamItemStatus.ACCEPTED, null, null));
        TxStreamReceipt receipt = new TxStreamReceipt("payouts", "item-1", projection,
                new AtomicLong());

        assertThrows(NullPointerException.class, () -> receipt.awaitSettled(null));
        assertThrows(IllegalArgumentException.class,
                () -> receipt.awaitSettled(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> receipt.awaitConfirmed(Duration.ofNanos(-1)));
        TxStreamTimeoutException timeout = assertThrows(TxStreamTimeoutException.class,
                () -> receipt.awaitConfirmed(Duration.ofMillis(5)));

        assertSame(receipt.current(), timeout.result());
        assertFalse(projection.promise().isDone(), "a caller timeout must not cancel item work");
    }

    @Test
    void interruptionRestoresTheFlagAndReturnsTheStableCode() throws Exception {
        ItemProjection projection = new ItemProjection(
                result(TxStreamItemStatus.ACCEPTED, null, null));
        TxStreamReceipt receipt = new TxStreamReceipt("payouts", "item-1", projection,
                new AtomicLong());
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<TxStreamException> failure = new AtomicReference<>();
        AtomicBoolean interruptedFlag = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            entered.countDown();
            try {
                receipt.awaitSettled();
            } catch (TxStreamException exception) {
                failure.set(exception);
                interruptedFlag.set(Thread.currentThread().isInterrupted());
            }
        });

        waiter.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        waiter.interrupt();
        waiter.join(1_000);

        assertFalse(waiter.isAlive());
        assertEquals("TXSTREAM_INTERRUPTED", failure.get().getCode());
        assertTrue(interruptedFlag.get());
        assertFalse(projection.promise().isDone());
    }

    @Test
    void multipleWaitersObserveTheSameResultWithoutInterference() throws Exception {
        ItemProjection projection = new ItemProjection(
                result(TxStreamItemStatus.ACCEPTED, null, null));
        TxStreamReceipt receipt = new TxStreamReceipt("payouts", "item-1", projection,
                new AtomicLong());
        ExecutorService waiters = Executors.newFixedThreadPool(2);
        try {
            Future<TxStreamItemResult> first = waiters.submit(() -> receipt.awaitSettled());
            Future<TxStreamItemResult> second = waiters.submit(() -> receipt.awaitSettled());
            TxStreamItemResult confirmed = result(TxStreamItemStatus.CONFIRMED, "tx-ok", null);
            projection.completePromise(confirmed);

            assertSame(confirmed, first.get(1, TimeUnit.SECONDS));
            assertSame(confirmed, second.get(1, TimeUnit.SECONDS));
        } finally {
            waiters.shutdownNow();
        }
    }

    private static TxStreamReceipt settledReceipt(TxStreamItemResult result) {
        return new TxStreamReceipt("payouts", "item-1", ItemProjection.settled(result),
                new AtomicLong());
    }

    private static TxStreamItemResult result(TxStreamItemStatus status, String hash,
                                             Throwable error) {
        return TxStreamItemResult.builder("payouts", "item-1", status)
                .transactionHash(hash)
                .error(error)
                .updatedAt(StubEngineGateway.NOW)
                .build();
    }
}
