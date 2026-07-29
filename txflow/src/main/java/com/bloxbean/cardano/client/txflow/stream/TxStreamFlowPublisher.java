package com.bloxbean.cardano.client.txflow.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link TxStreamEventListener} that is also a
 * {@link java.util.concurrent.Flow.Publisher} of item results (ADR 0004,
 * iteration 3 — {@code java.util.concurrent.Flow} adapters).
 * <p>
 * Set one instance as the stream's {@link TxFlowStream.Builder#eventListener}
 * and subscribe to it for reactive-streams interop: every
 * {@link #onItemUpdated(TxStreamItemResult) item projection advance} is fanned
 * out to each subscriber, and {@link #onStreamClosed(String)} completes them.
 * This is a thin, contract-correct bridge, not a reactive-streams framework
 * (ADR 0004 Non-goals): it owns no threads, timers, or clock, and a simple
 * {@code submit}/{@code receipt} user never touches {@code Flow}.
 *
 * <h2>Reactive-Streams §2.2 precondition — subscribers MUST NOT block in onNext</h2>
 * Delivery is <b>inline</b>: {@code onNext} runs on the stream's
 * listener-callback thread (the thread that advanced the item's projection —
 * its lane's dispatch/completion thread), because these adapters own no threads,
 * timers, or clock (ADR 0004 Non-goals). A subscriber that honours
 * Reactive-Streams rule §2.2 (return promptly from {@code onNext}, never block
 * on external work) is what this bridge is built for. A subscriber that
 * <b>violates §2.2 by blocking inside {@code onNext}</b> stalls the very thread
 * dispatching its lane — that lane makes no further progress until the block
 * clears. Other lanes and other subscribers keep running (delivery is serial
 * per subscriber, not global), and — because the item promise is completed
 * before the inline callback — a blocked callback does not wedge the item's own
 * {@code receipt.completion()}, {@code drain()}, or {@code awaitPromises}; only
 * the lane's ongoing dispatch stalls. There is no delivery thread to absorb a
 * blocking subscriber, by design.
 *
 * <h2>Per-subscriber independence and demand</h2>
 * Each subscriber gets its own {@link java.util.concurrent.Flow.Subscription}
 * with independent demand tracking: {@code request(n)} accumulates and
 * {@code onNext} is delivered only up to the outstanding demand. Multiple
 * subscribers are fully independent; a slow (under-requesting) one never holds
 * back a fast one.
 *
 * <h2>Overflow policy — a slow subscriber is bounded and dropped, never stalls the stream</h2>
 * The overflow policy covers a subscriber that <em>under-requests or is slow to
 * request</em> — NOT one that blocks (see §2.2 above). Each subscriber has a
 * bounded buffer (default {@value #DEFAULT_BUFFER_CAPACITY}). When such a
 * subscriber's buffer overflows, the publisher <b>terminates that subscriber
 * with {@code onError(}{@link TxStreamSubscriberOverflowException}{@code )}</b>
 * and drops it — it never blocks the stream, never drops silently, and never
 * back-pressures the stream. The stream keeps dispatching and confirming while a
 * slow subscriber is torn down. This bounded-buffer guarantee holds only for
 * subscribers that return from {@code onNext} without blocking; a §2.2-violating
 * blocking {@code onNext} stalls its lane's dispatch instead.
 *
 * <h2>Isolation and ordering</h2>
 * Delivery is serial per subscriber. A subscriber whose {@code onNext} throws is
 * cancelled and dropped in isolation — the stream's listener dispatch and every
 * other subscriber are unaffected. A subscriber's {@code cancel()} removes it.
 */
public final class TxStreamFlowPublisher
        implements TxStreamEventListener, Flow.Publisher<TxStreamItemResult> {

    /** Default per-subscriber buffer capacity. */
    public static final int DEFAULT_BUFFER_CAPACITY = 256;

    private static final Logger log = LoggerFactory.getLogger(TxStreamFlowPublisher.class);

    private final int bufferCapacity;
    private final boolean terminalOnly;
    private final CopyOnWriteArrayList<SubscriptionImpl> subscriptions = new CopyOnWriteArrayList<>();
    private volatile boolean streamClosed;

    private TxStreamFlowPublisher(int bufferCapacity, boolean terminalOnly) {
        if (bufferCapacity <= 0) {
            throw new IllegalArgumentException("bufferCapacity must be positive");
        }
        this.bufferCapacity = bufferCapacity;
        this.terminalOnly = terminalOnly;
    }

    /**
     * Creates a publisher of <em>every</em> item projection advance, with the
     * default per-subscriber buffer.
     *
     * @return a new publisher/listener
     */
    public static TxStreamFlowPublisher create() {
        return new TxStreamFlowPublisher(DEFAULT_BUFFER_CAPACITY, false);
    }

    /**
     * Creates a publisher of every item projection advance with an explicit
     * per-subscriber buffer capacity.
     *
     * @param bufferCapacity positive per-subscriber buffer capacity
     * @return a new publisher/listener
     */
    public static TxStreamFlowPublisher create(int bufferCapacity) {
        return new TxStreamFlowPublisher(bufferCapacity, false);
    }

    /**
     * Creates a publisher that only emits <em>terminal</em> item results
     * (confirmed, failed, or cancelled) — a receipts-style feed for observers
     * that care about final outcomes rather than the full lifecycle.
     *
     * @return a new terminal-only publisher/listener
     */
    public static TxStreamFlowPublisher terminalOnly() {
        return new TxStreamFlowPublisher(DEFAULT_BUFFER_CAPACITY, true);
    }

    /**
     * Creates a terminal-only publisher with an explicit per-subscriber buffer
     * capacity.
     *
     * @param bufferCapacity positive per-subscriber buffer capacity
     * @return a new terminal-only publisher/listener
     */
    public static TxStreamFlowPublisher terminalOnly(int bufferCapacity) {
        return new TxStreamFlowPublisher(bufferCapacity, true);
    }

    // ------------------------------------------------------------------
    // Flow.Publisher
    // ------------------------------------------------------------------

    @Override
    public void subscribe(Flow.Subscriber<? super TxStreamItemResult> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        SubscriptionImpl subscription = new SubscriptionImpl(subscriber);
        subscriptions.add(subscription);
        try {
            subscriber.onSubscribe(subscription);
        } catch (Throwable onSubscribeFailure) {
            // A broken subscriber must not affect the publisher or the stream.
            subscriptions.remove(subscription);
            log.warn("TxStreamFlowPublisher subscriber onSubscribe threw; dropped", onSubscribeFailure);
            return;
        }
        if (streamClosed) {
            // The stream already closed: deliver onComplete once demand allows.
            subscription.complete();
        }
    }

    // ------------------------------------------------------------------
    // TxStreamEventListener (runs on the stream's listener-callback thread)
    // ------------------------------------------------------------------

    @Override
    public void onItemUpdated(TxStreamItemResult result) {
        if (result == null) {
            return;
        }
        if (terminalOnly && !result.isTerminal()) {
            return;
        }
        // Never throw back into the stream's listener dispatch.
        for (SubscriptionImpl subscription : subscriptions) {
            subscription.offer(result);
        }
    }

    @Override
    public void onStreamClosed(String streamId) {
        streamClosed = true;
        for (SubscriptionImpl subscription : subscriptions) {
            subscription.complete();
        }
    }

    /** Per-subscriber subscription with a bounded buffer and demand tracking. */
    private final class SubscriptionImpl implements Flow.Subscription {
        private final Flow.Subscriber<? super TxStreamItemResult> subscriber;
        private final Object lock = new Object();
        private final ArrayDeque<TxStreamItemResult> buffer = new ArrayDeque<>();
        private final AtomicInteger wip = new AtomicInteger();
        private long demand;
        private boolean cancelled;
        private boolean completePending;   // onStreamClosed seen; onComplete owed
        private boolean terminated;        // onComplete/onError already emitted
        private boolean overflowed;

        SubscriptionImpl(Flow.Subscriber<? super TxStreamItemResult> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                // Reactive Streams rule 3.9: a non-positive request is a signal
                // error terminating the subscriber.
                synchronized (lock) {
                    if (terminated || cancelled) {
                        return;
                    }
                    terminated = true;
                }
                subscriptions.remove(this);
                emitError(new IllegalArgumentException(
                        "Flow rule 3.9: request must be positive but was " + n));
                return;
            }
            synchronized (lock) {
                if (terminated || cancelled) {
                    return;
                }
                long updated = demand + n;
                demand = updated < 0 ? Long.MAX_VALUE : updated;   // saturate
            }
            drain();
        }

        @Override
        public void cancel() {
            synchronized (lock) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                buffer.clear();
            }
            subscriptions.remove(this);
        }

        /** Buffers a result, applying the overflow policy on a full buffer. */
        void offer(TxStreamItemResult result) {
            synchronized (lock) {
                if (terminated || cancelled) {
                    return;
                }
                if (buffer.size() >= bufferCapacity) {
                    overflowed = true;   // teardown happens in drain(), off the lock
                } else {
                    buffer.addLast(result);
                }
            }
            drain();
        }

        /** Records that the stream closed; onComplete is delivered after buffered items. */
        void complete() {
            synchronized (lock) {
                if (terminated || cancelled) {
                    return;
                }
                completePending = true;
            }
            drain();
        }

        /**
         * Serial drain: at most one thread delivers at a time (WIP guard), so
         * signals to this subscriber are never concurrent even when several
         * stream threads fan out at once.
         */
        private void drain() {
            if (wip.getAndIncrement() != 0) {
                return;
            }
            int missed = 1;
            do {
                deliver();
                missed = wip.addAndGet(-missed);
            } while (missed != 0);
        }

        private void deliver() {
            while (true) {
                boolean doOverflow = false;
                boolean doComplete = false;
                TxStreamItemResult next = null;
                synchronized (lock) {
                    if (terminated || cancelled) {
                        return;
                    }
                    if (overflowed) {
                        terminated = true;
                        doOverflow = true;
                    } else if (demand > 0 && !buffer.isEmpty()) {
                        next = buffer.pollFirst();
                        demand--;
                    } else if (completePending && buffer.isEmpty()) {
                        terminated = true;
                        doComplete = true;
                    } else {
                        return;   // nothing deliverable right now
                    }
                }
                if (doOverflow) {
                    subscriptions.remove(this);
                    emitError(new TxStreamSubscriberOverflowException(bufferCapacity));
                    return;
                }
                if (doComplete) {
                    subscriptions.remove(this);
                    emitComplete();
                    return;
                }
                if (!emitNext(next)) {
                    return;   // subscriber's onNext threw; it was cancelled in isolation
                }
            }
        }

        /** @return {@code true} if delivery may continue, {@code false} if the subscriber was torn down */
        private boolean emitNext(TxStreamItemResult result) {
            try {
                subscriber.onNext(result);
                return true;
            } catch (Throwable onNextFailure) {
                // Rule 2.13: a throwing onNext leaves the subscriber undefined;
                // cancel it in isolation so the stream and peers are unaffected.
                synchronized (lock) {
                    terminated = true;
                    buffer.clear();
                }
                subscriptions.remove(this);
                log.warn("TxStreamFlowPublisher subscriber onNext threw; cancelled in isolation",
                        onNextFailure);
                return false;
            }
        }

        private void emitComplete() {
            try {
                subscriber.onComplete();
            } catch (Throwable onCompleteFailure) {
                log.warn("TxStreamFlowPublisher subscriber onComplete threw", onCompleteFailure);
            }
        }

        private void emitError(Throwable error) {
            try {
                subscriber.onError(error);
            } catch (Throwable onErrorFailure) {
                log.warn("TxStreamFlowPublisher subscriber onError threw", onErrorFailure);
            }
        }
    }
}
