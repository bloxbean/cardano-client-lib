package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.util.Objects;

/**
 * Bytes that Haskell's decoders would not decode, found by a strict read of {@link HaskellPolicy} (ADR 0005 D4, D10).
 * It is a {@link CborRuntimeException}, so a caller that catches the reader's exception catches this one too.
 *
 * <p>The {@link #reason() reason} is a stable code; the message is free text, for people, and is never compared. A
 * strict read rejects with this exception only: bytes that are not well formed are {@link Reason#NOT_WELL_FORMED},
 * and a rejection by the reader's own checks (the arity or form of a record) is {@link Reason#STRUCTURE}.</p>
 *
 * <p>Passing a strict read is not ledger validity: fees, balance, witnesses, scripts and every rule that crosses
 * fields are the ledger's, not the decoder's.</p>
 */
public class LedgerDecodingException extends CborRuntimeException {

    /** Why Haskell's decoder would not decode the bytes. A fixed set: a new code is an API change. */
    public enum Reason {
        /** Not one well-formed CBOR item, truncated, or with bytes after it (RFC 8949 section 5.3). */
        NOT_WELL_FORMED,
        /** The reader's own checks: the arity or form of a record or a transaction, as read by CCL's reader. */
        STRUCTURE,
        /** An item of another major type than the rule's, or one that is tagged where no tag is read. */
        UNEXPECTED_TYPE,
        /** A tag where another tag, or none, is required (tag 24, tag 30, a record's own tag). */
        TAG,
        /** A key, item, union member or major type the record does not have. */
        UNKNOWN_FIELD,
        /** A field that the record has, but not in this era. */
        FIELD_NOT_IN_ERA,
        /** A key that occurs twice in a record or in metadata. */
        REPEATED_KEY,
        /** Two items of a set, or two keys of a map, that decode to equal values. */
        DUPLICATE_ITEM,
        /** A field the record requires in this era is absent. */
        MISSING_FIELD,
        /** An indefinite-length array where a definite one is required (a {@code RecD} record below version 12). */
        INDEFINITE_RECORD,
        /** An indefinite-length (chunked) byte or text string where a definite one is required. */
        INDEFINITE_STRING,
        /** A byte string of the wrong size: a hash, key, signature, proof or address part. */
        BYTES_SIZE,
        /** An integer outside its type's range, or a bignum where an integer is read. */
        INTEGER_RANGE,
        /** Zero where a positive or non-zero value is required (a positive coin, a multi-asset quantity). */
        ZERO,
        /** An empty collection where a non-empty one is required. */
        EMPTY_COLLECTION,
        /** A multi-asset too big to compact ({@code isMultiAssetSmallEnough}). */
        MULTI_ASSET_TOO_BIG,
        /** A map with an 8-byte length head where the decoder dispatches on cborg's token type. */
        MAP_HEAD_WIDTH,
        /** An address Haskell's address decoder refuses. */
        ADDRESS,
        /** A URL or DNS name that is not UTF-8 or is longer than its bound. */
        TEXT_BOUNDS,
        /** A rational that is not a tag-30 pair, has a zero denominator, or is out of its type's range. */
        RATIONAL,
        /** Plutus {@code Data} that plutus-core's decoder refuses. */
        PLUTUS_DATA,
        /** Metadata that the ledger's metadatum decoder refuses. */
        METADATA,
        /** A script that is not a script of the era: its language, its form, or a native script's structure. */
        SCRIPT,
        /** A value nested deeper than the equality check reads (no Conway value the ledger compares is near it). */
        NESTING_TOO_DEEP
    }

    private final Reason reason;

    /**
     * @param reason  why the bytes do not decode
     * @param message what was found and where, for people
     */
    public LedgerDecodingException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * @param reason  why the bytes do not decode
     * @param message what was found and where, for people
     * @param cause   the reader's exception that this one classifies
     */
    public LedgerDecodingException(Reason reason, String message, Exception cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** @return why the bytes do not decode: a stable code */
    public Reason reason() {
        return reason;
    }
}
