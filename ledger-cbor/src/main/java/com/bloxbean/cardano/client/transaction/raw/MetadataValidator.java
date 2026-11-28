package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Transaction metadata, decoded as Conway's decoders do before protocol version 12: a {@code Map Word64 Metadatum} (no
 * duplicate label), each {@code Metadatum} ({@code decodeMetadatum}, Metadata.hs:151-230) an integer of at most 64 bits
 * in magnitude (major type 0 or 1, no big-number tags), a byte string or a UTF-8 text string of at most 64 bytes
 * (definite or indefinite), a list, or a map (a list of pairs, duplicates allowed). Anything else does not decode: at
 * cardano-ledger {@code f649f975} the 64-byte limits are decoding failures, not {@code InvalidMetadata}. Part of the
 * Haskell-strict policy (ADR 0005); where metadata is in the auxiliary data is for the reader to know, and
 * {@link HaskellPolicy} checks it there. A metadatum is checked without recursion, in linear time, whatever its
 * nesting depth.
 */
public final class MetadataValidator {

    private static final int MAX_METADATUM_BYTES = 64;

    private MetadataValidator() {
    }

    /**
     * @param bytes the metadata map, as encoded, nothing after it
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    public static void validate(byte[] bytes) {
        validate(StrictCbor.span(bytes));
    }

    /**
     * @param item the metadata, as encoded
     * @throws LedgerDecodingException when Haskell would not decode it
     */
    static void validate(CborSpan item) {
        Set<BigInteger> labels = new HashSet<>();
        for (Map.Entry<CborSpan, CborSpan> entry : StrictCbor.map(item)) {
            BigInteger label = StrictCbor.unsigned(entry.getKey());
            if (!labels.add(label)) {
                throw new LedgerDecodingException(Reason.REPEATED_KEY, "duplicate metadata label " + label);
            }
            metadatum(entry.getValue());
        }
    }

    /** {@code decodeMetadatum} with the size checks of decoder versions above 2. */
    private static void metadatum(CborSpan item) {
        HeadCursor c = new HeadCursor(item.buffer(), item.offset(), item.offset() + item.length(), Reason.METADATA);
        HeadCursor.Containers open = new HeadCursor.Containers();
        do {
            int major = c.peek() >>> 5;
            switch (major) {
                case 0:
                case 1:
                    c.next();
                    break;
                case 2:
                    if (string(c, 2) > MAX_METADATUM_BYTES) {
                        throw c.reject("bytes .size (0..64): bytestring exceeds 64 bytes");
                    }
                    break;
                case 3:
                    if (string(c, 3) > MAX_METADATUM_BYTES) {
                        throw c.reject("text .size (0..64): text exceeds 64 bytes");
                    }
                    break;
                case 4:
                case 5:
                    open.open(major, c.containerHeader(major));
                    break;
                default:
                    throw c.reject("Unsupported token type in metadata (major type " + major + ")");
            }
        } while (open.next(c));
    }

    /**
     * Reads a byte or text string, definite or indefinite; {@code decodeString} validates each text chunk as UTF-8.
     *
     * @return its length, the chunks of an indefinite one together
     */
    private static int string(HeadCursor c, int major) {
        if ((c.peek() & 0x1f) != 31) {
            return chunk(c, major);
        }
        c.next();
        int length = 0;
        while (!c.atBreak()) {
            length += chunk(c, major);
        }
        return length;
    }

    private static int chunk(HeadCursor c, int major) {
        byte[] chunk = c.definiteString(major);
        if (major == 3) {
            try {
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(chunk));
            } catch (CharacterCodingException e) {
                throw c.reject("metadata text is not valid UTF-8");
            }
        }
        return chunk.length;
    }
}
