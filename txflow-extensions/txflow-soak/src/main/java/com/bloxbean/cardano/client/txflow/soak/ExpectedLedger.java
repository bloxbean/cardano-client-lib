package com.bloxbean.cardano.client.txflow.soak;

import com.bloxbean.cardano.client.txflow.stream.TxStreamItemResult;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemStatus;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the soak run believes it asked for, and what it was told happened.
 *
 * <p>This is one half of the reconciliation: the run's own record of intent. The other half is
 * the chain. Keeping them separate is the point — if the stream's projection and the ledger
 * agreed by construction, comparing them would prove nothing.
 *
 * <p>Payments are spread over a fixed pool of recipients rather than one address per order, so
 * attribution stays cheap at high volume. Correctness is then checked per recipient: the sum of
 * a recipient's expected payments must equal its on-chain balance delta. That catches a double
 * payment, which per-item status alone cannot — a duplicated payment leaves both items looking
 * perfectly {@code CONFIRMED}.
 */
public final class ExpectedLedger {

    /** One submitted unit of work and everything later learned about it. */
    public static final class Entry {
        final String orderId;
        final int recipient;
        final BigInteger lovelace;
        volatile TxStreamItemStatus status;   // null until settled
        volatile String txHash;
        volatile String error;

        Entry(String orderId, int recipient, BigInteger lovelace) {
            this.orderId = orderId;
            this.recipient = recipient;
            this.lovelace = lovelace;
        }

        public String orderId() { return orderId; }
        public int recipient() { return recipient; }
        public BigInteger lovelace() { return lovelace; }
        public TxStreamItemStatus status() { return status; }
        public String txHash() { return txHash; }
        public String error() { return error; }

        boolean isConfirmed() { return status == TxStreamItemStatus.CONFIRMED; }

        boolean isTerminal() {
            return status == TxStreamItemStatus.CONFIRMED
                    || status == TxStreamItemStatus.FAILED
                    || status == TxStreamItemStatus.CANCELLED;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<Integer, BigInteger> baselineByRecipient = new ConcurrentHashMap<>();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong settled = new AtomicLong();

    /** Record the balance each recipient held before any soak payment was made. */
    public void captureBaseline(int recipient, BigInteger lovelace) {
        baselineByRecipient.put(recipient, lovelace);
    }

    public BigInteger baselineOf(int recipient) {
        return baselineByRecipient.getOrDefault(recipient, BigInteger.ZERO);
    }

    public Entry recordSubmitted(String orderId, int recipient, BigInteger lovelace) {
        Entry entry = new Entry(orderId, recipient, lovelace);
        entries.put(orderId, entry);
        submitted.incrementAndGet();
        return entry;
    }

    /** Record the stream's own verdict for an item. Safe to call from receipt callbacks. */
    public void recordOutcome(String orderId, TxStreamItemResult result) {
        Entry entry = entries.get(orderId);
        if (entry == null) return;
        entry.status = result.getStatus();
        entry.txHash = result.getTransactionHash();
        entry.error = result.getError() == null ? null : String.valueOf(result.getError());
        settled.incrementAndGet();
    }

    /** Record an item the stream refused outright (never accepted). */
    public void recordRejected(String orderId, String reason) {
        Entry entry = entries.get(orderId);
        if (entry == null) return;
        entry.status = TxStreamItemStatus.FAILED;
        entry.error = reason;
        settled.incrementAndGet();
    }

    /**
     * Total lovelace this recipient should have received, counting only items the stream
     * reported as confirmed. Items that failed before submission must not be expected on chain.
     */
    public BigInteger expectedDelta(int recipient) {
        BigInteger total = BigInteger.ZERO;
        for (Entry entry : entries.values()) {
            if (entry.recipient == recipient && entry.isConfirmed()) {
                total = total.add(entry.lovelace);
            }
        }
        return total;
    }

    public Collection<Entry> entries() {
        return entries.values();
    }

    public Entry entry(String orderId) {
        return entries.get(orderId);
    }

    public Collection<Integer> recipients() {
        return baselineByRecipient.keySet();
    }

    public long submittedCount() {
        return submitted.get();
    }

    public long settledCount() {
        return settled.get();
    }

    public long countWith(TxStreamItemStatus status) {
        return entries.values().stream().filter(e -> e.status == status).count();
    }

    public long nonTerminalCount() {
        return entries.values().stream().filter(e -> !e.isTerminal()).count();
    }
}
