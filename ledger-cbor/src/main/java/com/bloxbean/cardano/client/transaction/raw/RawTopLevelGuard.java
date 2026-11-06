package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An entry of a Dijkstra transaction body's {@code required_top_level_guards},
 * {@code credential => plutus_data / nil}: a guard that the top-level transaction must have, with the data given to it.
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 *
 * @param credential the guard's credential
 * @param data       the data, or null
 */
public record RawTopLevelGuard(RawCredential credential, RawDatum data) {
    /** The {@code required_top_level_guards} rule. */
    static final RecordSpec MAP = RecordSpec.entries("required_top_level_guards", FieldType.CREDENTIAL,
            FieldSpec.of(0, FieldType.OPTIONAL_PLUTUS_DATA, false, Era.Dijkstra, null));

    /**
     * Every entry in encoded order.
     *
     * @throws CborRuntimeException if it is not a map, or a key is not a credential
     */
    static List<RawTopLevelGuard> read(CborSpan map) {
        List<Map.Entry<CborSpan, CborSpan>> entries = RawItems.map(map, MAP.name());
        List<RawTopLevelGuard> guards = new ArrayList<>(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries)
            guards.add(new RawTopLevelGuard(RawCredential.of(entry.getKey()),
                    RawItems.nullable(entry.getValue(), RawDatum::new)));
        return guards;
    }
}
