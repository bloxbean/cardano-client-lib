package com.bloxbean.cardano.vds.mpf;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;

/** Encodes a proof in the JS {@code toJSON()} shape into the MPF wire CBOR (definite lengths, as CCL writes). */
final class LegacyProofs {

    private LegacyProofs() {
    }

    static byte[] toWire(JsonNode steps) {
        Array list = new Array();
        for (JsonNode s : steps) {
            Array step = new Array();
            step.add(new UnsignedInteger(s.get("skip").asInt()));
            switch (s.get("type").asText()) {
                case "branch":
                    step.add(new ByteString(Bytes.fromHex(s.get("neighbors").asText())));
                    step.setTag(new Tag(121));
                    break;
                case "fork": {
                    JsonNode n = s.get("neighbor");
                    Array neighbor = new Array();
                    neighbor.add(new UnsignedInteger(n.get("nibble").asInt()));
                    neighbor.add(new ByteString(Bytes.fromHex(n.get("prefix").asText())));
                    neighbor.add(new ByteString(Bytes.fromHex(n.get("root").asText())));
                    neighbor.setTag(new Tag(121));
                    step.add(neighbor);
                    step.setTag(new Tag(122));
                    break;
                }
                case "leaf": {
                    JsonNode n = s.get("neighbor");
                    step.add(new ByteString(Bytes.fromHex(n.get("key").asText())));
                    step.add(new ByteString(Bytes.fromHex(n.get("value").asText())));
                    step.setTag(new Tag(123));
                    break;
                }
                default:
                    throw new IllegalArgumentException("unknown step " + s);
            }
            list.add(step);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new CborEncoder(out).encode(list);
        } catch (CborException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
