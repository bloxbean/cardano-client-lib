package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;

/**
 * The table of a ledger record: its shape and its fields, with the eras each field is valid for (ADR 0003 D3). Tables
 * are written by hand and checked against the pinned CDDL by {@code tools/cddl-table-check}.
 */
public final class RecordSpec {
    /** How a record's fields are found. */
    public enum Shape {
        /** A map, whose keys are the fields. */
        KEYED,
        /** An array whose first item is the tag that picks one of the fields. */
        TAGGED,
        /** An array whose positions are the fields. */
        POSITIONAL
    }

    private final String name;
    private final Shape shape;
    private final List<FieldSpec> fields;
    private final int firstPosition;

    private RecordSpec(String name, Shape shape, List<FieldSpec> fields, int firstPosition) {
        this.name = name;
        this.shape = shape;
        this.fields = List.copyOf(fields);
        this.firstPosition = firstPosition;
    }

    static RecordSpec keyed(String name, List<FieldSpec> rows) {
        return new RecordSpec(name, Shape.KEYED, rows, 0);
    }

    static RecordSpec tagged(String name, List<FieldSpec> members) {
        return new RecordSpec(name, Shape.TAGGED, members, 0);
    }

    static RecordSpec positional(String name, FieldType... items) {
        return new RecordSpec(name, Shape.POSITIONAL, rows(0, items), 0);
    }

    // A member of a tagged union: its positions start after the tag, which the union has read.
    static RecordSpec member(long tag, FieldType... items) {
        return new RecordSpec("member " + tag, Shape.POSITIONAL, rows(1, items), 1);
    }

    private static List<FieldSpec> rows(int first, FieldType[] items) {
        List<FieldSpec> rows = new ArrayList<>(items.length);
        for (int i = 0; i < items.length; i++)
            rows.add(FieldSpec.of(first + i, items[i], true, Era.Shelley, null));
        return rows;
    }

    // The position of a positional record's first field: 1 in a member of a tagged union, after its tag.
    int firstPosition() {
        return firstPosition;
    }

    // The number of items of a positional record, with the tag of a member.
    int arity() {
        return firstPosition + fields.size();
    }

    public String name() {
        return name;
    }

    public Shape shape() {
        return shape;
    }

    public List<FieldSpec> fields() {
        return fields;
    }

    /**
     * @param key a key, tag or position
     * @param era the era being read
     * @return the row of that key valid in the era, or else any row of that key, or null if no era has the key. A key
     * can have several rows when its type or its meaning changes between eras.
     */
    public FieldSpec field(long key, Era era) {
        FieldSpec found = null;
        for (FieldSpec row : fields) {
            if (row.key() != key)
                continue;
            if (row.validIn(era))
                return row;
            if (found == null)
                found = row;
        }
        return found;
    }

    // The member of a tagged union with this tag, in any era, or null; a tag keeps one layout in every era.
    FieldSpec member(long tag) {
        for (FieldSpec row : fields) {
            if (row.key() == tag)
                return row;
        }
        return null;
    }

    /**
     * Walks a value of this record with a policy. For each record, nested ones included: {@link FormatPolicy#enter},
     * then per entry {@link FormatPolicy#entry}, {@link FormatPolicy#check} if the field is known and the walk of the
     * value if it has a table, and finally {@link FormatPolicy#missing}. Recursive types are leaves, and tables nest to
     * a small fixed depth, so a walk is stack-safe whatever the input (ADR 0003 I9).
     *
     * @param value  the record as encoded
     * @param era    the era to read it in
     * @param policy the policy, which throws to reject
     * @throws CborRuntimeException if the policy accepts a value the walk cannot
     *                              enter: not the map or array a table says it is
     */
    public void walk(CborSpan value, Era era, FormatPolicy policy) {
        RecordWalk.walk(this, value, era, policy);
    }

    @Override
    public String toString() {
        return name;
    }
}
