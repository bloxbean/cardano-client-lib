package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScriptEvaluator;
import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;

/**
 * Every position of unbounded nesting at the maximum transaction size (ADR 0001 section 6.7), read through the raw
 * views: the real preprod trigger transaction and its block, and synthetic witness, reference and aux data native
 * scripts, witness list and constructor datums, an inline datum, redeemer data in both forms and metadata. Each is hashed
 * from its original bytes, decoded, and hashed again from the model (equal, as the fixtures are canonical); native
 * scripts are evaluated. {@link #main(String[])} runs them all, for a forked JVM.
 */
final class RawViewDepthFixtures {
    private static final int MAX_TX_SIZE = 16_384;
    private static final String KEY_HASH = "00".repeat(28);
    private static final String SIG = "8200581c" + KEY_HASH;
    private static final String ADDRESS = "581d60" + KEY_HASH;
    private static final String BODY = "a3008001800200"; // {0: [], 1: [], 2: 0}

    private RawViewDepthFixtures() {
    }

    static void runAll() throws Exception {
        trigger();

        String witnessScript = "820181".repeat(5_410) + SIG;
        RawTx tx = tx(BODY, "a10181" + witnessScript, "f6");
        nativeScript("witness native script (5,410 levels)", tx.scripts().get(0), witnessScript);

        String referenceScript = "820181".repeat(5_400) + SIG;
        String scriptRef = "8200" + referenceScript;
        String output = "a3" + "00" + ADDRESS + "0100" + "03d818" + bytesHead(scriptRef) + scriptRef;
        tx = tx("a3" + "0080" + "0181" + output + "0200", "a0", "f6");
        nativeScript("reference native script (5,400 levels)", tx.outputs().get(0).scriptRef().orElseThrow(), referenceScript);

        String auxScript = "820181".repeat(5_400) + SIG;
        String aux = "d90103a1" + "0181" + auxScript;
        tx = tx(BODY, "a0", aux);
        nativeScript("aux data native script (5,400 levels)", tx.auxScripts().get(0), auxScript);
        check(Arrays.equals(tx.auxDataHash().orElseThrow(), hash256(aux)), "aux data native script: aux data hash");

        String listDatum = "81".repeat(16_250) + "00";
        datum("witness list datum (16,250 levels)", tx(BODY, "a10481" + listDatum, "f6").witnessDatums().get(0), listDatum);
        String constrDatum = "d87981".repeat(5_420) + "00";
        datum("witness constr datum (5,420 levels)", tx(BODY, "a10481" + constrDatum, "f6").witnessDatums().get(0), constrDatum);

        String inlineDatum = "81".repeat(16_230) + "00";
        output = "a3" + "00" + ADDRESS + "0100" + "02" + "8201" + "d818" + bytesHead(inlineDatum) + inlineDatum;
        tx = tx("a3" + "0080" + "0181" + output + "0200", "a0", "f6");
        datum("inline datum (16,230 levels)", tx.outputs().get(0).inlineDatum().orElseThrow(), inlineDatum);

        String redeemerData = "81".repeat(16_240) + "00";
        tx = tx(BODY, "a105818400" + "00" + redeemerData + "820000", "f6");
        datum("redeemer data, array form (16,240 levels)", new RawDatum(tx.redeemers().get(0).data()), redeemerData);
        tx = tx(BODY, "a105a1820000" + "82" + redeemerData + "820000", "f6");
        datum("redeemer data, map form (16,240 levels)", new RawDatum(tx.redeemers().get(0).data()), redeemerData);

        String metadata = "a100" + "81".repeat(16_250) + "00";
        tx = tx(BODY, "a0", metadata);
        check(Arrays.equals(tx.auxDataHash().orElseThrow(), hash256(metadata)), "metadata (16,250 levels): aux data hash");
    }

    // The real trigger: its TxId, its script's hash as on chain, evaluation as the ledger decided, and its block's body hash.
    private static void trigger() throws Exception {
        RealCborFixturesAccess.Tx trigger = RealCborFixturesAccess.trigger();
        RawTx tx = RawTx.of(trigger.cbor());
        check(encodeHexString(tx.txId()).equals(trigger.txHash()), "trigger: TxId");
        RawScript script = tx.scripts().get(0);
        check(encodeHexString(script.hash()).equals(RealCborFixturesAccess.TRIGGER_NATIVE_SCRIPT_HASH), "trigger: script hash");
        NativeScript model = (NativeScript) script.toScript();
        check(Arrays.equals(model.getScriptHash(), script.hash()), "trigger: model script hash");
        Set<String> vkeyHashes = new HashSet<>();
        tx.vkeyWitnesses().forEach(witness -> vkeyHashes.add(encodeHexString(Blake2bUtil.blake2bHash224(witness.get(0).byteString()))));
        check(NativeScriptEvaluator.evaluate(model, vkeyHashes, tx.bodyField(8).map(CborSpan::asLong).orElse(null),
                tx.bodyField(3).map(CborSpan::asLong).orElse(null)), "trigger: evaluates as on chain");

        RealCborFixturesAccess.Block block = RealCborFixturesAccess.blocks().stream()
                .filter(b -> b.txHashes().contains(trigger.txHash())).findFirst().orElseThrow();
        RawBlock raw = RawBlock.of(block.cbor());
        check(Arrays.equals(raw.bodyHash(), raw.header().get(0).get(7).byteString()), "trigger block: body hash");
        boolean found = false;
        for (int i = 0; i < raw.txCount(); i++) {
            if (encodeHexString(raw.tx(i).txId()).equals(trigger.txHash())) {
                check(Arrays.equals(raw.tx(i).scripts().get(0).hash(), script.hash()), "trigger block: script hash");
                check(Arrays.equals(raw.txBytes(i), trigger.cbor()), "trigger block: tx bytes");
                found = true;
            }
        }
        check(found, "trigger block: contains the trigger");
    }

    private static void nativeScript(String name, RawScript script, String hex) throws Exception {
        byte[] original = decodeHexString(hex);
        check(script.type() == 0 && Arrays.equals(script.span().bytes(), original), name + ": span");
        byte[] expected = Blake2bUtil.blake2bHash224(decodeHexString("00" + hex));
        check(Arrays.equals(script.hash(), expected), name + ": script hash");
        NativeScript model = (NativeScript) script.toScript();
        check(Arrays.equals(model.getScriptHash(), expected), name + ": model script hash");
        check(NativeScriptEvaluator.evaluate(model, Set.of(KEY_HASH), null, null), name + ": holds with the signature");
        check(!NativeScriptEvaluator.evaluate(model, Set.of(), null, null), name + ": fails without it");
    }

    private static void datum(String name, RawDatum datum, String hex) throws Exception {
        check(Arrays.equals(datum.span().bytes(), decodeHexString(hex)), name + ": span");
        check(Arrays.equals(datum.hash(), hash256(hex)), name + ": datum hash");
        PlutusData model = datum.toPlutusData();
        check(Arrays.equals(model.getDatumHashAsBytes(), datum.hash()), name + ": model datum hash");
    }

    private static RawTx tx(String body, String witnesses, String aux) {
        byte[] tx = decodeHexString("84" + body + witnesses + "f5" + aux);
        check(tx.length <= MAX_TX_SIZE, "larger than the maximum transaction size: " + tx.length);
        return RawTx.of(tx);
    }

    private static byte[] hash256(String hex) {
        return Blake2bUtil.blake2bHash256(decodeHexString(hex));
    }

    private static String bytesHead(String hex) {
        return String.format("59%04x", hex.length() / 2);
    }

    private static void check(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("depth fixture check failed: " + what);
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("ok: raw view depth fixtures");
    }
}
