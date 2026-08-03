package com.bloxbean.cardano.client.txflow.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link TxWorkSource} that ingests work from a
 * {@link java.util.concurrent.Flow.Publisher} with demand-based backpressure
 * (ADR 0004, iteration 3 — {@code java.util.concurrent.Flow} adapters).
 * <p>
 * This is a thin, contract-correct bridge, not a reactive-streams framework
 * (ADR 0004 Non-goals): it owns no threads, timers, or clock. Every re-request
 * runs on the thread that delivered the driving signal — the publisher's
 * {@code onNext} thread or the thread that settles an accepted item's receipt.
 *
 * <h2>Backpressure</h2>
 * On subscription the adapter requests a bounded <em>prefetch</em> (default
 * {@value #DEFAULT_PREFETCH}, never {@code Long.MAX_VALUE}). Thereafter it
 * requests exactly one more item each time an accepted item's receipt settles
 * or a redelivery is resolved without new work. This keeps the number of
 * outstanding items pulled from the publisher bounded by {@code prefetch}.
 * <p>
 * Each received item is offered to the stream through the non-blocking
 * {@link TxWorkSink#trySubmit}. On {@link EmitResult.Status#FULL} the item is
 * <b>not dropped</b>: it is held in a bounded internal deque (bounded by
 * {@code prefetch} — see the invariant below) and retried when an accepted item
 * settles frees stream capacity. On {@link EmitResult.Status#PAUSED} — the
 * stream is <em>temporarily</em> not accepting because this instance is an
 * ownership {@code STANDBY} — the item is likewise parked in the deque, the
 * subscription stays live, {@link #terminated()} does <b>not</b> complete, and
 * the retry is driven by a later settle or by the stream's {@link #resume()}
 * nudge when it reclaims ownership. Only {@link EmitResult.Status#CLOSED} — the
 * stream is genuinely gone (closed/aborted/unhealthy) — tears the source down.
 * The publisher thread is never blocked and no item is ever discarded while the
 * source is live.
 * <p>
 * Capacity accounting invariant: {@code accepted + held + outstandingDemand}
 * stays equal to {@code prefetch}, where {@code accepted} is the number of
 * accepted-but-unsettled items, {@code held} is the number of received items
 * awaiting stream capacity, and {@code outstandingDemand} is the number of
 * items requested from the publisher but not yet delivered. Because every term
 * is non-negative and the sum is constant, {@code held} can never exceed
 * {@code prefetch} — memory is bounded and demand honours the publisher's
 * contract.
 *
 * <h2>Termination</h2>
 * The publisher's {@code onError} is surfaced (never silently swallowed):
 * logged, and completing {@link #terminated()} exceptionally with a typed
 * {@code TXSTREAM_SOURCE_FAILED} {@link TxStreamException}. {@code onComplete}
 * stops requesting and completes {@link #terminated()} normally, letting
 * already-accepted work drain — it does <b>not</b> close the stream (the caller
 * owns close). {@link #close()} cancels the subscription; no item is submitted
 * after cancellation.
 *
 * <h2>Single producer</h2>
 * The adapter drives its re-request off the settlement of items it itself
 * submitted, so it assumes it is the stream's primary producer. Mixing it with
 * direct {@link TxFlowStream#submit(TxWorkItem)} calls that fill the buffer can
 * stall held items until the stream frees capacity through other means; that is
 * an accepted, documented limitation of this thin bridge.
 */
public final class FlowWorkSource implements TxWorkSource {
    /** Default number of items requested up front and kept outstanding. */
    public static final int DEFAULT_PREFETCH = 64;

    private static final Logger log = LoggerFactory.getLogger(FlowWorkSource.class);

    private final Flow.Publisher<TxWorkItem> publisher;
    private final int prefetch;

    private final Object lock = new Object();
    private final ArrayDeque<TxWorkItem> held = new ArrayDeque<>();
    private final AtomicInteger wip = new AtomicInteger();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();

    private TxWorkSink sink;
    private Flow.Subscription subscription;
    private boolean subscribed;
    private int accepted;                 // accepted-but-unsettled items
    private long pendingRequest;          // requests to emit to the subscription
    private long owedRequest;             // requests suppressed while paused
    private boolean paused;
    private boolean sourceComplete;       // publisher signalled onComplete/onError
    private boolean closed;               // close() called or the stream is gone

    FlowWorkSource(Flow.Publisher<TxWorkItem> publisher, int prefetch) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        if (prefetch <= 0) {
            throw new IllegalArgumentException("prefetch must be positive");
        }
        this.prefetch = prefetch;
    }

    /**
     * Returns the configured prefetch — the bound on outstanding, unsettled
     * items pulled from the publisher.
     *
     * @return prefetch value
     */
    public int prefetch() {
        return prefetch;
    }

    /**
     * Returns a stage tracking the <em>publisher's</em> termination: it
     * completes normally when the publisher signals {@code onComplete} (or the
     * source is closed) and exceptionally with a typed
     * {@code TXSTREAM_SOURCE_FAILED} {@link TxStreamException} when the publisher
     * signals {@code onError}. It reflects the source's own lifecycle, not the
     * settlement of the work items — those settle through their receipts.
     *
     * @return read-only termination stage
     */
    public CompletionStage<Void> terminated() {
        return terminated.minimalCompletionStage();
    }

    @Override
    public void start(TxWorkSink workSink) {
        Objects.requireNonNull(workSink, "sink");
        synchronized (lock) {
            if (subscribed || closed) {
                return;
            }
            subscribed = true;
            this.sink = workSink;
        }
        publisher.subscribe(new WorkSubscriber());
    }

    @Override
    public void pause() {
        synchronized (lock) {
            paused = true;
            // Hard stop: park any demand already accrued but not yet issued so
            // no further request reaches the publisher while paused. resume()
            // restores it into pendingRequest.
            owedRequest += pendingRequest;
            pendingRequest = 0;
        }
    }

    @Override
    public void resume() {
        synchronized (lock) {
            if (paused) {
                paused = false;
                pendingRequest += owedRequest;
                owedRequest = 0;
            }
        }
        // Always drain, even when the source itself was never pause()d:
        // resume() doubles as the stream's reactivation nudge — items parked in
        // `held` on a PAUSED disposition (the stream was an ownership STANDBY)
        // are retried here when the stream reclaims ownership and calls
        // resume() from its open-for-work path.
        pump();
    }

    @Override
    public void close() {
        Flow.Subscription toCancel;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            toCancel = subscription;
            subscription = null;
            held.clear();
        }
        cancelQuietly(toCancel);
        terminated.complete(null);
    }

    // ------------------------------------------------------------------
    // Subscriber callbacks (all run on the publisher's threads)
    // ------------------------------------------------------------------

    private void onSubscribe(Flow.Subscription incoming) {
        Objects.requireNonNull(incoming, "subscription");
        boolean cancelIncoming = false;
        synchronized (lock) {
            if (closed || sourceComplete || subscription != null) {
                // Already torn down (closed), the publisher already reached a
                // terminal signal (onComplete/onError null out subscription, so
                // guarding on subscription alone would let a post-terminal
                // re-subscribe re-request), or a duplicate onSubscribe (rule 2.5).
                cancelIncoming = true;
            } else {
                subscription = incoming;
                requestLocked(prefetch);
            }
        }
        if (cancelIncoming) {
            cancelQuietly(incoming);
        } else {
            pump();
        }
    }

    private void onNext(TxWorkItem item) {
        Objects.requireNonNull(item, "item");
        synchronized (lock) {
            if (closed || sourceComplete) {
                return;
            }
            held.addLast(item);
        }
        pump();
    }

    private void onError(Throwable throwable) {
        Throwable cause = throwable != null ? throwable
                : new IllegalStateException("publisher signalled onError(null)");
        synchronized (lock) {
            if (sourceComplete || closed) {
                return;
            }
            sourceComplete = true;
            subscription = null;   // publisher is terminal; no cancel needed
        }
        log.warn("TxFlowStream Flow source publisher failed", cause);
        terminated.completeExceptionally(new TxStreamException(
                "TXSTREAM_SOURCE_FAILED", "work publisher signalled onError", cause));
    }

    private void onComplete() {
        synchronized (lock) {
            if (sourceComplete || closed) {
                return;
            }
            sourceComplete = true;
            subscription = null;
        }
        // Accepted and held items keep draining; the caller owns close().
        terminated.complete(null);
        pump();
    }

    // ------------------------------------------------------------------
    // Settlement-driven re-request
    // ------------------------------------------------------------------

    private void onItemSettled() {
        synchronized (lock) {
            if (accepted > 0) {
                accepted--;
            }
            if (!closed && !sourceComplete) {
                // Refill the credit the settled item freed.
                requestLocked(1);
            }
        }
        pump();
    }

    // ------------------------------------------------------------------
    // Drain pump — serial via the WIP guard; side effects run outside the lock
    // ------------------------------------------------------------------

    private void pump() {
        if (wip.getAndIncrement() != 0) {
            return;   // another thread owns the drain; it will observe our work
        }
        int missed = 1;
        do {
            drainOnce();
            missed = wip.addAndGet(-missed);
        } while (missed != 0);
    }

    private void drainOnce() {
        List<TxStreamReceipt> toObserve = new ArrayList<>();
        boolean cancelSubscription = false;
        // Select-submit-record loop. The WIP guard makes this method single-
        // threaded, so held.peekFirst() stays the same instance across the
        // unlock window (onNext only appends at the tail; a concurrent close()
        // clears held and is re-checked below). The item is CHOSEN under the
        // lock, sink.trySubmit(item) — which reaches engine/store I/O and
        // listener callbacks — runs OUTSIDE the lock, and the outcome is
        // recorded under the lock again. This keeps publisher onNext threads and
        // a status subscriber reached via a listener from blocking on this lock
        // during that I/O.
        while (true) {
            TxWorkItem item;
            synchronized (lock) {
                if (closed || paused || held.isEmpty()) {
                    break;
                }
                item = held.peekFirst();
            }
            EmitResult result = sink.trySubmit(item);   // outside the lock
            EmitResult.Status status = result.getStatus();
            boolean stop = false;
            synchronized (lock) {
                if (closed) {
                    // A concurrent close() cleared held and will settle anything
                    // the sink accepted; stop without recording it here.
                    break;
                }
                if (status == EmitResult.Status.OK
                        || status == EmitResult.Status.DUPLICATE_ATTACHED) {
                    held.removeFirst();   // exactly `item` — we are the only drainer
                    accepted++;
                    toObserve.add(result.getReceipt());
                } else if (status == EmitResult.Status.FULL
                        || status == EmitResult.Status.PAUSED) {
                    // FULL: stream capacity is full — retry on the next settle.
                    // PAUSED: the stream is TEMPORARILY not accepting (an
                    // ownership STANDBY that may reclaim): park the item in
                    // `held` WITHOUT tearing down, WITHOUT completing
                    // terminated(), and without requesting new demand for it —
                    // the retry is driven by a later settle or by the stream's
                    // resume() nudge on ownership reactivation. Neither
                    // disposition is terminal; no item is dropped.
                    stop = true;
                } else if (status == EmitResult.Status.CLOSED) {
                    // The stream is no longer accepting — stop and cancel.
                    closed = true;
                    held.clear();
                    cancelSubscription = subscription != null;
                    stop = true;
                } else {
                    // CONFLICT / REJECTED: resolved without becoming
                    // outstanding — refill the credit it consumed.
                    held.removeFirst();
                    if (!sourceComplete) {
                        requestLocked(1);
                    }
                }
            }
            if (stop) {
                break;
            }
        }
        long toRequest;
        synchronized (lock) {
            toRequest = (closed || paused) ? 0 : takePendingRequestLocked();
        }
        // Side effects strictly outside the lock (a synchronous receipt may
        // re-enter onItemSettled(); a synchronous subscription may re-enter
        // onNext() — the WIP guard serialises both back into this loop).
        for (TxStreamReceipt receipt : toObserve) {
            receipt.completion().whenComplete((result, error) -> onItemSettled());
        }
        if (cancelSubscription) {
            Flow.Subscription sub;
            synchronized (lock) {
                sub = subscription;
                subscription = null;
            }
            cancelQuietly(sub);
            terminated.complete(null);
        } else if (toRequest > 0) {
            Flow.Subscription sub;
            synchronized (lock) {
                sub = subscription;
            }
            if (sub != null) {
                sub.request(toRequest);
            }
        }
    }

    /** Records demand to emit, deferring it into {@link #owedRequest} while paused. */
    private void requestLocked(long n) {
        if (paused) {
            owedRequest += n;
        } else {
            pendingRequest += n;
        }
    }

    private long takePendingRequestLocked() {
        long n = pendingRequest;
        pendingRequest = 0;
        return n;
    }

    private void cancelQuietly(Flow.Subscription sub) {
        if (sub == null) {
            return;
        }
        try {
            sub.cancel();
        } catch (RuntimeException cancelFailure) {
            log.warn("TxFlowStream Flow source subscription cancel failed", cancelFailure);
        }
    }

    /** Bridges the JDK subscriber callbacks to the enclosing source. */
    private final class WorkSubscriber implements Flow.Subscriber<TxWorkItem> {
        @Override
        public void onSubscribe(Flow.Subscription s) {
            FlowWorkSource.this.onSubscribe(s);
        }

        @Override
        public void onNext(TxWorkItem item) {
            FlowWorkSource.this.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            FlowWorkSource.this.onError(throwable);
        }

        @Override
        public void onComplete() {
            FlowWorkSource.this.onComplete();
        }
    }
}
