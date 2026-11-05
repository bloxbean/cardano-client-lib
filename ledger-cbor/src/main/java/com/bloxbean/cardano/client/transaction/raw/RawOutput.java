package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A transaction output as received, in either form: the legacy array {@code [address, value, ? datum_hash]} or the map
 * {@code {0: address, 1: value, ? 2: datum_option, ? 3: script_ref}}.
 * <p>
 * {@link RawTx#outputs()} reads every item when it creates the view, and rejects anything but the shapes above, with no
 * tags. {@link #lenient(CborSpan)} reads what the models of {@code transaction-spec} have always accepted, which ADR
 * 0003 D7 keeps until a separate decision changes it: an item is read when it is asked for, its tags are looked through, a
 * map form may lack its address, a datum option's bytes and a script reference need no tag 24 and are not looked into,
 * and a datum option of another kind holds no datum.
 */
public final class RawOutput {
    /** The legacy form, {@code alonzo_transaction_output}. */
    static final RecordSpec LEGACY = RecordSpec.positional("alonzo_transaction_output", List.of(
            FieldSpec.of(0, FieldType.ADDRESS, true, Era.Shelley, null),
            FieldSpec.of(1, FieldType.VALUE, true, Era.Shelley, null),
            FieldSpec.of(2, FieldType.HASH32, false, Era.Alonzo, null)));

    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_MAP = 5;
    /** The {@code transaction_output} rule: the legacy array or the map. */
    static final RecordSpec RECORD = RecordSpec.byMajorType("transaction_output",
            FieldSpec.of(MAJOR_ARRAY, FieldType.TRANSACTION_OUTPUT, false, Era.Shelley, null, LEGACY),
            FieldSpec.of(MAJOR_MAP, FieldType.TRANSACTION_OUTPUT, false, Era.Babbage, null, TxOutputField.RECORD));

    private final CborSpan span;
    private final boolean lenient;
    private final PositionalRecord legacy;
    private final List<Map.Entry<CborSpan, CborSpan>> fields;

    private RawOutput(CborSpan span, boolean lenient, PositionalRecord legacy,
                      List<Map.Entry<CborSpan, CborSpan>> fields) {
        this.span = span;
        this.lenient = lenient;
        this.legacy = legacy;
        this.fields = fields;
    }

    static RawOutput of(CborSpan output) {
        boolean array = output.majorType() == MAJOR_ARRAY;
        if (array && output.isTagged())
            throw RawTx.error("A legacy output has no tag", output);
        RawOutput read = array ? new RawOutput(output, false, PositionalRecord.of(LEGACY, output, true), null)
                : new RawOutput(output, false, null, RawTx.record(output, "output"));
        read.address();
        read.value();
        read.datumHash();
        read.inlineDatum();
        read.scriptRef();
        return read;
    }

    /**
     * @param output an output as encoded
     * @return the view, read as the models read one
     * @throws CborRuntimeException if it is not an array of 2 or 3 items, or a map that repeats no key
     */
    public static RawOutput lenient(CborSpan output) {
        int major = output.majorType();
        if (major == MAJOR_ARRAY)
            return new RawOutput(output, true, PositionalRecord.of(LEGACY, output, true), null);
        if (major == MAJOR_MAP)
            return new RawOutput(output, true, null, RawItems.record(output, "output"));
        throw RawTx.error("An output is an array or a map", output);
    }

    /**
     * @return the output, as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * @return the address item
     * @throws CborRuntimeException if a map form has none
     */
    public CborSpan address() {
        return required(FieldType.ADDRESS, TxOutputField.OUTPUT_ADDRESS);
    }

    /**
     * @return the address's bytes, or empty if a map form has none
     */
    public Optional<byte[]> addressBytes() {
        return (legacy != null ? Optional.of(legacy.value(FieldType.ADDRESS)) : field(TxOutputField.OUTPUT_ADDRESS))
                .map(RawItems::bytes);
    }

    /**
     * @return the value: a coin, or {@code [coin, multiasset]}, as encoded
     * @throws CborRuntimeException if a map form has none
     */
    public CborSpan value() {
        return required(FieldType.VALUE, TxOutputField.AMOUNT);
    }

    /**
     * @return the value, read as this view's form reads one: for {@link #lenient(CborSpan)} see
     * {@link RawValue#lenient(CborSpan)}, else {@link RawValue#of(CborSpan)}, which is never empty
     */
    public Optional<RawValue> amount() {
        return lenient ? RawValue.lenient(value()) : Optional.of(RawValue.of(value()));
    }

    /**
     * @return the datum hash, from a legacy output or a datum option {@code [0, hash]}
     */
    public Optional<byte[]> datumHash() {
        if (legacy != null)
            return legacy.optional(FieldType.HASH32).map(this::bytes);
        return datumOption(RawDatum.HASH_OPTION).map(this::bytes);
    }

    /**
     * @return the inline datum, the payload of a datum option {@code [1, #6.24(bytes)]}
     */
    public Optional<RawDatum> inlineDatum() {
        return datumOption(RawDatum.INLINE_OPTION).map(data -> new RawDatum(data.embedded()));
    }

    /**
     * @return the bytes of an inline datum, as received
     */
    public Optional<byte[]> inlineDatumBytes() {
        return datumOption(RawDatum.INLINE_OPTION).map(this::embeddedBytes);
    }

    /**
     * @return the reference script, the payload of {@code #6.24(bytes .cbor [type, script])}
     */
    public Optional<RawScript> scriptRef() {
        return field(TxOutputField.OUTPUT_SCRIPT_REF).map(ref -> RawScript.ofScriptRef(ref.embedded()));
    }

    /**
     * @return the bytes of a reference script, as received
     */
    public Optional<byte[]> scriptRefBytes() {
        return field(TxOutputField.OUTPUT_SCRIPT_REF).map(this::embeddedBytes);
    }

    private byte[] bytes(CborSpan item) {
        return lenient ? RawItems.bytes(item) : item.byteString();
    }

    private byte[] embeddedBytes(CborSpan item) {
        return lenient ? RawItems.bytes(item) : item.embedded().bytes();
    }

    // The second item of the datum option, if it is of this kind.
    private Optional<CborSpan> datumOption(FieldSpec kind) {
        Optional<CborSpan> option = field(TxOutputField.OUTPUT_DATUM);
        if (option.isEmpty())
            return option;
        List<CborSpan> items = lenient ? RawItems.array(option.get(), RawDatum.OPTION.name())
                : RawTx.pair(option.get(), "A datum option");
        if (items.size() != 2)
            throw RawTx.error("A datum option has 2 items, found " + items.size(), option.get());
        return optionKind(items.get(0), option.get()) == kind.key() ? Optional.of(items.get(1)) : Optional.empty();
    }

    // The models compare the kind with an untagged integer, and any other kind holds no datum.
    private long optionKind(CborSpan kind, CborSpan option) {
        if (lenient)
            return !kind.isTagged() && kind.majorType() == 0 ? kind.asBigInteger().longValue() : -1;
        long value = kind.asLong();
        if (RawDatum.OPTION.member(value) == null)
            throw RawTx.error("Unknown datum option " + value, option);
        return value;
    }

    private CborSpan required(FieldType type, TxOutputField field) {
        if (legacy != null)
            return legacy.value(type);
        return field(field).orElseThrow(() -> RawTx.error("An output without " + field, span));
    }

    private Optional<CborSpan> field(TxOutputField field) {
        return fields != null ? RawTx.field(fields, field.key()) : Optional.empty();
    }
}
