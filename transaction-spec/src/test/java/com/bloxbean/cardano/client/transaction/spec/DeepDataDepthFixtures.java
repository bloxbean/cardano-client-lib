package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.raw.RawScript;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * The deeply nested native scripts, datums and metadata that reached Yaci's sync from real and crafted blocks
 * (bloxbean/yaci#185, #186, #188, #191), through every path of a transaction here: decoding, writing and hashing.
 * Malformed data is rejected with an ordinary exception. Run by {@link DeepDataDepthTest}.
 */
final class DeepDataDepthFixtures {
    private static final String KEY_SCRIPT = "8200581c" + "00".repeat(28);
    private static final String BODY = "a30081825820" + "00".repeat(32) + "0001800200";
    private static final int SCRIPT_DEPTH = 5400;

    private static final List<String> DATUMS = List.of(
            "81".repeat(10000) + "00",
            "a100".repeat(5000) + "00",
            "bf00".repeat(5000) + "00" + "ff".repeat(5000),
            "d87981".repeat(5000) + "00",
            "d8799f".repeat(5000) + "00" + "ff".repeat(5000),
            "81a100d87981".repeat(2000) + "00",
            "a1" + "81".repeat(5000) + "0000",
            "a11800".repeat(3000) + "1800",
            // a map that lost a pair when decoded, at every level
            "a2a000bfff".repeat(6000) + "00",
            "a2010001".repeat(4000) + "00");

    private static final List<String> METADATA = List.of(
            "a100" + "a10081".repeat(2000) + "00",
            "a100" + "a100".repeat(3000) + "00",
            "a100" + "81".repeat(10000) + "00");

    private static final List<String> MALFORMED = List.of(
            "a100".repeat(5000) + "1e",
            "81".repeat(8000) + "1e",
            "d87981".repeat(3000) + "1e",
            "a100".repeat(5000));

    private interface Step {
        void run() throws Exception;
    }

    private DeepDataDepthFixtures() {
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("ok: deep data depth fixtures");
    }

    static void runAll() throws Exception {
        nativeScripts();
        nativeScriptHashComesFromTheOriginalBytes();
        datums();
        metadata();
        malformedDataIsRejected();
    }

    private static void nativeScripts() throws Exception {
        for (String head : List.of("8201", "8202", "830301")) {
            String script = (head + "81").repeat(SCRIPT_DEPTH) + KEY_SCRIPT;
            byte[] expectedHash = nativeScriptHash(script);

            NativeScript decoded = NativeScript.deserialize((Array) item(script));
            check(Arrays.equals(decoded.getScriptHash(), expectedHash), "model hash " + head);
            check(Arrays.equals(CborSerializationUtil.serialize(decoded.serializeAsDataItem(), false), hex(script)),
                    "model bytes " + head);
            NativeScript.deserializeScriptRef(hex("8200" + script));

            byte[] tx = hex(tx("01", "81" + script));
            Transaction transaction = Transaction.deserialize(tx);
            check(transaction.getWitnessSet().getNativeScripts().size() == 1, "witness script " + head);
            transaction.serialize();
            check(Arrays.equals(RawTx.of(tx).scripts().get(0).hash(), expectedHash), "raw hash " + head);

            AuxiliaryData.deserialize((Map) item("d90103a10181" + script));
            Transaction.deserialize(hex(txWithAux("d90103a10181" + script)));
        }
    }

    /** A script's hash is of the bytes received, which a non-minimal encoding keeps only in the raw view. */
    private static void nativeScriptHashComesFromTheOriginalBytes() throws Exception {
        String script = ("821801" + "81").repeat(SCRIPT_DEPTH) + KEY_SCRIPT;
        RawScript raw = RawTx.of(hex(tx("01", "81" + script))).scripts().get(0);

        check(Arrays.equals(raw.hash(), nativeScriptHash(script)), "raw hash of a non-minimal script");
    }

    private static void datums() throws Exception {
        for (String datum : DATUMS) {
            byte[] bytes = hex(datum);
            PlutusData decoded = PlutusData.deserialize(bytes);
            CborSerializationUtil.serialize(decoded.serialize(), false);
            decoded.getDatumHash();
            check(decoded.equals(PlutusData.deserialize(bytes)), "equals");
            decoded.hashCode();

            TransactionOutput output = TransactionOutput.deserialize(item(outputWith(datum)));
            check(Arrays.equals(inlineDatumWritten(output), bytes), "inline datum bytes");
            check(Arrays.equals(inlineDatumWritten(output.copy()), bytes), "copied inline datum bytes");

            byte[] tx = hex(tx("04", "81" + datum));
            Transaction.deserialize(tx).serialize();
            check(Arrays.equals(RawTx.of(tx).witnessDatums().get(0).span().bytes(), bytes), "raw datum bytes");

            Redeemer.deserialize((Array) item("840000" + datum + "820000"));
        }
    }

    private static void metadata() throws Exception {
        for (String metadata : METADATA) {
            CborSerializationUtil.serialize(CBORMetadata.deserialize((Map) item(metadata)).getData());
            AuxiliaryData.deserialize((Map) item("d90103a100" + metadata)).getAuxiliaryDataHash();
            Transaction.deserialize(hex(txWithAux("d90103a100" + metadata))).serialize();
        }
    }

    private static void malformedDataIsRejected() {
        for (String bad : MALFORMED) {
            rejects(() -> PlutusData.deserialize(hex(bad)));
            rejects(() -> Transaction.deserialize(hex(tx("04", "81" + bad))));
            rejects(() -> RawTx.of(hex(tx("04", "81" + bad))).witnessDatums());
        }
    }

    // An overflow is an Error, so it escapes and fails the fixture.
    private static void rejects(Step step) {
        try {
            step.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError("malformed data was accepted");
    }

    private static void check(boolean condition, String what) {
        if (!condition)
            throw new AssertionError(what);
    }

    private static byte[] nativeScriptHash(String script) {
        return Blake2bUtil.blake2bHash224(hex("00" + script));
    }

    private static byte[] inlineDatumWritten(TransactionOutput output) throws Exception {
        Array option = (Array) ((Map) output.serialize()).get(new UnsignedInteger(2));
        return ((ByteString) option.getDataItems().get(1)).getBytes();
    }

    private static String outputWith(String datum) {
        int size = datum.length() / 2;
        return "a300581d60" + "00".repeat(28) + "0100028201" + "d8185a" + String.format("%08x", size) + datum;
    }

    private static String tx(String witnessKey, String value) {
        return "84" + BODY + "a1" + witnessKey + value + "f5f6";
    }

    private static String txWithAux(String auxiliaryData) {
        return "84" + BODY + "a0f5" + auxiliaryData;
    }

    private static DataItem item(String hex) {
        return CborSerializationUtil.deserialize(hex(hex));
    }

    private static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
