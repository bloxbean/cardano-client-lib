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
        /**
         * A map, whose keys are the fields; or, if {@link RecordSpec#keyType()} is set, a map of like entries: every
         * key has that type, and every value the type of the {@link #fields()} row valid in the era.
         */
        KEYED,
        /** An array whose first item is the tag that picks one of the fields. */
        TAGGED,
        /** An array whose positions are the fields. */
        POSITIONAL,
        /**
         * An item whose CBOR major type picks a row: its {@link FieldSpec#record()} if it has one, or else a value of
         * its type. Such as an output, an array or a map, or a value, a coin or an array. A policy is told of the
         * record the row names, and not of this one. An item of another major type is one the walk cannot enter.
         */
        BY_MAJOR_TYPE
    }

    private static final int MAJOR_BYTES = 2;
    /** An item of a set of key hashes, for a set whose type does not say what its items are. */
    static final RecordSpec KEY_HASH = byMajorType("addr_keyhash",
            FieldSpec.of(MAJOR_BYTES, FieldType.HASH28, false, Era.Shelley, null));

    private final String name;
    private final Shape shape;
    private final List<FieldSpec> fields;
    private final int firstPosition;
    private final int requiredArity;
    private final FieldType keyType;

    private RecordSpec(String name, Shape shape, List<FieldSpec> fields, int firstPosition) {
        this(name, shape, fields, firstPosition, null);
    }

    private RecordSpec(String name, Shape shape, List<FieldSpec> fields, int firstPosition, FieldType keyType) {
        this.name = name;
        this.shape = shape;
        this.fields = List.copyOf(fields);
        this.firstPosition = firstPosition;
        this.requiredArity = firstPosition + (int) fields.stream().filter(FieldSpec::required).count();
        this.keyType = keyType;
    }

    static RecordSpec keyed(String name, List<FieldSpec> rows) {
        return new RecordSpec(name, Shape.KEYED, rows, 0);
    }

    // A map of like entries: a row per era range of the values' type, each with key 0.
    static RecordSpec entries(String name, FieldType keyType, FieldSpec... values) {
        return new RecordSpec(name, Shape.KEYED, List.of(values), 0, keyType);
    }

    // A row per major type the item may have, with the major type as its key.
    static RecordSpec byMajorType(String name, FieldSpec... rows) {
        return new RecordSpec(name, Shape.BY_MAJOR_TYPE, List.of(rows), 0);
    }

    static RecordSpec tagged(String name, List<FieldSpec> members) {
        return new RecordSpec(name, Shape.TAGGED, members, 0);
    }

    static RecordSpec positional(String name, FieldType... items) {
        return new RecordSpec(name, Shape.POSITIONAL, rows(0, items), 0);
    }

    // A positional record whose rows say which items may be left out, from the end.
    static RecordSpec positional(String name, List<FieldSpec> rows) {
        return new RecordSpec(name, Shape.POSITIONAL, rows, 0);
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

    // The fewest items of a positional record, with the tag of a member.
    int requiredArity() {
        return requiredArity;
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
     * @return the type of every key of a map of like entries, or null for any other record
     */
    public FieldType keyType() {
        return keyType;
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

    /**
     * @param row a row of this record
     * @param era an era
     * @return whether the row's key means something else in the era: the row has ended and another row of the key is
     * valid (ADR 0003 D3). A field that is only outside its era has no other meaning, and stays readable (D5).
     */
    boolean replacedIn(FieldSpec row, Era era) {
        return row.endedBefore(era) && field(row.key(), era).validIn(era);
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
     * then per entry {@link FormatPolicy#entry}, {@link FormatPolicy#check} of a typed key if it has one, of the value
     * if the field is known and the walk of the value if it has a table, and finally {@link FormatPolicy#missing}.
     * Recursive types are leaves, and tables nest to a small fixed depth, so a walk is stack-safe whatever the input
     * (ADR 0003 I9).
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
