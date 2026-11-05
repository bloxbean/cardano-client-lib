package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.Era;

/**
 * What a caller decides about the shape of a ledger record while the reader walks it (ADR 0003 D6). The reader owns the
 * walk and the tables; a policy owns the rejections, which it makes by throwing, so it needs no knowledge of where a
 * field is. See {@link RecordSpec#walk(CborSpan, Era, FormatPolicy)} for the order of the calls.
 * <ul>
 *     <li>A keyed record reports each entry with its {@code key} as encoded.</li>
 *     <li>A tagged union reports its tag as {@code key} and the whole member as {@code value}, then walks the
 *     member.</li>
 *     <li>A positional record has no key in its bytes, so {@code key} is null and {@link FieldSpec#key()} is the
 *     position. An item beyond the record's last position has a null {@code field}.</li>
 *     <li>A map of like entries ({@link RecordSpec#keyType()}) reports every entry with the same {@code field}, and
 *     checks each key with its type, and walks it if its type has a table, such as a redeemer's key. Keys that decode
 *     alike are one key for {@code occurrence}.</li>
 *     <li>A record with a shape per container ({@link RecordSpec.Shape#BY_MAJOR_TYPE}) is not entered. It reports the
 *     row its item's major type picks as an entry with a null {@code key} and the whole item as {@code value}, so that
 *     {@link FieldSpec#validIn(Era)} says whether that form belongs to the era, even for an empty container; the
 *     row's {@link FieldSpec#key()} is the major type. Then the record the row names is walked, or the item is checked
 *     with the row's type and, for a row that says only what container the item is, each item is walked with its
 *     table.</li>
 * </ul>
 */
public interface FormatPolicy {
    /**
     * @param record    the record
     * @param container the record as encoded, before anything is normalised: its tag and length form are visible
     * @param era       the era being read
     */
    default void enter(RecordSpec record, CborSpan container, Era era) {
    }

    /**
     * Called for every raw entry in source order, repeated and unknown ones included.
     *
     * @param record     the record
     * @param field      the field, or null if the key is in no era's table or is not an untagged integer;
     *                   {@link FieldSpec#validIn(Era)} says whether a known field belongs to this era
     * @param key        the key as encoded, or null in a positional record
     * @param value      the value as encoded
     * @param occurrence how many earlier entries have the same decoded key, so {@code 02} and {@code 18 02} count as
     *                   one key
     * @param era        the era being read
     */
    default void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence, Era era) {
    }

    /**
     * Called after the entries, for each field required in this era that the record lacks.
     *
     * @param record    the record
     * @param field     the field
     * @param container the record as encoded
     * @param era       the era being read
     */
    default void missing(RecordSpec record, FieldSpec field, CborSpan container, Era era) {
    }

    /**
     * Called for each known value. A value of a recursive type (Plutus data, a native script, metadata) is one value.
     *
     * @param type  the value's type
     * @param value the value as encoded
     * @param era   the era being read
     */
    default void check(FieldType type, CborSpan value, Era era) {
    }

    /** Rejects nothing, so only what the reader itself rejects (ADR 0003 D5) fails a walk. */
    FormatPolicy ACCEPT_ALL = new FormatPolicy() {
    };
}
