package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A transaction output as received, in either form: the legacy array {@code [address, value, ? datum_hash]} or the map
 * {@code {0: address, 1: value, ? 2: datum_option, ? 3: script_ref}}.
 *
 * @param span        the output, as encoded
 * @param address     the address bytes item
 * @param value       the value: a coin, or {@code [coin, multiasset]}, as encoded
 * @param datumHash   the datum hash, from a legacy output or a datum option {@code [0, hash]}
 * @param inlineDatum the inline datum, the payload of a datum option {@code [1, #6.24(bytes)]}
 * @param scriptRef   the reference script, the payload of {@code #6.24(bytes .cbor [type, script])}
 */
public record RawOutput(CborSpan span, CborSpan address, CborSpan value, Optional<byte[]> datumHash,
                        Optional<RawDatum> inlineDatum, Optional<RawScript> scriptRef) {

    static RawOutput of(CborSpan output) {
        if (output.majorType() == 4) {
            List<CborSpan> items = output.items();
            if (items.size() < 2 || items.size() > 3)
                throw new CborRuntimeException("A legacy output has 2 or 3 items, found " + items.size() + " at offset " + output.offset());
            Optional<byte[]> datumHash = items.size() == 3 ? Optional.of(items.get(2).byteString()) : Optional.empty();
            return new RawOutput(output, items.get(0), items.get(1), datumHash, Optional.empty(), Optional.empty());
        }
        List<Map.Entry<CborSpan, CborSpan>> record = RawTx.record(output, "output");
        CborSpan address = required(record, output, 0);
        CborSpan value = required(record, output, 1);
        Optional<byte[]> datumHash = Optional.empty();
        Optional<RawDatum> inlineDatum = Optional.empty();
        Optional<CborSpan> datumOption = RawTx.field(record, 2);
        if (datumOption.isPresent()) {
            CborSpan option = datumOption.get();
            List<CborSpan> items = RawTx.pair(option, "A datum option");
            long kind = items.get(0).asLong();
            if (kind == 0)
                datumHash = Optional.of(items.get(1).byteString());
            else if (kind == 1)
                inlineDatum = Optional.of(new RawDatum(items.get(1).embedded()));
            else
                throw new CborRuntimeException("Unknown datum option " + kind + " at offset " + option.offset());
        }
        Optional<RawScript> scriptRef = RawTx.field(record, 3).map(ref -> RawScript.ofScriptRef(ref.embedded()));
        return new RawOutput(output, address, value, datumHash, inlineDatum, scriptRef);
    }

    private static CborSpan required(List<Map.Entry<CborSpan, CborSpan>> record, CborSpan output, int key) {
        return RawTx.field(record, key).orElseThrow(() ->
                new CborRuntimeException("Output without key " + key + " at offset " + output.offset()));
    }
}
