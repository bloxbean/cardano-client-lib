package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The cost models of a parameter update as received, {@code {* language => [* int64]}}. {@link #span()} is the field as
 * encoded, for a caller that hashes it. Language ids and the number of costs are not checked (ADR 0003 D5).
 * <p>
 * {@link #of(CborSpan)} keeps every entry in encoded order. {@link #lenient(CborSpan)} reads what CCL's model has
 * always read (D7): one entry per key, as a decoded map has it, and also an entry in the form of the language views of
 * the script integrity hash, a byte string holding the language id and a byte string holding the array of costs.
 */
public final class RawCostModels {
    /** The {@code cost_models} rule: a cost model per language. */
    static final RecordSpec RECORD = RecordSpec.entries("cost_models", FieldType.LANGUAGE,
            FieldSpec.of(0, FieldType.COST_MODEL, false, Era.Alonzo, null));
    private static final int MAJOR_BYTES = 2;

    /**
     * A cost model.
     *
     * @param language the language id
     * @param span     the array of costs, as encoded; in the language views form, the array in the byte string
     */
    public record Model(int language, CborSpan span) {
        /**
         * @return the costs, in encoded order
         * @throws CborRuntimeException if the cost model is not an array of integers that fit a long
         */
        public long[] costs() {
            List<CborSpan> items = RawItems.array(span, FieldType.COST_MODEL.name());
            long[] costs = new long[items.size()];
            for (int i = 0; i < costs.length; i++) {
                CborSpan item = items.get(i);
                if (RawItems.content(item).majorType() > 1)
                    throw RawTx.error("Invalid cost " + i + ": of major type " + item.majorType() + ", not an integer",
                            item);
                BigInteger cost = RawItems.number(item);
                if (cost.bitLength() > Long.SIZE - 1)
                    throw RawTx.error("Invalid cost " + i + ": an int64, found " + cost, item);
                costs[i] = cost.longValue();
            }
            return costs;
        }
    }

    private final CborSpan span;
    private final boolean lenient;

    private RawCostModels(CborSpan span, boolean lenient) {
        this.span = span;
        this.lenient = lenient;
    }

    /**
     * @param span cost models as encoded
     * @return the view; its items are read by {@link #models()}
     */
    public static RawCostModels of(CborSpan span) {
        return new RawCostModels(span, false);
    }

    /**
     * @param span cost models as encoded
     * @return the view, read as CCL's model reads it
     */
    public static RawCostModels lenient(CborSpan span) {
        return new RawCostModels(span, true);
    }

    /**
     * @return the cost models as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * @return the cost models, in encoded order; their costs are read by {@link Model#costs()}
     * @throws CborRuntimeException if the field is not a map, or a language id is not an unsigned integer that fits an
     *                              int
     */
    public List<Model> models() {
        List<Map.Entry<CborSpan, CborSpan>> entries = lenient ? RawItems.distinctKeys(span, RECORD.name())
                : RawItems.map(span, RECORD.name());
        List<Model> models = new ArrayList<>(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries) {
            CborSpan language = entry.getKey();
            CborSpan costs = entry.getValue();
            if (lenient && language.majorType() == MAJOR_BYTES) {
                language = embedded(language);
                costs = embedded(costs);
            }
            models.add(new Model(RawItems.smallUnsigned(language), costs));
        }
        return models;
    }

    // The first item in a byte string, the items after it well-formed, as a decoded tree reads one.
    private static CborSpan embedded(CborSpan item) {
        byte[] bytes = RawItems.bytes(item);
        CborSerializationUtil.deserialize(bytes);
        return CborSpan.at(bytes, 0);
    }
}
