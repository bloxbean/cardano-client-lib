package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.util.List;

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
        List<CborSpan> items = RawTx.pair(scriptRef, "A script reference");
        return new RawScript(RawTx.smallInt(items.get(0)), items.get(1));
    }

    /**
     * @return the script hash: {@code blake2b224} of the type byte and the native script as encoded, or of the type byte
     * and the Plutus script's bytes
     */
    public byte[] hash() {
        byte[] script = type == 0 ? span.bytes() : span.byteString();
        return Blake2bUtil.blake2bHash224(BytesUtil.merge(new byte[]{(byte) type}, script));
    }
}
