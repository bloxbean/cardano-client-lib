package com.bloxbean.cardano.client.txflow.stream;

/**
 * Signalled to a {@link java.util.concurrent.Flow.Subscriber} of
 * {@link TxStreamFlowPublisher} whose bounded per-subscriber buffer overflowed
 * because it was <em>under-requesting or slow to request</em> — it kept
 * returning from {@code onNext} but did not call {@code request(n)} fast enough
 * to keep pace with item projection advances.
 * <p>
 * This is the {@code java.util.concurrent.Flow} analogue of a missing-
 * backpressure error: rather than back-pressuring the stream (which must never
 * stall its own dispatch on a slow observer), the publisher terminates that one
 * subscriber with this exception and drops it, leaving the stream and every
 * other subscriber unaffected.
 * <p>
 * This policy covers a slow-to-request subscriber only. A subscriber that
 * instead <b>violates Reactive-Streams §2.2 by blocking inside {@code onNext}</b>
 * is a different failure that this exception does NOT address: because delivery
 * is inline (the adapter owns no threads), a blocking {@code onNext} stalls its
 * lane's dispatch until it returns rather than overflowing a buffer. Well-behaved
 * subscribers must not block in {@code onNext}. See {@link TxStreamFlowPublisher}.
 */
public final class TxStreamSubscriberOverflowException extends TxStreamException {
    /** Stable error code carried by this exception. */
    public static final String CODE = "TXSTREAM_SUBSCRIBER_OVERFLOW";

    /**
     * Creates an overflow exception.
     *
     * @param capacity the per-subscriber buffer capacity that was exceeded
     */
    public TxStreamSubscriberOverflowException(int capacity) {
        super(CODE, "status subscriber buffer overflowed (capacity " + capacity
                + "): the subscriber is not requesting fast enough and has been dropped"
                + " so the stream is never back-pressured by a slow observer");
    }
}
