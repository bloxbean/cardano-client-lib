package com.bloxbean.cardano.client.txflow.stream;

/**
 * Configuration for the {@link TxStreamPlanner#batching(BatchingOptions)
 * batching} planner (ADR 0004 Decision 6): the planner that merges compatible
 * payment-shaped items in one window into <em>fewer</em> transactions.
 *
 * <p>Two knobs, both conservative by default:</p>
 * <ul>
 *   <li>{@link #maxItemsPerTransaction()} caps how many payment items may share
 *       one merged transaction (protecting transaction size); overflow within a
 *       lane splits into multiple merged flows. Default {@value #DEFAULT_MAX_ITEMS_PER_TRANSACTION}.</li>
 *   <li>{@link #allowNonPaymentSingletons()} decides what happens to items that
 *       are <em>not</em> payment-shaped (they carry a script, mint, staking,
 *       governance, metadata, {@code collectFrom}, reference input, or a
 *       contract/datum-bearing output — anything that cannot be reproduced by a
 *       plain {@code payToAddress}). When {@code true} (default) such items each
 *       run as their own single-item flow inside the same plan (never merged);
 *       when {@code false} their presence fails the window typed. Default
 *       {@code true}.</li>
 * </ul>
 *
 * <p><b>Batching gives flow-level dedup only — the sharpest footgun of the
 * whole stream (read before using).</b> A merged flow claims the engine under a
 * key derived from its <em>exact</em> member set (ADR 0004 Decision 3), so an
 * identical batch redelivered whole matches the stored execution and is not
 * re-submitted. But a <em>single</em> item that is redelivered into a
 * <em>differently-composed</em> batch produces a <em>different</em> claim key —
 * a new execution — and, unlike {@link TxStreamPlanner#perWindow() perWindow}
 * where each item is its own independent transaction, a re-batched payment is a
 * <b>real second on-chain payment</b>: the recipient is paid twice. There is no
 * per-item exactly-once guarantee under batching. If your source can redeliver
 * individual items, either use {@link TxStreamPlanner#perItem() perItem} (true
 * per-item dedup) or dedup upstream so the same item can never land in two
 * different batches. Per-item dedup inside a merged flow awaits the multi-claim
 * engine extension (ADR 0004 Decision 6 / Open Question 2 — one execution
 * registering N item-level claim aliases atomically); until it lands, batching
 * is flow-level dedup only, by design.</p>
 *
 * <p>On chain a merged batch is one transaction, so its members share one fate:
 * the whole batch confirms together or fails together — batching trades
 * per-item failure independence for fewer transactions. (Independent flows
 * within one window — different lanes, or the overflow split — can still settle
 * to different outcomes.)</p>
 *
 * <p><b>A merged transaction is a minimal reconstruction — per-member context is
 * NOT carried.</b> The merge rebuilds a bare transaction from each member's pure
 * payment outputs plus the lane's funding source (and, for a funding-ref lane,
 * that ref as the payment signer). Any transaction-level context a member
 * carried — extra signers, a custom fee payer, a custom change address, a
 * validity window, attached metadata, mint, datum/script outputs — is
 * <em>dropped</em>. That is also why only pure payments are ever eligible: a
 * member needing any of that context is not payment-shaped (or would fail the
 * output round-trip guard) and runs unmerged as its own single-item flow. If an
 * item genuinely needs per-member signing, change, validity, or fee-payer
 * settings, do not rely on batching — use {@link TxStreamPlanner#perItem()
 * perItem} or {@link TxStreamPlanner#perWindow() perWindow}, which keep each
 * item's own transaction intact. (Batching fails closed if it ever produced an
 * unsignable merged transaction: the batch fails, no funds move.)</p>
 *
 * <p>Instances are immutable; build them with {@link #builder()} or take the
 * conservative {@link #defaults()}.</p>
 */
public final class BatchingOptions {
    /** Default cap on payment items merged into one transaction. */
    public static final int DEFAULT_MAX_ITEMS_PER_TRANSACTION = 20;

    private final int maxItemsPerTransaction;
    private final boolean allowNonPaymentSingletons;

    private BatchingOptions(Builder builder) {
        if (builder.maxItemsPerTransaction < 1) {
            throw new IllegalArgumentException(
                    "maxItemsPerTransaction must be at least 1");
        }
        this.maxItemsPerTransaction = builder.maxItemsPerTransaction;
        this.allowNonPaymentSingletons = builder.allowNonPaymentSingletons;
    }

    /**
     * Returns the conservative default configuration:
     * {@code maxItemsPerTransaction = }{@value #DEFAULT_MAX_ITEMS_PER_TRANSACTION},
     * non-payment items allowed through as singletons.
     *
     * @return default batching options
     */
    public static BatchingOptions defaults() {
        return builder().build();
    }

    /**
     * Creates a batching-options builder.
     *
     * @return builder seeded with the conservative defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the maximum number of payment items merged into one transaction.
     *
     * @return positive per-transaction item cap
     */
    public int maxItemsPerTransaction() {
        return maxItemsPerTransaction;
    }

    /**
     * Whether non-payment-shaped items are allowed through as their own
     * single-item flows (never merged). When {@code false} a non-payment item
     * in the window fails it typed.
     *
     * @return {@code true} to pass non-payment items through as singletons
     */
    public boolean allowNonPaymentSingletons() {
        return allowNonPaymentSingletons;
    }

    /** Builder for {@link BatchingOptions}. */
    public static final class Builder {
        private int maxItemsPerTransaction = DEFAULT_MAX_ITEMS_PER_TRANSACTION;
        private boolean allowNonPaymentSingletons = true;

        private Builder() {
        }

        /**
         * Caps how many payment items may share one merged transaction.
         * Overflow within a lane splits into multiple merged flows.
         *
         * @param max positive per-transaction item cap
         * @return this builder
         */
        public Builder maxItemsPerTransaction(int max) {
            this.maxItemsPerTransaction = max;
            return this;
        }

        /**
         * Sets whether non-payment items pass through as single-item flows
         * (default {@code true}) or fail the window typed ({@code false}).
         *
         * @param allowed {@code true} to allow non-payment singletons through
         * @return this builder
         */
        public Builder allowNonPaymentSingletons(boolean allowed) {
            this.allowNonPaymentSingletons = allowed;
            return this;
        }

        /**
         * Validates and builds the options.
         *
         * @return immutable batching options
         * @throws IllegalArgumentException when {@code maxItemsPerTransaction < 1}
         */
        public BatchingOptions build() {
            return new BatchingOptions(this);
        }
    }
}
