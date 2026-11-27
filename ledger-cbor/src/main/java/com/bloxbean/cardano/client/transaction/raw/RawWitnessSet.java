package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A witness set read in an era (ADR 0003 D3): {@link WitnessSetField} says which key a field has. A field outside its
 * era is read like any other, which is the policy's to reject (D5). Accessors read an item when they are called, and
 * throw {@link CborRuntimeException} if it is not what the table says. Scripts and Plutus data are recursive, and stay
 * spans. Lengths, ranges and empty sets are not checked.
 * <p>
 * A view from {@link RawTx#witnessSet(Era)} of {@link RawTx#of(byte[])} reads a list with its tag 258 or none, and
 * every redeemer it has. {@link #lenient(CborSpan, Era)} reads what the models of {@code transaction-spec} have always
 * accepted, which D7 keeps until a separate decision changes it: tags are looked through everywhere, and for redeemers
 * see {@link RawRedeemer}. {@link #tolerant(CborSpan, Era)} is for a chain follower, which must not lose a block its
 * node has accepted.
 */
public final class RawWitnessSet {
    private static final long SET_TAG = 258;

    private final CborSpan span;
    private final List<Map.Entry<CborSpan, CborSpan>> fields;
    private final Era era;
    private final boolean lenient;
    private final boolean tolerant;

    RawWitnessSet(CborSpan span, List<Map.Entry<CborSpan, CborSpan>> fields, Era era, boolean lenient) {
        this(span, fields, era, lenient, false);
    }

    private RawWitnessSet(CborSpan span, List<Map.Entry<CborSpan, CborSpan>> fields, Era era, boolean lenient,
                          boolean tolerant) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        this.span = span;
        this.fields = fields;
        this.era = era;
        this.lenient = lenient;
        this.tolerant = tolerant;
    }

    /**
     * @param span a witness set as encoded
     * @param era  the era to read it in
     * @return the view, read as the models read one
     * @throws CborRuntimeException if it is not a map, or it repeats a key
     */
    public static RawWitnessSet lenient(CborSpan span, Era era) {
        return new RawWitnessSet(span, RawItems.record(span, "witness set"), era, true);
    }

    /**
     * Reads a witness set as {@link #lenient(CborSpan, Era)} does, except that a repeated key means its last value, and
     * a value of the redeemer map may be indefinite. What decoding the witness set to a tree rejects is rejected.
     *
     * @param span a witness set as encoded
     * @param era  the era to read it in
     * @return the view
     * @throws CborRuntimeException if it is not a map, or decoding it would reject it
     */
    public static RawWitnessSet tolerant(CborSpan span, Era era) {
        CborSerializationUtil.checkTags(span);
        return new RawWitnessSet(span, RawItems.lastFirst(span, "witness set"), era, true, true);
    }

    /**
     * @return the witness set as encoded
     */
    public CborSpan span() {
        return span;
    }

    public Era era() {
        return era;
    }

    /**
     * @return the vkey witnesses, or empty if the field is absent
     */
    public Optional<List<RawVkeyWitness>> vkeyWitnesses() {
        return items(WitnessSetField.VKEY_WITNESSES_SET, RawVkeyWitness::of);
    }

    /**
     * @return the native scripts, or empty if the field is absent
     */
    public Optional<List<RawScript>> nativeScripts() {
        return scripts(RawScript.NATIVE_TYPE, WitnessSetField.NATIVE_SCRIPTS_SET);
    }

    /**
     * @return the bootstrap witnesses, or empty if the field is absent
     */
    public Optional<List<RawBootstrapWitness>> bootstrapWitnesses() {
        return items(WitnessSetField.BOOTSTRAP_WITNESSES_SET, RawBootstrapWitness::of);
    }

    /**
     * @return the Plutus V1 scripts, or empty if the field is absent
     */
    public Optional<List<RawScript>> plutusV1Scripts() {
        return scripts(RawScript.PLUTUS_V1_TYPE, WitnessSetField.PLUTUS_V1_SCRIPTS);
    }

    /**
     * @return the datums in encoded order, with any repeated datum kept, or empty if the field is absent
     */
    public Optional<List<RawDatum>> datums() {
        return items(WitnessSetField.PLUTUS_DATA_SET, RawDatum::new);
    }

    /**
     * @return the redeemers in encoded order, or empty if the field is absent; see {@link RawRedeemer} for a repeated
     * key
     */
    public Optional<List<RawRedeemer>> redeemers() {
        return value(WitnessSetField.REDEEMERS_FIELD)
                .map(field -> lenient ? RawRedeemer.readLenient(field, tolerant, era) : RawRedeemer.read(field));
    }

    /**
     * @return the Plutus V2 scripts, or empty if the field is absent
     */
    public Optional<List<RawScript>> plutusV2Scripts() {
        return scripts(RawScript.PLUTUS_V2_TYPE, WitnessSetField.PLUTUS_V2_SCRIPTS);
    }

    /**
     * @return the Plutus V3 scripts, or empty if the field is absent
     */
    public Optional<List<RawScript>> plutusV3Scripts() {
        return scripts(RawScript.PLUTUS_V3_TYPE, WitnessSetField.PLUTUS_V3_SCRIPTS);
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Plutus V4 scripts (key 8), or empty if the field is absent or the view's era is before Dijkstra,
     * where key 8 is unknown
     */
    public Optional<List<RawScript>> plutusV4Scripts() {
        if (WitnessSetField.PLUTUS_V4_SCRIPTS.spec().startsAfter(era))
            return Optional.empty();
        return scripts(RawScript.PLUTUS_V4_TYPE, WitnessSetField.PLUTUS_V4_SCRIPTS);
    }

    /**
     * @return every script: native, then Plutus V1 to V4
     */
    public List<RawScript> scripts() {
        List<RawScript> scripts = new ArrayList<>(nativeScripts().orElse(List.of()));
        scripts.addAll(plutusV1Scripts().orElse(List.of()));
        scripts.addAll(plutusV2Scripts().orElse(List.of()));
        scripts.addAll(plutusV3Scripts().orElse(List.of()));
        scripts.addAll(plutusV4Scripts().orElse(List.of()));
        return scripts;
    }

    private Optional<List<RawScript>> scripts(int type, WitnessSetField field) {
        return items(field, script -> new RawScript(type, script));
    }

    private <T> Optional<List<T>> items(WitnessSetField field, Function<CborSpan, T> read) {
        return value(field).map(value -> {
            List<CborSpan> items = lenient ? RawItems.array(value, "witness list") : value.untagIf(SET_TAG).items();
            List<T> views = new ArrayList<>(items.size());
            for (CborSpan item : items)
                views.add(read.apply(item));
            return views;
        });
    }

    private Optional<CborSpan> value(WitnessSetField field) {
        if (WitnessSetField.RECORD.replacedIn(field.spec(), era))
            return Optional.empty();
        return RawTx.field(fields, field.key());
    }
}
