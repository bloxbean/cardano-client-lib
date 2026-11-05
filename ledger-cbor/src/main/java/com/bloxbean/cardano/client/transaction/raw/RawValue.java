package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.transaction.raw.RawTx.error;

/**
 * A value as received, {@code coin / [coin, multiasset]}. Accessors read an item when they are called. Tags on an item
 * are looked through. Ranges, empty maps and asset name lengths are not checked (ADR 0003 D5).
 * <p>
 * {@link #of(CborSpan)} reads the format as D5 sets it. {@link #lenient(CborSpan)} reads what this module's models have
 * always accepted, which D7 keeps until a separate decision changes it.
 */
public final class RawValue {
    private static final int MAJOR_UNSIGNED = 0;
    private static final int MAJOR_NEGATIVE = 1;
    private static final int MAJOR_BYTES = 2;
    private static final int MAJOR_ARRAY = 4;

    /** The array form, {@code [coin, multiasset]}. */
    static final RecordSpec COIN_AND_ASSETS = RecordSpec.positional("value", List.of(
            FieldSpec.of(0, FieldType.COIN, true, Era.Mary, null),
            FieldSpec.of(1, FieldType.MAP, true, Era.Mary, null, RawMultiAsset.VALUE)));
    /** The {@code value} rule: a coin, of which a reader accepts any integer or bignum (D5), or the array form. */
    static final RecordSpec RECORD = RecordSpec.byMajorType("value",
            FieldSpec.of(MAJOR_UNSIGNED, FieldType.COIN, false, Era.Shelley, null),
            FieldSpec.of(MAJOR_NEGATIVE, FieldType.COIN, false, Era.Shelley, null),
            FieldSpec.of(MAJOR_BYTES, FieldType.COIN, false, Era.Shelley, null),
            FieldSpec.of(MAJOR_ARRAY, FieldType.VALUE, false, Era.Mary, null, COIN_AND_ASSETS));

    private final CborSpan span;
    private final CborSpan coin;
    private final CborSpan assets;
    private final boolean lenient;

    private RawValue(CborSpan span, CborSpan coin, CborSpan assets, boolean lenient) {
        this.span = span;
        this.coin = coin;
        this.assets = assets;
        this.lenient = lenient;
    }

    /**
     * @param span a value as encoded
     * @return the view
     * @throws CborRuntimeException if it is no integer or bignum, or array of two items
     */
    public static RawValue of(CborSpan span) {
        if (RawItems.content(span).majorType() == MAJOR_ARRAY)
            return ofArray(span, false);
        RawItems.integer(span, false);
        return new RawValue(span, span, null, false);
    }

    /**
     * Reads a value as the models always have: an array of another number of items is a value of no coin and no assets,
     * and an item at the position of a number that holds something else reads as null. An indefinite array is rejected,
     * which the ledger does with {@code decodeListLenOf}.
     *
     * @param span a value as encoded
     * @return the view, or empty for an item that holds no value: not a number or an array, or a tagged byte string
     * that is no bignum
     * @throws RuntimeException if it is a byte string without a tag, or an indefinite array
     */
    public static Optional<RawValue> lenient(CborSpan span) {
        CborSpan content = RawItems.content(span);
        int major = content.majorType();
        if (major == MAJOR_ARRAY) {
            if (content.isIndefinite())
                throw new CborRuntimeException("Invalid value: expected a definite-length array");
            return Optional.of(content.size() == COIN_AND_ASSETS.arity() ? ofArray(span, true)
                    : new RawValue(span, null, null, true));
        }
        if (major <= MAJOR_NEGATIVE || (major == MAJOR_BYTES && RawItems.integer(span, true) != null))
            return Optional.of(new RawValue(span, span, null, true));
        return Optional.empty();
    }

    /**
     * Reads a value as {@link #lenient(CborSpan)} does, except for an array: any array of at least two items is a coin
     * and assets, definite or not, and what follows them is ignored.
     *
     * @param span a value as encoded
     * @return the view, or empty for an item that holds no value
     * @throws RuntimeException if it is a byte string without a tag, or an array of fewer than two items
     */
    public static Optional<RawValue> tolerant(CborSpan span) {
        if (RawItems.content(span).majorType() != MAJOR_ARRAY)
            return lenient(span);
        PositionalRecord record = PositionalRecord.of(COIN_AND_ASSETS, span, false);
        return Optional.of(new RawValue(span, record.value(FieldType.COIN), record.value(FieldType.MAP), true));
    }

    private static RawValue ofArray(CborSpan span, boolean lenient) {
        PositionalRecord record = PositionalRecord.of(COIN_AND_ASSETS, span, true);
        return new RawValue(span, record.value(FieldType.COIN), record.value(FieldType.MAP), lenient);
    }

    /**
     * @return the value as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * @return the coin; for a view read as the models read one, null if its item is no number
     */
    public BigInteger coin() {
        return coin != null ? RawItems.integer(coin, lenient) : BigInteger.ZERO;
    }

    /**
     * @return the assets, none for a value of coin alone
     * @throws CborRuntimeException if they are not a map of byte strings to maps with byte string keys
     */
    public List<RawMultiAsset> multiAssets() {
        return assets != null ? RawMultiAsset.read(assets, lenient) : List.of();
    }
}
