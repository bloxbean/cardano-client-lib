package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

/**
 * The hard-fork combinator envelope of a block, {@code [era, block]}, optionally in tag 24, as received (ADR 0003 D4).
 * Deciding what a block is never requires being able to parse it: any era index is accepted, Byron and eras this reader
 * does not know included, and the block is not looked into. {@link RawBlock#of(BlockEnvelope)} and
 * {@link RawBlock#readAs(BlockEnvelope, com.bloxbean.cardano.client.spec.Era)} read it.
 * <p>
 * A block that is damaged has no envelope by these rules, as creating one walks every byte. {@link #eraIndexOf(byte[])}
 * and {@link #headerOf(byte[])} read only as far as they need, so what comes after may be malformed or missing.
 */
public final class BlockEnvelope {
    private static final int EMBEDDED_CBOR = 24;

    private final long eraIndex;
    private final CborSpan block;

    private BlockEnvelope(long eraIndex, CborSpan block) {
        this.eraIndex = eraIndex;
        this.block = block;
    }

    /**
     * @param bytes {@code [era, block]}, optionally in tag 24, exactly one CBOR item
     * @return the envelope
     * @throws CborRuntimeException if the bytes are not well-formed CBOR or not such an envelope
     */
    public static BlockEnvelope of(byte[] bytes) {
        return of(CborSpan.of(bytes));
    }

    /**
     * For a caller holding a span, such as the first item of a sequence from {@link CborSpan#at(byte[], int)}.
     *
     * @param span {@code [era, block]}, optionally in tag 24
     * @return the envelope
     * @throws CborRuntimeException if it is not such an envelope
     */
    public static BlockEnvelope of(CborSpan span) {
        CborSpan top = content(span);
        if (!isEnvelope(top))
            throw RawTx.error("A block envelope is an untagged array [era, block]", top);
        return new BlockEnvelope(RawItems.unsignedLong(top.get(0)), top.get(1));
    }

    // Takes a block out of its tag 24, the one wrapper a block may have.
    static CborSpan content(CborSpan span) {
        return span.tag() == EMBEDDED_CBOR ? span.embedded() : span;
    }

    // The envelope [era, block] is the one block-level array of two items with an untagged unsigned integer first.
    static boolean isEnvelope(CborSpan top) {
        if (top.majorType() != 4 || top.tag() != -1 || top.size() != 2)
            return false;
        CborSpan era = top.get(0);
        return era.majorType() == 0 && era.tag() == -1;
    }

    /**
     * Reads the era index of a block without reading the block. Tags on the envelope and on the index are looked
     * through, and tag 24 is not looked into.
     *
     * @param bytes the bytes of a block in its envelope; what follows the index may be malformed or missing
     * @return the era index, its 64 bits: a value of 2^63 or more reads as negative
     * @throws CborRuntimeException if the bytes do not start with an array whose first item is an unsigned integer
     */
    public static long eraIndexOf(byte[] bytes) {
        return RawItems.unsignedLong(CborSpan.arrayItem(bytes, 0));
    }

    /**
     * Reads the header of a block without reading what follows it, to report which block is damaged: this does not say
     * that the block is one. Tags on the arrays are looked through, and tag 24 is not looked into. Pair it with
     * {@link RawHeader#of(CborSpan)}, as the era index of a damaged block may be no era.
     *
     * @param bytes the bytes of a block in its envelope; what follows the header may be malformed or missing
     * @return the header as encoded
     * @throws CborRuntimeException if the bytes do not start with an array holding an era index and then an array whose
     *                              first item is well-formed
     */
    public static CborSpan headerOf(byte[] bytes) {
        return CborSpan.arrayItem(bytes, 1, 0);
    }

    /**
     * @return the era index, its 64 bits: a value of 2^63 or more reads as negative. This is the fact about the block,
     * whichever era it is read as
     */
    public long eraIndex() {
        return eraIndex;
    }

    /**
     * @return the block as encoded: for an index with no {@link EraSpec} row, whatever the envelope holds
     */
    public CborSpan block() {
        return block;
    }
}
