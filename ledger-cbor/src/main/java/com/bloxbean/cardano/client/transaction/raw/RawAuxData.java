package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bloxbean.cardano.client.transaction.raw.RawTx.error;

/**
 * Auxiliary data as received, in any of its forms: a metadata map (Shelley), {@code [metadata, [* native_script]]}
 * (Allegra) or {@code #6.259({...})} (Alonzo), read in an era (ADR 0003 D3). Metadata and scripts are recursive, and
 * stay spans (D5). The form may be used in any era, which is the policy's to reject (D5).
 * <p>
 * A view from {@link RawTx#auxData(Era)} of {@link RawTx#of(byte[])} takes the form from the outermost tag, and reads
 * lists as they are. {@link #lenient(CborSpan, Era)} reads what the models of {@code transaction-spec} have always
 * accepted, which D7 keeps until a separate decision changes it: tags are looked through, and the one closest to a map
 * says what it is; a tagged map may not repeat a key, of any kind; and the array form is definite.
 * {@link #tolerant(CborSpan, Era)} is for a chain follower, which must not lose a block its node has accepted.
 */
public final class RawAuxData {
    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_MAP = 5;
    private static final int MAJOR_TAG = 6;

    /** The {@code auxiliary_data_array} rule. */
    static final RecordSpec ARRAY_FORM = RecordSpec.positional("auxiliary_data_array", List.of(
            FieldSpec.of(0, FieldType.METADATA, true, Era.Allegra, null),
            FieldSpec.of(1, FieldType.LIST, true, Era.Allegra, null, RawScript.NATIVE)));
    /** The {@code auxiliary_data} rule. Metadata is recursive, so a plain map is one value. */
    static final RecordSpec RECORD = RecordSpec.byMajorType("auxiliary_data",
            FieldSpec.of(MAJOR_MAP, FieldType.METADATA, false, Era.Shelley, null),
            FieldSpec.of(MAJOR_ARRAY, FieldType.AUXILIARY_DATA, false, Era.Allegra, null, ARRAY_FORM),
            FieldSpec.of(MAJOR_TAG, FieldType.AUXILIARY_DATA, false, Era.Alonzo, null, AuxDataField.RECORD));

    private final CborSpan span;
    private final Era era;
    private final boolean lenient;
    // the metadata of a plain map or of the array form, and the scripts of the array form
    private final CborSpan metadata;
    private final CborSpan arrayScripts;
    // the entries of the keyed form
    private final List<Map.Entry<CborSpan, CborSpan>> fields;

    private RawAuxData(CborSpan span, Era era, boolean lenient, CborSpan metadata, CborSpan arrayScripts,
                       List<Map.Entry<CborSpan, CborSpan>> fields) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        this.span = span;
        this.era = era;
        this.lenient = lenient;
        this.metadata = metadata;
        this.arrayScripts = arrayScripts;
        this.fields = fields;
    }

    // Of a view that has read the entries of the keyed form, if it is one. No other tag is known.
    static RawAuxData of(CborSpan span, List<Map.Entry<CborSpan, CborSpan>> fields, Era era) {
        if (fields != null)
            return new RawAuxData(span, era, false, null, null, fields);
        if (span.tag() != -1)
            throw error("Unknown aux data shape", span);
        if (span.majorType() == MAJOR_MAP)
            return new RawAuxData(span, era, false, span, null, null);
        if (span.majorType() != MAJOR_ARRAY)
            throw error("Unknown aux data shape", span);
        List<CborSpan> items = span.items();
        checkArrayForm(items, span);
        return new RawAuxData(span, era, false, items.get(0), items.get(1), null);
    }

    /**
     * @param span aux data as encoded
     * @param era  the era to read it in
     * @return the view, read as the models read one
     * @throws CborRuntimeException if it is none of the forms
     */
    public static RawAuxData lenient(CborSpan span, Era era) {
        int major = span.majorType();
        if (major == MAJOR_MAP) {
            List<Map.Entry<CborSpan, CborSpan>> entries = span.isTagged() ? RawItems.record(span, "aux data") : null;
            return RawItems.innermostTag(span) == AuxDataField.RECORD.tag()
                    ? new RawAuxData(span, era, true, null, null, entries)
                    : new RawAuxData(span, era, true, span, null, null);
        }
        if (major != MAJOR_ARRAY)
            throw error("Unknown aux data shape", span);
        if (!RawItems.indefiniteRecordsAllowed(era) && RawItems.indefiniteWhenDecoded(span))
            throw error("Invalid aux data: expected a definite-length array", span);
        List<CborSpan> items = RawItems.array(span, ARRAY_FORM.name());
        checkArrayForm(items, span);
        if (items.get(1).majorType() != MAJOR_ARRAY)
            throw error("Aux data [metadata, scripts] has an array of scripts", span);
        return new RawAuxData(span, era, true, items.get(0), items.get(1), null);
    }

    /**
     * Reads aux data as {@link #lenient(CborSpan, Era)} does, except that a repeated key means its last value, the
     * array form may be indefinite and have more than two items, and an item of none of the forms reads as aux data
     * with nothing in it. What decoding it to a tree rejects is rejected.
     *
     * @param span aux data as encoded
     * @param era  the era to read it in
     * @return the view
     * @throws CborRuntimeException if it is an array of fewer than two items or with no array of scripts, or decoding
     *                              it would reject it
     */
    public static RawAuxData tolerant(CborSpan span, Era era) {
        CborSerializationUtil.checkTags(span);
        int major = span.majorType();
        if (major == MAJOR_MAP)
            return RawItems.innermostTag(span) == AuxDataField.RECORD.tag()
                    ? new RawAuxData(span, era, true, null, null, RawItems.lastFirst(span, "aux data"))
                    : new RawAuxData(span, era, true, span, null, null);
        if (major != MAJOR_ARRAY)
            return new RawAuxData(span, era, true, null, null, List.of());
        List<CborSpan> items = RawItems.array(span, ARRAY_FORM.name());
        if (items.size() < ARRAY_FORM.arity())
            throw error("Aux data [metadata, scripts] has " + ARRAY_FORM.arity() + " items, found " + items.size(),
                    span);
        if (items.get(1).majorType() != MAJOR_ARRAY)
            throw error("Aux data [metadata, scripts] has an array of scripts", span);
        return new RawAuxData(span, era, true, items.get(0), items.get(1), null);
    }

    private static void checkArrayForm(List<CborSpan> items, CborSpan span) {
        if (items.size() != ARRAY_FORM.arity())
            throw error("Aux data [metadata, scripts] has " + ARRAY_FORM.arity() + " items, found " + items.size(),
                    span);
    }

    /**
     * @return the aux data, as encoded
     */
    public CborSpan span() {
        return span;
    }

    public Era era() {
        return era;
    }

    /**
     * @return the metadata as encoded, or empty if the keyed form has none
     * @throws CborRuntimeException if it is not a map
     */
    public Optional<CborSpan> metadata() {
        Optional<CborSpan> found = fields == null ? Optional.of(metadata) : field(AuxDataField.METADATA);
        found.ifPresent(item -> {
            if (item.majorType() != MAJOR_MAP)
                throw error("Metadata is a map", item);
        });
        return found;
    }

    /**
     * @param field a field of the keyed form
     * @return whether the aux data has it: the other forms have none of its fields, and a Dijkstra field (key 5) is an
     * unknown key, so absent, before Dijkstra
     */
    public boolean has(AuxDataField field) {
        return field(field).isPresent();
    }

    /**
     * @return the native scripts, none if there are none
     * @throws CborRuntimeException if the field is not an array
     */
    public List<RawScript> nativeScripts() {
        return fields == null && arrayScripts != null ? scripts(RawScript.NATIVE_TYPE, arrayScripts)
                : scripts(RawScript.NATIVE_TYPE, AuxDataField.NATIVE_SCRIPTS);
    }

    public List<RawScript> plutusV1Scripts() {
        return scripts(RawScript.PLUTUS_V1_TYPE, AuxDataField.PLUTUS_V1_SCRIPTS);
    }

    public List<RawScript> plutusV2Scripts() {
        return scripts(RawScript.PLUTUS_V2_TYPE, AuxDataField.PLUTUS_V2_SCRIPTS);
    }

    public List<RawScript> plutusV3Scripts() {
        return scripts(RawScript.PLUTUS_V3_TYPE, AuxDataField.PLUTUS_V3_SCRIPTS);
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Plutus V4 scripts of the keyed form (key 5), none if there are none or the view's era is before
     * Dijkstra, where key 5 is unknown
     * @throws CborRuntimeException if the field is not an array
     */
    public List<RawScript> plutusV4Scripts() {
        return scripts(RawScript.PLUTUS_V4_TYPE, AuxDataField.PLUTUS_V4_SCRIPTS);
    }

    /**
     * @return every script: native, then Plutus V1 to V4
     */
    public List<RawScript> scripts() {
        List<RawScript> scripts = new ArrayList<>(nativeScripts());
        scripts.addAll(plutusV1Scripts());
        scripts.addAll(plutusV2Scripts());
        scripts.addAll(plutusV3Scripts());
        scripts.addAll(plutusV4Scripts());
        return scripts;
    }

    private List<RawScript> scripts(int type, AuxDataField field) {
        return field(field).map(list -> scripts(type, list)).orElse(List.of());
    }

    private List<RawScript> scripts(int type, CborSpan list) {
        List<CborSpan> items = lenient ? RawItems.array(list, "list of scripts") : list.items();
        List<RawScript> scripts = new ArrayList<>(items.size());
        for (CborSpan script : items)
            scripts.add(new RawScript(type, script));
        return scripts;
    }

    // A field of Dijkstra is an unknown key before it, so it reads as absent there, as in RawTxBody.since. The fields
    // of earlier eras keep their tolerant reading in every era (ADR 0003 I14, D5).
    private Optional<CborSpan> field(AuxDataField field) {
        if (fields == null || AuxDataField.RECORD.replacedIn(field.spec(), era)
                || field.spec().from() == Era.Dijkstra && field.spec().startsAfter(era))
            return Optional.empty();
        return RawTx.field(fields, field.key());
    }
}
