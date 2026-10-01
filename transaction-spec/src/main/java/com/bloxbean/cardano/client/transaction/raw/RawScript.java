package com.bloxbean.cardano.client.transaction.raw;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.spec.PlutusV1Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;

/**
 * A script as received: a witness or aux data script, or a reference script ({@code script_ref}).
 *
 * @param type 0 native, 1 Plutus V1, 2 Plutus V2, 3 Plutus V3 (the {@code script_ref} numbering)
 * @param span a native script, or the byte string of a Plutus script, as encoded
 */
public record RawScript(int type, CborSpan span) {

    public RawScript {
        if (type < 0 || type > 3)
            throw new CborRuntimeException("Unknown script type " + type + " at offset " + span.offset());
    }

    /**
     * @param scriptRef the payload of a {@code script_ref}, {@code [type, script]}
     * @return the script
     */
    static RawScript ofScriptRef(CborSpan scriptRef) {
        return new RawScript(RawTx.smallInt(scriptRef.get(0)), scriptRef.get(1));
    }

    /**
     * @return the script hash: {@code blake2b224} of the type byte and the native script as encoded, or of the type byte
     * and the Plutus script's bytes
     */
    public byte[] hash() {
        byte[] script = type == 0 ? span.bytes() : span.byteString();
        return Blake2bUtil.blake2bHash224(BytesUtil.merge(new byte[]{(byte) type}, script));
    }

    /**
     * Decodes the script. Re-encoding a native script model may give other bytes than were received; hash with
     * {@link #hash()}.
     *
     * @return a {@link NativeScript}, {@link PlutusV1Script}, {@link PlutusV2Script} or {@link PlutusV3Script}
     * @throws CborDeserializationException if the bytes are not a script of this type
     */
    public Script toScript() throws CborDeserializationException {
        if (type == 0) {
            if (span.majorType() != 4)
                throw new CborDeserializationException("Expected a native script at offset " + span.offset());
            return NativeScript.deserialize((Array) CborSerializationUtil.deserialize(span.bytes()));
        }
        if (span.majorType() != 2)
            throw new CborDeserializationException("Expected a Plutus script byte string at offset " + span.offset());
        ByteString bytes = (ByteString) CborSerializationUtil.deserialize(span.bytes());
        switch (type) {
            case 1:
                return PlutusV1Script.deserialize(bytes);
            case 2:
                return PlutusV2Script.deserialize(bytes);
            default:
                return PlutusV3Script.deserialize(bytes);
        }
    }
}
