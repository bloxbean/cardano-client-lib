package com.bloxbean.cardano.client.transaction.raw;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.spec.AuxiliaryData;
import com.bloxbean.cardano.client.transaction.spec.BootstrapWitness;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.VkeyWitness;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.RegCert;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScriptEvaluator;
import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;

/**
 * Every position of unbounded nesting at the maximum transaction size (ADR 0001 section 6.7), read through the raw
 * views: the real preprod trigger transaction and its block, and synthetic witness, reference and aux data native
 * scripts, witness list and constructor datums, an inline datum, redeemer data in both forms, and metadata lists, maps
 * and a deep map key. Each is hashed
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

        // a certificate model re-encodes its item for the reader (ADR 0003 D7)
        String deepCoin = "81".repeat(16_250) + "00";
        Array certificate = (Array) CborSerializationUtil.deserialize(
                decodeHexString("8307" + "8200581c" + KEY_HASH + deepCoin));
        check(((RegCert) Certificate.deserialize(certificate)).getCoin() == null, "certificate: a deep item at a coin");

        witnessSetAndAuxModels();

        String[][] metadata = {
                {"metadata lists (16,250 levels)", "a100" + "81".repeat(16_250) + "00"},
                {"metadata maps (8,000 levels)", "a100" + "a100".repeat(8_000) + "00"},
                {"metadata map key (16,000 levels)", "a100" + "a1" + "81".repeat(16_000) + "00" + "00"},
        };
        for (String[] fixture : metadata) {
            tx = tx(BODY, "a0", fixture[1]);
            check(Arrays.equals(tx.auxDataHash().orElseThrow(), hash256(fixture[1])), fixture[0] + ": aux data hash");
        }
    }

    // The mappers and the overloads that re-encode a tree (ADR 0003 D7), at every position of unbounded nesting: what
    // they cannot read is rejected as an exception.
    private static void witnessSetAndAuxModels() throws Exception {
        String list = "81".repeat(16_000) + "00";
        String map = "a100".repeat(8_000) + "00";
        String script = "820181".repeat(5_300) + SIG;
        String units = "820000";
        String keyedData = "a10581" + "8400" + "00" + list + units;
        String entryData = "a105a1" + "820000" + "82" + list + units;

        checkModel(BODY, keyedData, "f6", tx -> tx.getWitnessSet().getRedeemers().size() == 1);
        checkModel(BODY, entryData, "f6", tx -> tx.getWitnessSet().getRedeemers().size() == 1);
        checkModel(BODY, "a10481" + list, "f6", tx -> tx.getWitnessSet().getPlutusDataList().size() == 1);
        checkModel(BODY, "a10181" + script, "f6", tx -> tx.getWitnessSet().getNativeScripts().size() == 1);
        checkModel(BODY, "a0", "a100" + list, tx -> tx.getAuxiliaryData() != null);
        checkModel(BODY, "a0", "82" + "a100" + map + "80", tx -> tx.getAuxiliaryData() != null);
        checkModel(BODY, "a0", "82" + "a0" + "81" + script, tx -> tx.getAuxiliaryData().getNativeScripts().size() == 1);
        checkModel(BODY, "a0", "d90103a1" + "00" + "a100" + list, tx -> tx.getAuxiliaryData() != null);
        checkModel(BODY, "a0", "d90103a1" + "0181" + script,
                tx -> tx.getAuxiliaryData().getNativeScripts().size() == 1);

        for (String witnesses : new String[]{"a1" + "00" + list, "a1" + "0081" + list, "a1" + "0281" + list,
                "a1" + "0381" + list, "a1" + "0681" + list, "a1" + "0781" + list,
                "a1" + "05a1" + list + units, "a1" + "0581" + list, "a10581" + "84" + list + "00" + "00" + units,
                "a10581" + "84" + "00" + "00" + "00" + list, "a105a1" + "820000" + list}) {
            rejectsModel(BODY, witnesses, "f6");
            rejectsTree(() -> TransactionWitnessSet.deserialize((Map) item(witnesses)));
        }
        for (String aux : new String[]{list, "82" + list + "80", "82" + "a0" + list, "d90103a1" + "00" + list,
                "d90103a1" + "01" + list, "d90103a1" + "0281" + list})
            rejectsModel(BODY, "a0", aux);

        check(TransactionWitnessSet.deserialize((Map) item(keyedData)).getRedeemers().size() == 1, "witness set tree");
        check(TransactionWitnessSet.deserialize((Map) item("a10181" + script)).getNativeScripts().size() == 1,
                "witness set tree: native script");
        check(AuxiliaryData.deserialize((Map) item("a100" + list)).getMetadata() != null, "aux data tree: map");
        check(AuxiliaryData.deserialize((Array) item("82" + "a0" + "81" + script)).getNativeScripts().size() == 1,
                "aux data tree: array");
        check(AuxiliaryData.deserialize((Map) item("d90103a1" + "0181" + script)).getNativeScripts().size() == 1,
                "aux data tree: keyed");
        check(Redeemer.deserializePreConway((Array) item("8400" + "00" + list + units)).getIndex().signum() == 0,
                "redeemer tree: array");
        check(Redeemer.deserialize((Array) item("820000"), (Array) item("82" + list + units)).getData() != null,
                "redeemer tree: map");
        rejectsTree(() -> Redeemer.deserialize((Array) item("82" + list + "00"), (Array) item("82" + "00" + units)));
        rejectsTree(() -> ExUnits.deserialize((Array) item("82" + list + "00")));
        rejectsTree(() -> VkeyWitness.deserialize((Array) item("82" + list + "40")));
        rejectsTree(() -> BootstrapWitness.deserialize((Array) item("84" + list + "40" + "40" + "40")));
    }

    private static DataItem item(String hex) {
        return CborSerializationUtil.deserialize(decodeHexString(hex));
    }

    private static void checkModel(String body, String witnesses, String aux, Predicate<Transaction> holds)
            throws Exception {
        check(holds.test(Transaction.deserialize(decodeHexString("84" + body + witnesses + "f5" + aux))),
                "model of " + witnesses.substring(0, Math.min(16, witnesses.length())) + " / "
                        + aux.substring(0, Math.min(16, aux.length())));
    }

    private static void rejectsModel(String body, String witnesses, String aux) {
        rejectsTree(() -> Transaction.deserialize(decodeHexString("84" + body + witnesses + "f5" + aux)));
    }

    private interface Decode {
        Object run() throws Exception;
    }

    // Rejected with an ordinary exception: a stack overflow is an Error, and fails the test.
    private static void rejectsTree(Decode decode) {
        boolean rejected = false;
        try {
            decode.run();
        } catch (Exception e) {
            rejected = true;
        }
        check(rejected, "a deep value is rejected");
    }

    // The real trigger: its TxId, its script's hash as on chain, evaluation as the ledger decided, and its block's body hash.
    private static void trigger() throws Exception {
        RealCborFixturesAccess.Tx trigger = RealCborFixturesAccess.trigger();
        RawTx tx = RawTx.of(trigger.cbor());
        check(encodeHexString(tx.txId()).equals(trigger.txHash()), "trigger: TxId");
        RawScript script = tx.scripts().get(0);
        check(encodeHexString(script.hash()).equals(RealCborFixturesAccess.TRIGGER_NATIVE_SCRIPT_HASH), "trigger: script hash");
        NativeScript model = (NativeScript) RawModels.toScript(script);
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
        NativeScript model = (NativeScript) RawModels.toScript(script);
        check(Arrays.equals(model.getScriptHash(), expected), name + ": model script hash");
        check(NativeScriptEvaluator.evaluate(model, Set.of(KEY_HASH), null, null), name + ": holds with the signature");
        check(!NativeScriptEvaluator.evaluate(model, Set.of(), null, null), name + ": fails without it");
    }

    private static void datum(String name, RawDatum datum, String hex) throws Exception {
        check(Arrays.equals(datum.span().bytes(), decodeHexString(hex)), name + ": span");
        check(Arrays.equals(datum.hash(), hash256(hex)), name + ": datum hash");
        PlutusData model = RawModels.toPlutusData(datum);
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
