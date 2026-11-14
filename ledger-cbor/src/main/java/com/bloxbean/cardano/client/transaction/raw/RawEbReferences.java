package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The references of a Leios endorser block, {@code nonempty_omap<hash32, uint .size 4>}, as received: each
 * transaction's hash and its size in bytes, in wire order (cardano-ledger master {@code Dijkstra/Block/Endorser.hs}).
 * A header announces them by {@link #hash()} and size ({@link RawHeader#ebReferencesAnnouncement()}). An empty map,
 * a repeated hash and lengths are not checked (ADR 0003 D5).
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 */
public final class RawEbReferences {
    /**
     * One reference. Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param txHash the transaction's hash, of any length
     * @param size   the transaction's size in bytes, its 64 bits
     */
    public record Entry(byte[] txHash, long size) {
    }

    private final CborSpan span;

    private RawEbReferences(CborSpan span) {
        this.span = span;
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param bytes endorser block references, exactly one CBOR item
     * @return the view
     * @throws CborRuntimeException if the bytes are not well-formed CBOR or not a map
     */
    public static RawEbReferences of(byte[] bytes) {
        CborSpan span = CborSpan.of(bytes);
        RawItems.map(span, "eb_references");
        return new RawEbReferences(span);
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the references as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the hash a header announces: {@code blake2b256} of the references as encoded
     */
    public byte[] hash() {
        return Blake2bUtil.blake2bHash256(span.bytes());
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return every entry, in wire order
     * @throws CborRuntimeException if a key is not a byte string or a value not an unsigned integer
     */
    public List<Entry> entries() {
        List<Map.Entry<CborSpan, CborSpan>> entries = RawItems.map(span, "eb_references");
        List<Entry> references = new ArrayList<>(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries)
            references.add(new Entry(RawItems.bytes(entry.getKey()), RawItems.unsignedLong(entry.getValue())));
        return references;
    }
}
