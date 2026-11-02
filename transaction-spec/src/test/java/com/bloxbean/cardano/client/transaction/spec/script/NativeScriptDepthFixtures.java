package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;

/**
 * Native scripts nested as deep as a transaction allows (ADR 0001 section 6.7): the real preprod trigger, and synthetic
 * scripts as a witness, a reference script and aux data in maximum-size transactions, plus deep atLeast and indefinite
 * chains. Each decodes (through {@link Transaction#deserialize(byte[])} where it sits in a transaction), serializes back,
 * hashes like its original bytes, evaluates, and equals, hashes and prints like a second decode.
 * {@link #main(String[])} runs them all, for a forked JVM.
 */
final class NativeScriptDepthFixtures {
    private static final int MAX_TX_SIZE = 16_384;
    private static final String KEY_HASH = "00".repeat(28);
    private static final String SIG = "8200581c" + KEY_HASH;
    private static final String BODY = "a3008001800200"; // {0: [], 1: [], 2: 0}

    private NativeScriptDepthFixtures() {
    }

    static void runAll() throws Exception {
        trigger();
        String witness = "820181".repeat(5_410) + SIG;
        fixture("witness native script (5,410 levels)", tx(BODY, "a10181" + witness, "f6"),
                tx -> tx.getWitnessSet().getNativeScripts().get(0), witness, 5_410);
        // [0, script] in tag 24, in the script_ref of an output {0: enterprise address, 1: 0, 3: 24(bytes)}
        String reference = "820181".repeat(5_400) + SIG;
        String scriptRef = "8200" + reference;
        String output = "a3" + "00581d60" + KEY_HASH + "0100" + "03d818" + bytesHead(scriptRef.length() / 2) + scriptRef;
        fixture("reference native script (5,400 levels)", tx("a3008001" + "81" + output + "0200", "a0", "f6"),
                tx -> NativeScript.deserializeScriptRef(tx.getBody().getOutputs().get(0).getScriptRef()), reference, 5_400);
        String aux = "820181".repeat(5_400) + SIG;
        fixture("aux-data native script (5,400 levels)", tx(BODY, "a0", "d90103a10181" + aux),
                tx -> tx.getAuxiliaryData().getNativeScripts().get(0), aux, 5_400);
        String atLeast = "83030181".repeat(4_000) + SIG;
        script("atLeast chain (4,000 levels)", atLeast, true);
        String indefinite = "9f019f".repeat(3_000) + SIG + "ffff".repeat(3_000);
        script("indefinite all chain (3,000 levels)", indefinite, false);
    }

    private interface ScriptOf {
        NativeScript of(Transaction tx) throws Exception;
    }

    // The real trigger: the script hashes to the hash on chain, and holds for the transaction's witnesses and validity
    // interval, as the ledger decided.
    private static void trigger() throws Exception {
        byte[] cbor = RealCborFixturesAccess.trigger().cbor();
        CborSpan tx = CborSpan.of(cbor);
        CborSpan scriptSpan = tx.get(1).field(1).orElseThrow().untagIf(258).get(0);
        NativeScript script = Transaction.deserialize(cbor).getWitnessSet().getNativeScripts().get(0);
        String name = "preprod trigger";
        check(encodeHexString(script.getScriptHash()).equals(RealCborFixturesAccess.TRIGGER_NATIVE_SCRIPT_HASH), name + ": script hash");
        check(encodeHexString(Blake2bUtil.blake2bHash224(BytesUtil.merge(new byte[]{0}, scriptSpan.bytes())))
                .equals(RealCborFixturesAccess.TRIGGER_NATIVE_SCRIPT_HASH), name + ": hash of the original bytes");
        check(Arrays.equals(script.serializeScriptBody(), scriptSpan.bytes()), name + ": round trip");
        check(levels(script) >= RealCborFixturesAccess.TRIGGER_NATIVE_SCRIPT_LEVELS, name + ": depth");
        Set<String> vkeyHashes = new HashSet<>();
        tx.get(1).field(0).orElseThrow().untagIf(258).items().forEach(witness ->
                vkeyHashes.add(encodeHexString(Blake2bUtil.blake2bHash224(witness.get(0).byteString()))));
        CborSpan body = tx.get(0);
        check(NativeScriptEvaluator.evaluate(script, vkeyHashes, body.field(8).map(CborSpan::asLong).orElse(null),
                body.field(3).map(CborSpan::asLong).orElse(null)), name + ": evaluates as on chain");
        compareWithSecondDecode(name, script, scriptSpan.bytes());
    }

    private static void fixture(String name, byte[] txBytes, ScriptOf scriptOf, String scriptHex, int levels) throws Exception {
        check(txBytes.length <= MAX_TX_SIZE, name + ": larger than the maximum transaction size");
        NativeScript script = scriptOf.of(Transaction.deserialize(txBytes));
        byte[] original = decodeHexString(scriptHex);
        check(Arrays.equals(script.serializeScriptBody(), original), name + ": round trip");
        check(Arrays.equals(script.getScriptHash(), Blake2bUtil.blake2bHash224(BytesUtil.merge(new byte[]{0}, original))), name + ": script hash");
        check(levels(script) == levels, name + ": depth");
        check(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH), null, null), name + ": holds with the signature");
        check(!NativeScriptEvaluator.evaluate(script, Set.of(), null, null), name + ": fails without it");
        compareWithSecondDecode(name, script, original);
    }

    private static void script(String name, String hex, boolean canonical) throws Exception {
        byte[] original = decodeHexString(hex);
        NativeScript script = NativeScript.deserialize((Array) CborSerializationUtil.deserialize(original));
        byte[] encoded = script.serializeScriptBody();
        check(Arrays.equals(encoded, original) == canonical, name + ": round trip");
        check(Arrays.equals(script.getScriptHash(), Blake2bUtil.blake2bHash224(BytesUtil.merge(new byte[]{0}, encoded))), name + ": script hash");
        check(NativeScriptEvaluator.evaluate(script, Set.of(KEY_HASH), null, null), name + ": holds with the signature");
        check(!NativeScriptEvaluator.evaluate(script, Set.of(), null, null), name + ": fails without it");
        compareWithSecondDecode(name, script, encoded);
    }

    private static void compareWithSecondDecode(String name, NativeScript script, byte[] bytes) throws Exception {
        NativeScript again = NativeScript.deserialize((Array) CborSerializationUtil.deserialize(bytes));
        check(script.equals(again) && again.equals(script), name + ": equals");
        check(script.hashCode() == again.hashCode(), name + ": hashCode");
        check(script.toString().equals(again.toString()) && script.toString().length() > bytes.length, name + ": toString");
        check(NativeScript.deserializeScriptRef(script.scriptRefBytes()).equals(script), name + ": script ref round trip");
    }

    private static int levels(NativeScript script) {
        int levels = 0;
        List<NativeScript> children = NativeScriptCodec.scripts(script);
        while (children != null && !children.isEmpty()) {
            levels++;
            children = NativeScriptCodec.scripts(children.get(0));
        }
        return levels;
    }

    private static byte[] tx(String bodyHex, String witnessesHex, String auxHex) {
        return decodeHexString("84" + bodyHex + witnessesHex + "f5" + auxHex);
    }

    private static String bytesHead(int length) {
        return length < 0x100 ? String.format("58%02x", length) : String.format("59%04x", length);
    }

    private static void check(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("depth fixture check failed: " + what);
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("ok: native script depth fixtures");
    }
}
