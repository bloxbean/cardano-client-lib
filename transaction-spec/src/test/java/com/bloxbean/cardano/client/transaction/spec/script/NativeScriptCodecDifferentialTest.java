package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The iterative native script codec against the recursive one it replaced, on every native script of the real
 * transactions and blocks (witness, aux data and reference scripts) and on seeded random scripts: the same models, the
 * same serialization, script hash and text.
 */
class NativeScriptCodecDifferentialTest {

    @Test
    void realScriptsDecodeAndEncodeAsBefore() throws Exception {
        int compared = 0;
        int canonical = 0;
        for (NativeScriptCorpus.Script script : NativeScriptCorpus.all()) {
            NativeScript legacy;
            try {
                legacy = LegacyNativeScript.deserialize((Array) CborDecoder.decode(script.cbor()).get(0));
            } catch (StackOverflowError e) {
                continue; // the deeply nested trigger: covered by the depth tests
            }
            assertSame(script.toString(), script.cbor(), legacy);
            // on chain scripts are mostly canonical, and then the model hashes like the original bytes
            if (Arrays.equals(NativeScript.deserialize((Array) CborSerializationUtil.deserialize(script.cbor())).serializeScriptBody(), script.cbor()))
                canonical++;
            compared++;
        }
        assertThat(compared).isGreaterThan(20);
        assertThat(canonical).isPositive();
        System.out.println("native scripts compared: " + compared + ", canonical: " + canonical);
    }

    @Test
    void randomScriptsDecodeAndEncodeAsBefore() throws Exception {
        RandomNativeScript generator = new RandomNativeScript(681_686);
        for (int i = 0; i < 20_000; i++) {
            byte[] cbor = generator.next(5);
            assertSame(HexUtil.encodeHexString(cbor), cbor, LegacyNativeScript.deserialize((Array) CborDecoder.decode(cbor).get(0)));
        }
    }

    private static void assertSame(String what, byte[] cbor, NativeScript legacy) throws Exception {
        NativeScript decoded = NativeScript.deserialize((Array) CborSerializationUtil.deserialize(cbor));
        assertThat(LegacyNativeScript.sameModel(decoded, legacy)).as(what).isTrue();
        assertThat(decoded).as(what).isEqualTo(legacy);
        assertThat(decoded.hashCode()).as(what).isEqualTo(legacy.hashCode());
        assertThat(decoded.toString()).as(what).isEqualTo(LegacyNativeScript.render(legacy));

        byte[] legacyBytes = CborSerializationUtil.serialize(LegacyNativeScript.serialize(legacy));
        assertThat(decoded.serializeScriptBody()).as(what).isEqualTo(legacyBytes);
        assertThat(decoded.getScriptHash()).as(what)
                .isEqualTo(Blake2bUtil.blake2bHash224(BytesUtil.merge(new byte[]{0}, legacyBytes)));
        assertThat(NativeScript.deserializeScriptRef(decoded.scriptRefBytes())).as(what).isEqualTo(decoded);
    }
}
