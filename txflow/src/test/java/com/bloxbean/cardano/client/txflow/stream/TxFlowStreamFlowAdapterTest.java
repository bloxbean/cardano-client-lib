package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic tests for the {@code java.util.concurrent.Flow} adapters (ADR
 * 0004, iteration 3c). Both publishers and subscribers are hand-driven — no
 * real threads, timers, or sleeps: the stream runs on {@code Runnable::run} and
 * every engine outcome is scripted through {@link StubEngineGateway}.
 */
class TxFlowStreamFlowAdapterTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String SENDER2 = "addr_test1vpqsender2";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    // ==================================================================
    // A. Ingestion — TxWorkSource.fromPublisher(...)
    // ==================================================================

    @Test
    void fromPublisherForwardsItemsWhichDispatchAndConfirm() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualPublisher publisher = new ManualPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher);
        try (TxFlowStream stream = builder(gateway).source(source).build()) {
            stream.start();
            assertEquals(FlowWorkSource.DEFAULT_PREFETCH, publisher.requested.get(),
                    "an initial bounded prefetch is requested, never Long.MAX_VALUE");

            publisher.next(planItem("pay-1"));
            publisher.next(planItem("pay-2"));   // queues behind pay-1 on the single lane

            assertEquals(1, gateway.started.size(), "pay-1 dispatched; pay-2 queued FIFO");
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            assertEquals(2, gateway.started.size(), "pay-2 dispatches once the lane frees");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-2");

            assertEquals(TxStreamItemStatus.CONFIRMED, stream.getItemStatus("pay-1").orElseThrow().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED, stream.getItemStatus("pay-2").orElseThrow().getStatus());
        }
    }

    @Test
    void prefetchBoundsOutstandingAndReRequestsOnlyAsItemsSettle() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualPublisher publisher = new ManualPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher, 3);
        try (TxFlowStream stream = builder(gateway).source(source).build()) {
            stream.start();
            assertEquals(3L, publisher.requested.get());

            // Offer exactly the prefetch; no settlement yet.
            publisher.next(planItem("pay-1"));   // dispatched (lane free)
            publisher.next(planItem("pay-2"));   // queued FIFO
            publisher.next(planItem("pay-3"));   // queued FIFO
            assertEquals(3L, publisher.requested.get(),
                    "no re-request while items are still outstanding");
            assertEquals(1, gateway.started.size());

            // Settling frees exactly one credit → exactly one more requested.
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            assertEquals(4L, publisher.requested.get(), "one settle → one re-request");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-2");
            assertEquals(5L, publisher.requested.get());
            gateway.handles.get(2).completeConfirmed(STEP_ID, "tx-3");

            assertEquals(3, gateway.started.size(), "all three offered items reached the engine");
        }
    }

    @Test
    void aFullStreamAppliesBackpressureAndHeldItemsAreNeverDropped() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualPublisher publisher = new ManualPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher, 4);
        try (TxFlowStream stream = builder(gateway).source(source).maxBufferSize(1).build()) {
            stream.start();
            assertEquals(4L, publisher.requested.get());

            publisher.next(planItem("pay-1"));   // dispatched (lane free)
            publisher.next(planItem("pay-2"));   // occupies the single buffer slot
            publisher.next(planItem("pay-3"));   // FULL → held (not dropped)
            publisher.next(planItem("pay-4"));   // FULL → held (not dropped)

            assertEquals(1, gateway.started.size(), "only pay-1 dispatched; the buffer is full");
            assertEquals(4L, publisher.requested.get(),
                    "a full stream does NOT request more — capacity, not the publisher, gates");

            // Drain one at a time; each freed slot admits exactly one held item,
            // FIFO, and nothing is lost.
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            assertEquals(2, gateway.started.size());
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-2");
            assertEquals(3, gateway.started.size());
            gateway.handles.get(2).completeConfirmed(STEP_ID, "tx-3");
            assertEquals(4, gateway.started.size(), "all four items eventually dispatched — no drop");
            gateway.handles.get(3).completeConfirmed(STEP_ID, "tx-4");

            for (String id : List.of("pay-1", "pay-2", "pay-3", "pay-4")) {
                assertEquals(TxStreamItemStatus.CONFIRMED,
                        stream.getItemStatus(id).orElseThrow().getStatus());
            }
            assertTrue(publisher.requested.get() < 100L,
                    "re-request stays bounded; never Long.MAX_VALUE");
        }
    }

    @Test
    void onErrorIsSurfacedThroughTerminatedNotSwallowed() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualPublisher publisher = new ManualPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher);
        try (TxFlowStream stream = builder(gateway).source(source).build()) {
            stream.start();
            publisher.next(planItem("pay-1"));
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");

            publisher.error(new IllegalStateException("broker down"));

            CompletionException failure = assertThrows(CompletionException.class,
                    () -> source.terminated().toCompletableFuture().join());
            Throwable cause = failure;
            while (cause != null && !(cause instanceof TxStreamException)) {
                cause = cause.getCause();
            }
            TxStreamException typed = assertInstanceOf(TxStreamException.class, cause);
            assertEquals("TXSTREAM_SOURCE_FAILED", typed.getCode());
        }
    }

    @Test
    void onCompleteLetsAcceptedWorkDrainWithoutClosingTheStreamOrReRequesting() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualPublisher publisher = new ManualPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher, 4);
        try (TxFlowStream stream = builder(gateway).source(source).build()) {
            stream.start();
            publisher.next(planItem("pay-1"));
            publisher.next(planItem("pay-2"));
            long requestedBeforeComplete = publisher.requested.get();

            publisher.complete();
            assertTrue(source.terminated().toCompletableFuture().isDone());
            assertFalse(source.terminated().toCompletableFuture().isCompletedExceptionally());

            // Accepted work still drains and confirms; the caller owns close().
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-2");
            assertEquals(TxStreamItemStatus.CONFIRMED, stream.getItemStatus("pay-1").orElseThrow().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED, stream.getItemStatus("pay-2").orElseThrow().getStatus());

            assertEquals(requestedBeforeComplete, publisher.requested.get(),
                    "no more demand is requested after onComplete");
        }
    }

    @Test
    void closeCancelsTheSubscriptionAndNoItemIsSubmittedAfterCancel() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualPublisher publisher = new ManualPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher);
        TxFlowStream stream = builder(gateway).source(source).build();
        stream.start();
        publisher.next(planItem("pay-1"));
        gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");

        stream.close();   // graceful close → source.close() → subscription cancelled
        assertTrue(publisher.cancelled, "close() cancels the publisher subscription");

        // A straggler onNext after cancellation must never reach the sink.
        publisher.next(planItem("pay-2"));
        assertEquals(1, gateway.started.size(), "no submit after cancel");
    }

    // ==================================================================
    // B. Status out — TxStreamFlowPublisher
    // ==================================================================

    @Test
    void statusPublisherDeliversUpdatesRespectingRequestedDemand() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.create();
        RecordingSubscriber subscriber = new RecordingSubscriber();   // requests 0 up front
        publisher.subscribe(subscriber);
        try (TxFlowStream stream = builder(gateway).eventListener(publisher).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            receipt.completion().toCompletableFuture().join();

            assertTrue(subscriber.items.isEmpty(), "no onNext beyond zero demand");

            subscriber.request(2);
            assertEquals(List.of(TxStreamItemStatus.ACCEPTED, TxStreamItemStatus.PLANNED),
                    statuses(subscriber), "exactly the requested count is delivered");

            subscriber.request(10);
            assertEquals(List.of(TxStreamItemStatus.ACCEPTED, TxStreamItemStatus.PLANNED,
                            TxStreamItemStatus.SUBMITTED, TxStreamItemStatus.CONFIRMED),
                    statuses(subscriber));
        }
    }

    @Test
    void twoSubscribersTrackDemandIndependently() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.create();
        RecordingSubscriber slow = new RecordingSubscriber();              // demand 0
        RecordingSubscriber fast = new RecordingSubscriber(Long.MAX_VALUE); // unbounded demand
        publisher.subscribe(slow);
        publisher.subscribe(fast);
        try (TxFlowStream stream = builder(gateway).eventListener(publisher).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            receipt.completion().toCompletableFuture().join();

            assertEquals(4, fast.items.size(), "the fast subscriber sees the whole lifecycle");
            assertTrue(slow.items.isEmpty(), "the slow subscriber is independent and unaffected");
            slow.request(4);
            assertEquals(4, slow.items.size());
        }
    }

    @Test
    void slowSubscriberOverflowIsIsolatedWithoutStallingTheStreamOrPeers() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.create(2);   // tiny buffer
        RecordingSubscriber slow = new RecordingSubscriber();               // never requests
        RecordingSubscriber fast = new RecordingSubscriber(Long.MAX_VALUE);
        publisher.subscribe(slow);
        publisher.subscribe(fast);
        try (TxFlowStream stream = builder(gateway).eventListener(publisher).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            // The stream dispatches and CONFIRMS while the slow subscriber is stuck.
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());

            // The slow subscriber's bounded buffer overflowed → typed onError, dropped.
            assertInstanceOf(TxStreamSubscriberOverflowException.class, slow.error);
            assertTrue(slow.items.isEmpty());
            // The fast peer and the stream are entirely unaffected.
            assertEquals(4, fast.items.size());
            assertTrue(stream.isHealthy());
            assertEquals(TxStreamItemStatus.CONFIRMED, stream.getItemStatus("pay-1").orElseThrow().getStatus());
        }
    }

    @Test
    void cancelRemovesTheSubscriber() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.create();
        RecordingSubscriber subscriber = new RecordingSubscriber(Long.MAX_VALUE);
        publisher.subscribe(subscriber);
        try (TxFlowStream stream = builder(gateway).eventListener(publisher).build()) {
            stream.start();
            stream.submit(planItem("pay-1"));
            int seenBeforeCancel = subscriber.items.size();
            assertTrue(seenBeforeCancel > 0);

            subscriber.cancel();
            stream.submit(planItem("pay-2"));   // pay-1 in flight; pay-2 queued
            assertEquals(seenBeforeCancel, subscriber.items.size(),
                    "a cancelled subscriber receives no further signals");
            // Settle both so the graceful close() drains rather than hangs.
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-2");
            assertEquals(seenBeforeCancel, subscriber.items.size(),
                    "still no signals after cancel, even as later items progress");
        }
    }

    @Test
    void onStreamClosedCompletesSubscribers() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.create();
        RecordingSubscriber subscriber = new RecordingSubscriber(Long.MAX_VALUE);
        publisher.subscribe(subscriber);
        try (TxFlowStream stream = builder(gateway).eventListener(publisher).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            receipt.completion().toCompletableFuture().join();
        }   // close() → onStreamClosed → onComplete
        assertTrue(subscriber.completed, "onStreamClosed maps to onComplete");
    }

    @Test
    void throwingSubscriberOnNextIsIsolatedFromStreamAndPeers() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.create();
        RecordingSubscriber hostile = new RecordingSubscriber(Long.MAX_VALUE);
        hostile.throwOnNext = true;
        RecordingSubscriber healthy = new RecordingSubscriber(Long.MAX_VALUE);
        publisher.subscribe(hostile);
        publisher.subscribe(healthy);
        try (TxFlowStream stream = builder(gateway).eventListener(publisher).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus(),
                    "the stream is unaffected by a throwing subscriber");

            assertEquals(1, hostile.items.size(), "the hostile subscriber is cancelled after its first throw");
            assertEquals(4, healthy.items.size(), "the healthy peer keeps receiving");
        }
    }

    @Test
    void nonPositiveRequestSignalsIllegalArgumentToTheSubscriber() {
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.create();
        RecordingSubscriber subscriber = new RecordingSubscriber();
        publisher.subscribe(subscriber);
        subscriber.request(0);
        assertInstanceOf(IllegalArgumentException.class, subscriber.error);
    }

    @Test
    void terminalOnlyPublisherEmitsOnlyTerminalResults() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.terminalOnly();
        RecordingSubscriber subscriber = new RecordingSubscriber(Long.MAX_VALUE);
        publisher.subscribe(subscriber);
        try (TxFlowStream stream = builder(gateway).eventListener(publisher).build()) {
            stream.start();
            stream.submit(planItem("pay-1"));
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");

            assertEquals(List.of(TxStreamItemStatus.CONFIRMED), statuses(subscriber),
                    "only the terminal result is delivered");
        }
    }

    // ==================================================================
    // C. 3c review fix pass — honest §2.2 blast radius, WIP concurrency,
    //    FlowWorkSource demand accounting
    // ==================================================================

    /**
     * F1: delivery is INLINE, so a subscriber that violates Reactive-Streams
     * §2.2 by blocking in {@code onNext} stalls its lane's dispatch thread. But
     * because the item promise is completed BEFORE the inline listener callback
     * (the F1 reorder), the blocked item's {@code receipt.completion()} still
     * settles, a DIFFERENT lane keeps confirming, and a well-behaved peer
     * subscriber keeps receiving — and everything unblocks once the block clears.
     */
    @Test
    void blockingSubscriberOnNextStallsOnlyItsLaneNotItsPromiseOrOtherLanes() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.create();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch blockedEntered = new CountDownLatch(1);
        BlockingSubscriber blocker = new BlockingSubscriber(release, blockedEntered);
        RecordingSubscriber fast = new RecordingSubscriber(Long.MAX_VALUE);
        publisher.subscribe(blocker);   // subscribed first → offered first on each fan-out
        publisher.subscribe(fast);

        LaneIdentityResolver resolver = laneName -> "lane-a".equals(laneName)
                ? ResolvedLane.ofAddress("lane-a", SENDER)
                : ResolvedLane.ofAddress("lane-b", SENDER2);
        Thread laneAConfirm;
        try (TxFlowStream stream = explicitBuilder(gateway, resolver)
                .eventListener(publisher).build()) {
            stream.start();
            TxStreamReceipt receiptA = stream.submit(laneItem("a-1", "lane-a"));
            TxStreamReceipt receiptB = stream.submit(laneItem("b-1", "lane-b"));

            // Confirm lane-a on a background thread; its terminal onItemUpdated
            // blocks the blocker inside onNext, wedging THAT thread only.
            laneAConfirm = new Thread(
                    () -> gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-a1"),
                    "lane-a-confirm");
            laneAConfirm.start();
            assertTrue(blockedEntered.await(10, TimeUnit.SECONDS),
                    "the blocking subscriber must have entered its blocked onNext");

            // (a) The blocked item's receipt STILL completes — the promise was
            //     completed before the inline (now blocked) listener callback.
            assertEquals(TxStreamItemStatus.CONFIRMED, receiptA.completion()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus(),
                    "the blocked item's receipt settles despite the §2.2-violating block");

            // (b) A DIFFERENT lane confirms on this thread while lane-a's dispatch
            //     thread is wedged, and the well-behaved peer keeps receiving.
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-b1");
            assertEquals(TxStreamItemStatus.CONFIRMED, receiptB.completion()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus(),
                    "another lane confirms while lane-a's dispatch thread is blocked");
            assertTrue(terminalItemIds(fast).contains("b-1"),
                    "a well-behaved peer subscriber keeps receiving during the block");

            // (c) Releasing the block unblocks everything.
            release.countDown();
            laneAConfirm.join(10_000);
            assertFalse(laneAConfirm.isAlive(), "the blocked dispatch thread unblocks");
        }
        assertTrue(blocker.terminalIds().containsAll(List.of("a-1", "b-1")),
                "after unblocking, the blocked subscriber has received both terminals");
    }

    /**
     * The per-subscriber serial-delivery WIP guard, under REAL concurrency:
     * many threads fan {@code onItemUpdated} and hammer {@code request} at once,
     * yet {@code onNext} is never concurrent and no signal is lost or duplicated.
     */
    @Test
    void concurrentProducersDeliverSeriallyWithoutLossOrDuplication() throws Exception {
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.create(8192);
        ConcurrencyCheckingSubscriber subscriber = new ConcurrencyCheckingSubscriber();
        publisher.subscribe(subscriber);

        int producers = 4;
        int perProducer = 250;
        ExecutorService pool = Executors.newFixedThreadPool(producers);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int p = 0; p < producers; p++) {
                int producerId = p;
                futures.add(pool.submit(() -> {
                    startGate.await();
                    for (int i = 0; i < perProducer; i++) {
                        publisher.onItemUpdated(terminalResult("p" + producerId + "-" + i));
                        subscriber.subscription.request(1);   // hammer request too
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        int expected = producers * perProducer;
        assertFalse(subscriber.concurrencyViolation, "onNext must never run concurrently");
        assertEquals(expected, subscriber.total.get(), "no signal is lost or duplicated");
        assertEquals(expected, subscriber.ids.size(), "every distinct item was delivered exactly once");
        assertNull(subscriber.error, "no spurious error under concurrency");
    }

    /** Demand saturates at {@code Long.MAX_VALUE}; requesting MAX twice never wraps negative. */
    @Test
    void requestSaturatesAtMaxValueAndKeepsDelivering() {
        TxStreamFlowPublisher publisher = TxStreamFlowPublisher.create();
        RecordingSubscriber subscriber = new RecordingSubscriber();   // demand 0
        publisher.subscribe(subscriber);
        subscriber.request(Long.MAX_VALUE);
        subscriber.request(Long.MAX_VALUE);   // MAX + MAX would overflow negative → must saturate

        for (int i = 0; i < 5; i++) {
            publisher.onItemUpdated(terminalResult("id-" + i));
        }
        assertEquals(5, subscriber.items.size(),
                "saturated demand keeps delivering; it never wrapped to a negative value");
        assertNull(subscriber.error, "saturating demand is not a signal error");
    }

    /**
     * F2: {@code onComplete}/{@code onError} null out the subscription, so the
     * duplicate-onSubscribe guard must also check {@code sourceComplete} — a
     * post-terminal {@code onSubscribe} is cancelled, never re-requested.
     */
    @Test
    void onSubscribeAfterTerminalIsCancelledNotHonoured() {
        ManualPublisher publisher = new ManualPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher, 4);
        ScriptedSink sink = new ScriptedSink();
        source.start(sink);
        assertEquals(4L, publisher.requested.get());

        publisher.complete();   // terminal: subscription is nulled, terminated completes

        publisher.reSubscribe();   // a publisher re-calling onSubscribe after terminal
        assertTrue(publisher.reCancelled, "a post-terminal onSubscribe is cancelled");
        assertEquals(0L, publisher.reRequested.get(),
                "a post-terminal onSubscribe never re-requests");
    }

    /**
     * F3: {@code pause()} is a hard stop — no further demand reaches the
     * publisher while paused (even the credit a settlement frees), and
     * {@code resume()} releases the parked demand.
     */
    @Test
    void pauseParksFreedDemandUntilResume() {
        ManualPublisher publisher = new ManualPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher, 2);
        ScriptedSink sink = new ScriptedSink();
        ReceiptHandle rA = unsettledReceipt("pay-1");
        ReceiptHandle rB = unsettledReceipt("pay-2");
        sink.enqueue(EmitResult.ok(rA.receipt));
        sink.enqueue(EmitResult.ok(rB.receipt));
        source.start(sink);
        assertEquals(2L, publisher.requested.get());

        publisher.next(planItem("pay-1"));   // accepted (unsettled)
        publisher.next(planItem("pay-2"));   // accepted (unsettled)
        assertEquals(2L, publisher.requested.get(), "no re-request while both are outstanding");

        source.pause();
        rA.settle();   // a settle frees a credit, but paused parks it
        assertEquals(2L, publisher.requested.get(),
                "pause() is a hard stop: no request reaches the publisher while paused");

        source.resume();
        assertEquals(3L, publisher.requested.get(), "resume() releases the parked demand");
    }

    /**
     * F4 refill paths: content outcomes that never become outstanding
     * ({@code CONFLICT}, {@code REJECTED}) refill demand immediately;
     * {@code DUPLICATE_ATTACHED} counts as accepted and refills on settle — no
     * stall in any case.
     */
    @Test
    void conflictRejectedAndDuplicateAttachedRefillDemandWithoutStalling() {
        ManualPublisher publisher = new ManualPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher, 4);
        ScriptedSink sink = new ScriptedSink();
        ReceiptHandle rA = unsettledReceipt("pay-a");
        ReceiptHandle rD = unsettledReceipt("pay-d");
        sink.enqueue(EmitResult.ok(rA.receipt));
        sink.enqueue(EmitResult.conflict(new TxStreamDuplicateItemException("pay-b", "conflict")));
        sink.enqueue(EmitResult.rejected(new TxStreamException("TXSTREAM_REJECTED", "rejected")));
        sink.enqueue(EmitResult.duplicateAttached(rD.receipt));
        source.start(sink);
        assertEquals(4L, publisher.requested.get());

        publisher.next(planItem("pay-a"));   // OK  → accepted, refill only on settle
        assertEquals(4L, publisher.requested.get());
        publisher.next(planItem("pay-b"));   // CONFLICT → immediate refill
        assertEquals(5L, publisher.requested.get());
        publisher.next(planItem("pay-c"));   // REJECTED → immediate refill
        assertEquals(6L, publisher.requested.get());
        publisher.next(planItem("pay-d"));   // DUPLICATE_ATTACHED → accepted, refill on settle
        assertEquals(6L, publisher.requested.get());

        rA.settle();
        assertEquals(7L, publisher.requested.get(), "settling the OK item refills one");
        rD.settle();
        assertEquals(8L, publisher.requested.get(), "settling the attached duplicate refills one");
        assertEquals(4, sink.submitted.size(), "all four items reached the sink; none stalled");
    }

    /**
     * Close with items HELD (the stream reported FULL): held items are dropped
     * cleanly, the subscription is cancelled, and no item is submitted after close.
     */
    @Test
    void closeWithItemsHeldDropsThemCleanlyAndNeverSubmitsAfter() {
        ManualPublisher publisher = new ManualPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher, 4);
        ScriptedSink sink = new ScriptedSink();
        sink.fallback = EmitResult.full();   // the stream is full → every item is held
        source.start(sink);
        assertEquals(4L, publisher.requested.get());

        publisher.next(planItem("pay-1"));   // FULL → held
        publisher.next(planItem("pay-2"));   // FULL → held (behind pay-1)
        int submittedBeforeClose = sink.submitted.size();
        assertTrue(submittedBeforeClose >= 1, "held items were offered to the sink");

        source.close();
        assertTrue(publisher.cancelled, "close() cancels the subscription");
        assertTrue(source.terminated().toCompletableFuture().isDone(),
                "close() completes the termination stage");

        publisher.next(planItem("pay-3"));   // straggler after close
        assertEquals(submittedBeforeClose, sink.submitted.size(),
                "held items are dropped and nothing is submitted after close");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TxFlowStream.Builder builder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxWorkItem planItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER)));
    }

    private static List<TxStreamItemStatus> statuses(RecordingSubscriber subscriber) {
        List<TxStreamItemStatus> out = new ArrayList<>();
        for (TxStreamItemResult result : subscriber.items) {
            out.add(result.getStatus());
        }
        return out;
    }

    private TxFlowStream.Builder explicitBuilder(StubEngineGateway gateway,
                                                 LaneIdentityResolver resolver) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.explicit())
                .laneResolver(resolver)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    /** Lane-scoped item whose plan has no funding source (the lane materializes it). */
    private TxWorkItem laneItem(String itemId, String lane) {
        return TxWorkItem.builder(itemId)
                .withTxPlan(TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5))))
                .withLane(lane)
                .build();
    }

    private static Set<String> terminalItemIds(RecordingSubscriber subscriber) {
        Set<String> ids = new java.util.HashSet<>();
        for (TxStreamItemResult result : subscriber.items) {
            if (result.isTerminal()) {
                ids.add(result.getItemId());
            }
        }
        return ids;
    }

    private static TxStreamItemResult terminalResult(String itemId) {
        return TxStreamItemResult.builder("payouts", itemId, TxStreamItemStatus.CONFIRMED)
                .updatedAt(StubEngineGateway.NOW).build();
    }

    private ReceiptHandle unsettledReceipt(String itemId) {
        TxStreamItemResult initial = TxStreamItemResult.builder(
                "payouts", itemId, TxStreamItemStatus.ACCEPTED)
                .updatedAt(StubEngineGateway.NOW).build();
        ItemProjection projection = new ItemProjection(initial);
        TxStreamReceipt receipt = new TxStreamReceipt("payouts", itemId, projection, new AtomicLong());
        return new ReceiptHandle(receipt, projection, itemId);
    }

    /** An unsettled receipt plus a handle to settle its promise on demand. */
    private static final class ReceiptHandle {
        final TxStreamReceipt receipt;
        private final ItemProjection projection;
        private final String itemId;

        ReceiptHandle(TxStreamReceipt receipt, ItemProjection projection, String itemId) {
            this.receipt = receipt;
            this.projection = projection;
            this.itemId = itemId;
        }

        void settle() {
            projection.completePromise(terminalResult(itemId));
        }
    }

    /** Scripted {@link TxWorkSink}: returns queued outcomes, records submissions. */
    private static final class ScriptedSink implements TxWorkSink {
        final ArrayDeque<EmitResult> results = new ArrayDeque<>();
        final List<TxWorkItem> submitted = new CopyOnWriteArrayList<>();
        volatile EmitResult fallback = EmitResult.full();

        void enqueue(EmitResult result) {
            results.addLast(result);
        }

        @Override
        public TxStreamReceipt submit(TxWorkItem item) {
            throw new UnsupportedOperationException("scripted sink is trySubmit-only");
        }

        @Override
        public EmitResult trySubmit(TxWorkItem item) {
            submitted.add(item);
            EmitResult next = results.pollFirst();
            return next != null ? next : fallback;
        }
    }

    /** Hand-driven {@link Flow.Publisher}: records demand and cancellation. */
    private static final class ManualPublisher implements Flow.Publisher<TxWorkItem> {
        final AtomicLong requested = new AtomicLong();
        volatile boolean cancelled;
        // Tracking for a SECOND (post-terminal) onSubscribe (F2).
        final AtomicLong reRequested = new AtomicLong();
        volatile boolean reCancelled;
        private Flow.Subscriber<? super TxWorkItem> subscriber;

        @Override
        public void subscribe(Flow.Subscriber<? super TxWorkItem> subscriber) {
            this.subscriber = subscriber;
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    requested.addAndGet(n);
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        /** Re-delivers onSubscribe (a misbehaving/reconnecting publisher). */
        void reSubscribe() {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    reRequested.addAndGet(n);
                }

                @Override
                public void cancel() {
                    reCancelled = true;
                }
            });
        }

        void next(TxWorkItem item) {
            subscriber.onNext(item);
        }

        void error(Throwable t) {
            subscriber.onError(t);
        }

        void complete() {
            subscriber.onComplete();
        }
    }

    /**
     * A {@link Flow.Subscriber} that BLOCKS inside {@code onNext} the first time
     * it delivers a terminal result — a deliberate Reactive-Streams §2.2
     * violation, used to prove the inline-delivery blast radius (F1).
     */
    private static final class BlockingSubscriber implements Flow.Subscriber<TxStreamItemResult> {
        private final List<TxStreamItemResult> items = new CopyOnWriteArrayList<>();
        private final CountDownLatch release;
        private final CountDownLatch blockedEntered;
        private final AtomicBoolean blockedOnce = new AtomicBoolean();

        BlockingSubscriber(CountDownLatch release, CountDownLatch blockedEntered) {
            this.release = release;
            this.blockedEntered = blockedEntered;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(TxStreamItemResult item) {
            items.add(item);
            if (item.isTerminal() && blockedOnce.compareAndSet(false, true)) {
                blockedEntered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupt) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }

        Set<String> terminalIds() {
            Set<String> ids = new java.util.HashSet<>();
            for (TxStreamItemResult item : items) {
                if (item.isTerminal()) {
                    ids.add(item.getItemId());
                }
            }
            return ids;
        }
    }

    /**
     * A {@link Flow.Subscriber} that detects concurrent {@code onNext} calls and
     * records every delivered item id — proves serial delivery under real
     * concurrency with no loss or duplication.
     */
    private static final class ConcurrencyCheckingSubscriber
            implements Flow.Subscriber<TxStreamItemResult> {
        final Set<String> ids = ConcurrentHashMap.newKeySet();
        final AtomicInteger total = new AtomicInteger();
        final AtomicInteger inOnNext = new AtomicInteger();
        volatile boolean concurrencyViolation;
        volatile Throwable error;
        Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(TxStreamItemResult item) {
            if (inOnNext.incrementAndGet() != 1) {
                concurrencyViolation = true;
            }
            total.incrementAndGet();
            ids.add(item.getItemId());
            inOnNext.decrementAndGet();
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onComplete() {
        }
    }

    /** Hand-driven {@link Flow.Subscriber}: records signals; controllable demand. */
    private static final class RecordingSubscriber implements Flow.Subscriber<TxStreamItemResult> {
        final List<TxStreamItemResult> items = new ArrayList<>();
        final long initialRequest;
        volatile boolean throwOnNext;
        Throwable error;
        boolean completed;
        private Flow.Subscription subscription;

        RecordingSubscriber() {
            this(0);
        }

        RecordingSubscriber(long initialRequest) {
            this.initialRequest = initialRequest;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (initialRequest > 0) {
                subscription.request(initialRequest);
            }
        }

        @Override
        public void onNext(TxStreamItemResult item) {
            items.add(item);
            if (throwOnNext) {
                throw new IllegalStateException("subscriber boom");
            }
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        void request(long n) {
            subscription.request(n);
        }

        void cancel() {
            subscription.cancel();
        }
    }
}
