package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeScriptCodecTest {
    private static final String KEY_HASH = "abababababababababababababababababababababababababababab";
    private static final String SIG = "8200581c" + KEY_HASH;

    private static NativeScript decode(String hex) throws CborDeserializationException {
        return NativeScript.deserialize((Array) CborSerializationUtil.deserialize(decodeHexString(hex)));
    }

    private static String encode(NativeScript script) throws Exception {
        return encodeHexString(script.serializeScriptBody());
    }

    @Test
    void everyType() throws Exception {
        assertThat(decode(SIG)).isEqualTo(new ScriptPubkey(KEY_HASH));
        ScriptAll all = (ScriptAll) decode("8201" + "82" + SIG + "820419012c");
        assertThat(all.getScripts()).containsExactly(new ScriptPubkey(KEY_HASH), new RequireTimeAfter(300));
        ScriptAny any = (ScriptAny) decode("8202" + "81" + "820519012c");
        assertThat(any.getScripts()).containsExactly(new RequireTimeBefore(300));
        ScriptAtLeast atLeast = (ScriptAtLeast) decode("830302" + "80");
        assertThat(atLeast.getRequired()).isEqualTo(2);
        for (String hex : new String[]{SIG, "8201" + "82" + SIG + "820419012c", "8202" + "81" + "820519012c", "83030280"})
            assertThat(encode(decode(hex))).isEqualTo(hex);
    }

    @Test
    void mIsSigned() throws Exception {
        // TimelockMOf !Int: m may be negative, and round trips as a negative integer
        ScriptAtLeast atLeast = (ScriptAtLeast) decode("830320" + "80");
        assertThat(atLeast.getRequired()).isEqualTo(-1);
        assertThat(encode(atLeast)).isEqualTo("83032080");
        assertThat(((ScriptAtLeast) decode("83033b7fffffffffffffff80")).getRequired()).isEqualTo(Long.MIN_VALUE);
        assertThat(((ScriptAtLeast) decode("83031b7fffffffffffffff80")).getRequired()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void slotsAreUnsigned64Bit() throws Exception {
        // the recursive code read the slot as a long, so a slot from 2^63 came out negative
        BigInteger slot = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        RequireTimeAfter after = (RequireTimeAfter) decode("82041bffffffffffffffff");
        assertThat(after.getSlot()).isEqualTo(slot);
        assertThat(encode(after)).isEqualTo("82041bffffffffffffffff");
    }

    @Test
    void indefiniteArraysAndNonMinimalHeadsDecode() throws Exception {
        NativeScript script = decode("9f01" + "9f" + SIG + "9f04190064ff" + "ff" + "ff");
        ScriptAll expected = new ScriptAll().addScript(new ScriptPubkey(KEY_HASH)).addScript(new RequireTimeAfter(100));
        assertThat(script).isEqualTo(expected);
        assertThat(decode("82180181" + SIG)).isEqualTo(new ScriptAll().addScript(new ScriptPubkey(KEY_HASH)));
        // the model re-encodes in the canonical form
        assertThat(encode(script)).isEqualTo("8201" + "82" + SIG + "82041864");
    }

    /**
     * What the ledger's Timelock decoder rejects: an unknown type (the recursive code returned null, and ScriptAll
     * dropped it), the wrong number of fields, a field of the wrong type, a key hash that is not 28 bytes, m outside a
     * signed 64-bit integer, a negative slot, a tag.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "820600", "8218ff00", "822080", "8241008180", "80",
            "821b000000010000000180",           // type 2^32 + 1: the recursive code read it as 1
            "8201" + "81" + "820600",           // unknown type inside all: was dropped
            "8202" + "81" + "8218ff00",         // ... and inside any
            "83028080", "830180" + "80", "8300581c" + KEY_HASH + "00", "8104", "83040101", "820301", // number of fields
            "820101", "8202a0", "83030101", "820001",                                      // field types
            "8200581b" + "ababababababababababababababababababababababababababab", "8200581d" + "ababababababababababababababababababababababababababababab", "820040",                               // key hash size
            "83031bffffffffffffffff80", "83033bffffffffffffffff80", "8303c2410180",       // m
            "820420", "8204c24101", "8205fb4059000000000000",                             // slot
            "c1" + SIG, "8201c180", "82c10180", "8204c11864",                              // tags
    })
    void malformedScriptsAreRejected(String hex) {
        assertThatThrownBy(() -> decode(hex)).as(hex).isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void typedDeserializeChecksTheType() throws Exception {
        Array sig = (Array) CborSerializationUtil.deserialize(decodeHexString(SIG));
        assertThat(ScriptPubkey.deserialize(sig).getKeyHash()).isEqualTo(KEY_HASH);
        assertThatThrownBy(() -> ScriptAll.deserialize(sig)).isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void scriptRefRoundTrip() throws Exception {
        ScriptAll script = new ScriptAll().addScript(new ScriptPubkey(KEY_HASH));
        assertThat(NativeScript.deserializeScriptRef(script.scriptRefBytes())).isEqualTo(script);
        assertThatThrownBy(() -> NativeScript.deserializeScriptRef(decodeHexString("8200" + "820600")))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void equalsHashCodeAndToStringAsGenerated() throws Exception {
        NativeScript a = decode("830302" + "83" + SIG + "820419012c" + "8202" + "80");
        NativeScript b = decode("830302" + "83" + SIG + "820419012c" + "8202" + "80");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(decode("830301" + "83" + SIG + "820419012c" + "8202" + "80"));
        assertThat(a).isNotEqualTo(decode("830302" + "83" + SIG + "820419012c" + "8201" + "80"));
        assertThat(a).isNotEqualTo(decode("830302" + "82" + SIG + "820419012c"));
        assertThat(a.equals(null)).isFalse();
        assertThat(a.toString()).isEqualTo("ScriptAtLeast(type=atLeast, required=2, scripts=[ScriptPubkey(type=sig, keyHash="
                + KEY_HASH + "), RequireTimeAfter(type=after, slot=300), ScriptAny(type=any, scripts=[])])");
        assertThat(a.toString()).isEqualTo(LegacyNativeScript.render(a));
        ScriptAll withNull = new ScriptAll();
        withNull.getScripts().add(null);
        assertThat(withNull.toString()).isEqualTo("ScriptAll(type=all, scripts=[null])");
        assertThat(new ScriptAtLeast().toString()).isEqualTo("ScriptAtLeast(type=atLeast, required=null, scripts=[])");
    }
}
