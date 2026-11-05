package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;

/**
 * One row of a ledger record table (ADR 0003 D3).
 *
 * @param key      the map key of a keyed record, the tag of a tagged union's member, the position in a positional
 *                 record, or the major type of a record that has a shape per container; 0 in a map of like entries
 * @param type     the value's type
 * @param required whether the record must have the field in the eras it is valid for
 * @param from     the first era of the field
 * @param until    the last era of the field; null if it has not ended
 * @param record   the table of the value, or of each item of a collection; null for a leaf
 */
public record FieldSpec(long key, FieldType type, boolean required, Era from, Era until, RecordSpec record) {

    static FieldSpec of(long key, FieldType type, boolean required, Era from, Era until) {
        return new FieldSpec(key, type, required, from, until, type.record());
    }

    static FieldSpec of(long key, FieldType type, boolean required, Era from, Era until, RecordSpec record) {
        return new FieldSpec(key, type, required, from, until, record);
    }

    /** @return a member of a tagged union, whose items are read with its own table */
    static FieldSpec member(long tag, Era from, Era until, FieldType... items) {
        return new FieldSpec(tag, FieldType.MEMBER, false, from, until, RecordSpec.member(tag, items));
    }

    /**
     * @param era an era
     * @return whether the field belongs to the era
     */
    public boolean validIn(Era era) {
        return era.compareTo(from) >= 0 && (until == null || era.compareTo(until) <= 0);
    }

    /**
     * @param era an era
     * @return whether the field's last era is before this era
     */
    boolean endedBefore(Era era) {
        return until != null && era.compareTo(until) > 0;
    }

    /**
     * @param era an era
     * @return whether a record of the era must have the field
     */
    public boolean requiredIn(Era era) {
        return required && validIn(era);
    }
}
