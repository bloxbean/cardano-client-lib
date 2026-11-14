package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.util.List;

/**
 * A script as received: a witness or aux data script, or a reference script ({@code script_ref}).
 *
 * @param type 0 native, 1 Plutus V1, 2 Plutus V2, 3 Plutus V3, 4 Plutus V4 (the {@code script_ref} numbering; Plutus V4
 *             is experimental: Dijkstra is not final; this may change in any release)
 * @param span a native script, or the byte string of a Plutus script, as encoded
 */
public record RawScript(int type, CborSpan span) {
    static final int NATIVE_TYPE = 0;
    static final int PLUTUS_V1_TYPE = 1;
    static final int PLUTUS_V2_TYPE = 2;
    static final int PLUTUS_V3_TYPE = 3;
    static final int PLUTUS_V4_TYPE = 4;

    private static final int MAJOR_BYTES = 2;
    private static final int MAJOR_ARRAY = 4;
    /** An item of a list of native scripts. It is recursive, so an item is one value (ADR 0003 D6). */
    static final RecordSpec NATIVE = RecordSpec.byMajorType("native_script",
            FieldSpec.of(MAJOR_ARRAY, FieldType.NATIVE_SCRIPT, false, Era.Shelley, null));
    /** An item of a list of Plutus scripts. */
    static final RecordSpec PLUTUS = RecordSpec.byMajorType("plutus_script",
            FieldSpec.of(MAJOR_BYTES, FieldType.PLUTUS_SCRIPT, false, Era.Alonzo, null));

    public RawScript {
        if (type < NATIVE_TYPE || type > PLUTUS_V4_TYPE)
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
        byte[] script = type == NATIVE_TYPE ? span.bytes() : span.byteString();
        return Blake2bUtil.blake2bHash224(BytesUtil.merge(new byte[]{(byte) type}, script));
    }
}
