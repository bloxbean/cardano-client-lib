package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.transaction.raw.RawTx.error;

/**
 * A redeemer as received, from either form of the {@code redeemers} rule: the array
 * {@code [tag, index, data, ex_units]} or the Conway map entry {@code [tag, index] => [data, ex_units]}.
 * <p>
 * The {@code lenient} readings read what the models of {@code transaction-spec} have always accepted, which ADR 0003 D7
 * keeps until a separate decision changes it: tags on an item are looked through, a map value is definite, a field of
 * neither form holds no redeemer, and a map keeps one entry per key, equal as a decoded map's are, with the key's first
 * encoding. A view of {@link RawWitnessSet#tolerant} reads a map as they do, and a map value may be indefinite.
 *
 * @param tag     the purpose: 0 spend, 1 mint, 2 cert, 3 reward, 4 voting, 5 proposing
 * @param index   the index of the item the redeemer is for
 * @param data    the redeemer data, as encoded (see {@link RawDatum} to hash or decode it)
 * @param exUnits the execution units {@code [mem, steps]}, as encoded
 * @param key     the key {@code [tag, index]} of the map form, as encoded; null for the array form
 * @param span    the redeemer as encoded: the item of the array form, or the value of the map form
 */
public record RawRedeemer(int tag, long index, CborSpan data, CborSpan exUnits, CborSpan key, CborSpan span) {
    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_MAP = 5;

    /** The array form of a redeemer. */
    static final RecordSpec ITEM = RecordSpec.positional("redeemer", FieldType.REDEEMER_TAG, FieldType.UINT32,
            FieldType.PLUTUS_DATA, FieldType.EX_UNITS);
    /** The key of the map form. */
    static final RecordSpec KEY = RecordSpec.positional("redeemer_key", FieldType.REDEEMER_TAG, FieldType.UINT32);
    /** The value of the map form. */
    static final RecordSpec VALUE = RecordSpec.positional("redeemer_value", FieldType.PLUTUS_DATA, FieldType.EX_UNITS);
    /** The map form. */
    static final RecordSpec MAP = RecordSpec.entries("redeemers_map", FieldType.REDEEMER_KEY,
            FieldSpec.of(0, FieldType.REDEEMER_VALUE, false, Era.Conway, null));
    /** The {@code redeemers} rule: a list to Babbage, and a list or a map in Conway, neither empty. */
    static final RecordSpec RECORD = RecordSpec.byMajorType("redeemers",
            FieldSpec.of(MAJOR_ARRAY, FieldType.LIST, false, Era.Alonzo, Era.Babbage, ITEM),
            FieldSpec.of(MAJOR_ARRAY, FieldType.NONEMPTY_LIST, false, Era.Conway, null, ITEM),
            FieldSpec.of(MAJOR_MAP, FieldType.NONEMPTY_MAP, false, Era.Conway, null, MAP));

    /**
     * @param item the array form, as encoded
     * @return the redeemer, read as the models read one
     * @throws CborRuntimeException if it is not an array of 4 items, or its tag or index is not an unsigned integer
     */
    public static RawRedeemer lenient(CborSpan item) {
        return ofItem(item, RawItems.array(item, ITEM.name()), true);
    }

    /**
     * @param key   the key of the map form, as encoded
     * @param value the value of the map form, as encoded
     * @return the redeemer, read as the models read one
     * @throws CborRuntimeException if they are not arrays of 2 items and a definite value, or the tag or index is not
     * an                              unsigned integer
     */
    public static RawRedeemer lenient(CborSpan key, CborSpan value) {
        if (RawItems.indefiniteWhenDecoded(value))
            throw error("Invalid redeemer value: expected a definite-length array", value);
        return tolerant(key, value);
    }

    private static RawRedeemer tolerant(CborSpan key, CborSpan value) {
        return ofEntry(key, RawItems.array(key, KEY.name()), value, RawItems.array(value, VALUE.name()), true);
    }

    // The field in encoded order, with any repeated key kept (the ledger keeps the last value of a repeated key).
    static List<RawRedeemer> read(CborSpan field) {
        List<RawRedeemer> redeemers = new ArrayList<>();
        if (field.majorType() == MAJOR_ARRAY) {
            for (CborSpan item : field.items())
                redeemers.add(ofItem(item, item.items(), false));
        } else {
            for (Map.Entry<CborSpan, CborSpan> entry : field.entries()) {
                CborSpan key = entry.getKey();
                CborSpan value = entry.getValue();
                redeemers.add(ofEntry(key, key.items(), value, value.items(), false));
            }
        }
        return redeemers;
    }

    // A tolerant reading lets a map value be indefinite.
    static List<RawRedeemer> readLenient(CborSpan field, boolean tolerant) {
        List<RawRedeemer> redeemers = new ArrayList<>();
        int major = field.majorType();
        if (major == MAJOR_ARRAY) {
            for (CborSpan item : RawItems.array(field, RECORD.name()))
                redeemers.add(lenient(item));
        } else if (major == MAJOR_MAP) {
            for (Map.Entry<CborSpan, CborSpan> entry : RawItems.distinctKeys(field, MAP.name()))
                redeemers.add(tolerant ? tolerant(entry.getKey(), entry.getValue())
                        : lenient(entry.getKey(), entry.getValue()));
        }
        return redeemers;
    }

    private static RawRedeemer ofItem(CborSpan item, List<CborSpan> items, boolean lenient) {
        PositionalRecord record = PositionalRecord.of(ITEM, item, items, true);
        return of(record.value(FieldType.REDEEMER_TAG), record.value(FieldType.UINT32),
                record.value(FieldType.PLUTUS_DATA), record.value(FieldType.EX_UNITS), null, item, lenient);
    }

    private static RawRedeemer ofEntry(CborSpan key, List<CborSpan> keyItems, CborSpan value, List<CborSpan> valueItems,
                                       boolean lenient) {
        PositionalRecord keyRecord = PositionalRecord.of(KEY, key, keyItems, true);
        PositionalRecord valueRecord = PositionalRecord.of(VALUE, value, valueItems, true);
        return of(keyRecord.value(FieldType.REDEEMER_TAG), keyRecord.value(FieldType.UINT32),
                valueRecord.value(FieldType.PLUTUS_DATA), valueRecord.value(FieldType.EX_UNITS), key, value, lenient);
    }

    private static RawRedeemer of(CborSpan tag, CborSpan index, CborSpan data, CborSpan exUnits, CborSpan key,
                                  CborSpan span, boolean lenient) {
        return lenient
                ? new RawRedeemer(RawItems.smallUnsigned(tag), RawItems.unsignedLong(index), data, exUnits, key, span)
                : new RawRedeemer(RawTx.smallInt(tag), index.asLong(), data, exUnits, key, span);
    }

    /**
     * @return the index, for a redeemer read as the models read one: its 64 bits, as an unsigned value
     */
    public BigInteger unsignedIndex() {
        return new BigInteger(Long.toUnsignedString(index));
    }

    /**
     * @return the execution units
     * @throws CborRuntimeException if they are not an array of two unsigned integers
     */
    public RawExUnits units() {
        return RawExUnits.of(exUnits);
    }
}
