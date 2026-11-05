package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.raw.RawDatum;
import com.bloxbean.cardano.client.transaction.raw.RawScript;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;

/**
 * Turns the raw views of {@code ledger-cbor} into this module's models. The views cannot do it, as no model is on their
 * classpath (ADR 0003 Q2).
 */
public final class RawModels {

    private RawModels() {
    }

    /**
     * Decodes the data. The model keeps one entry per map key, and re-encoding it may give other bytes; hash with
     * {@link RawDatum#hash()}.
     *
     * @param datum the datum as received
     * @return the data
     * @throws CborDeserializationException if the bytes are not Plutus data
     */
    public static PlutusData toPlutusData(RawDatum datum) throws CborDeserializationException {
        return PlutusData.deserialize(datum.span().bytes());
    }

    /**
     * Decodes the script. Re-encoding a native script model may give other bytes than were received; hash with
     * {@link RawScript#hash()}.
     *
     * @param script the script as received
     * @return a {@link NativeScript}, {@link PlutusV1Script}, {@link PlutusV2Script} or {@link PlutusV3Script}
     * @throws CborDeserializationException if the bytes are not a script of this type
     */
    public static Script toScript(RawScript script) throws CborDeserializationException {
        if (script.type() == 0) {
            if (script.span().majorType() != 4)
                throw new CborDeserializationException("Expected a native script at offset " + script.span().offset());
            return NativeScript.deserialize((Array) CborSerializationUtil.deserialize(script.span().bytes()));
        }
        if (script.span().majorType() != 2)
            throw new CborDeserializationException(
                    "Expected a Plutus script byte string at offset " + script.span().offset());
        ByteString bytes = (ByteString) CborSerializationUtil.deserialize(script.span().bytes());
        switch (script.type()) {
            case 1:
                return PlutusV1Script.deserialize(bytes);
            case 2:
                return PlutusV2Script.deserialize(bytes);
            default:
                return PlutusV3Script.deserialize(bytes);
        }
    }
}
