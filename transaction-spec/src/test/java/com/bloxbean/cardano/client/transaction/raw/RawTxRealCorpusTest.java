package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.CostModel;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.util.RealCborFixturesAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash256;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RawTx} on every real transaction (mainnet, preprod and preview, Shelley to Conway, standalone and reassembled
 * from blocks), checked against on-chain values: the TxId, the aux data hash in body field 7, the script integrity hash in
 * body field 11, and the minting policies of body field 9.
 */
class RawTxRealCorpusTest {

    @Test
    void hashesMatchTheOnChainValues() throws Exception {
        Map<String, Integer> seen = new TreeMap<>();
        for (RealCborFixturesAccess.Tx tx : RealCborFixturesAccess.allTxsAndBlockTxs()) {
            RawTx raw = RawTx.of(tx.cbor());
            assertThat(encodeHexString(raw.txId())).as(tx.name()).isEqualTo(tx.txHash());
            assertThat(raw.span().bytes()).isEqualTo(tx.cbor());

            // body field 7 is the aux data hash, of the aux data as encoded, whatever its shape
            Optional<CborSpan> auxDataHash = raw.bodyField(7);
            assertThat(raw.auxDataHash().isPresent()).as(tx.name()).isEqualTo(auxDataHash.isPresent());
            if (auxDataHash.isPresent()) {
                assertThat(raw.auxDataHash().get()).as(tx.name()).isEqualTo(auxDataHash.get().byteString());
                count(seen, "aux data " + auxShape(raw.auxData().get()));
            }

            // a minting policy is a script of the transaction unless it comes from a reference input
            Optional<CborSpan> mint = raw.bodyField(9);
            if (mint.isPresent() && raw.bodyField(18).isEmpty()) {
                Set<String> scriptHashes = new HashSet<>();
                raw.scripts().forEach(script -> scriptHashes.add(encodeHexString(script.hash())));
                for (Map.Entry<CborSpan, CborSpan> policy : mint.get().entries())
                    assertThat(scriptHashes).as(tx.name()).contains(encodeHexString(policy.getKey().byteString()));
                count(seen, "minting policies checked against witness scripts");
            }

            readEverything(tx.name(), raw, seen);
        }
        System.out.println("raw tx corpus: " + seen);
        assertThat(seen).containsKeys("aux data metadata map", "aux data [metadata, scripts]", "aux data #6.259",
                "minting policies checked against witness scripts", "native script", "Plutus V1 script", "Plutus V2 script",
                "Plutus V3 script", "witness datum", "inline datum", "reference script", "redeemers array form",
                "redeemers map form", "legacy output with datum hash", "collateral return", "bootstrap witness");
    }

    /**
     * Body field 11 of every real transaction that has one equals {@link RawTx#scriptDataHash}, with language views for
     * the languages the transaction runs (resolved from the chain, including reference scripts) under the cost models of
     * its epoch.
     */
    @Test
    void scriptIntegrityHashesMatchBodyField11() throws Exception {
        JsonNode data;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("cbor/script-integrity.json.gz")) {
            data = new ObjectMapper().readTree(new GZIPInputStream(in));
        }
        Map<String, RealCborFixturesAccess.Tx> txs = new HashMap<>();
        RealCborFixturesAccess.allTxsAndBlockTxs().forEach(tx -> txs.putIfAbsent(tx.txHash(), tx));
        int checked = 0;
        for (JsonNode entry : data.get("txs")) {
            RealCborFixturesAccess.Tx tx = txs.get(entry.get("txHash").asText());
            JsonNode costModels = data.get("costModels").get(entry.get("network").asText() + ":" + entry.get("epoch").asInt());
            CostMdls costMdls = new CostMdls();
            for (JsonNode language : entry.get("languages")) {
                JsonNode costs = costModels.get(language.asText());
                long[] values = new long[costs.size()];
                for (int i = 0; i < values.length; i++)
                    values[i] = costs.get(i).asLong();
                costMdls.add(new CostModel(language(language.asText()), values));
            }
            RawTx raw = RawTx.of(tx.cbor());
            Optional<byte[]> hash = raw.scriptDataHash(Era.valueOf(tx.era()), costMdls.getLanguageViewEncoding());
            assertThat(hash).as(tx.name()).isPresent();
            assertThat(hash.get()).as(tx.name()).isEqualTo(raw.bodyField(11).orElseThrow().byteString());
            checked++;
        }
        assertThat(checked).isEqualTo(63);
    }

    /**
     * The views agree with the models where the models are lossless: as many witnesses, scripts, datums, redeemers and
     * outputs, the same datum hashes in outputs.
     */
    @Test
    void viewsAgreeWithTheModels() throws Exception {
        int compared = 0;
        for (RealCborFixturesAccess.Tx tx : RealCborFixturesAccess.allTxsAndBlockTxs()) {
            Transaction model;
            try {
                model = Transaction.deserialize(tx.cbor());
            } catch (Exception e) {
                continue; // the Shelley-with-metadata model bug, fixed separately
            }
            RawTx raw = RawTx.of(tx.cbor());
            // the model keeps one of equal scripts and datums (UniqueList on value equality); the views keep every entry
            var witnesses = model.getWitnessSet();
            assertBetween(tx.name(), raw.vkeyWitnesses(), size(witnesses.getVkeyWitnesses()));
            assertBetween(tx.name(), raw.bootstrapWitnesses(), size(witnesses.getBootstrapWitnesses()));
            assertBetween(tx.name(), raw.witnessDatums().stream().map(RawDatum::span).toList(), size(witnesses.getPlutusDataList()));
            assertThat(raw.redeemers()).as(tx.name()).hasSize(size(witnesses.getRedeemers()));
            assertBetween(tx.name(), raw.scripts().stream().map(RawScript::span).toList(), size(witnesses.getNativeScripts())
                    + size(witnesses.getPlutusV1Scripts()) + size(witnesses.getPlutusV2Scripts()) + size(witnesses.getPlutusV3Scripts()));
            List<RawOutput> outputs = raw.outputs();
            assertThat(outputs).as(tx.name()).hasSize(model.getBody().getOutputs().size());
            for (int i = 0; i < outputs.size(); i++) {
                var output = model.getBody().getOutputs().get(i);
                assertThat(outputs.get(i).datumHash().map(hash -> encodeHexString(hash)).orElse(null)).as(tx.name())
                        .isEqualTo(output.getDatumHash() != null ? encodeHexString(output.getDatumHash()) : null);
                assertThat(outputs.get(i).inlineDatum().isPresent()).isEqualTo(output.getInlineDatum() != null);
                assertThat(outputs.get(i).scriptRef().isPresent()).isEqualTo(output.getScriptRef() != null);
            }
            assertThat(raw.isValid()).as(tx.name()).isEqualTo(model.isValid());
            compared++;
        }
        assertThat(compared).isGreaterThan(300);
    }

    private static void readEverything(String name, RawTx raw, Map<String, Integer> seen) throws Exception {
        for (RawScript script : raw.scripts()) {
            count(seen, new String[]{"native script", "Plutus V1 script", "Plutus V2 script", "Plutus V3 script"}[script.type()]);
            // the model hashes like the original bytes when it re-encodes to them
            var model = script.toScript();
            if (script.type() != 0 || Arrays.equals(((NativeScript) model).serializeScriptBody(), script.span().bytes()))
                assertThat(model.getScriptHash()).as(name).isEqualTo(script.hash());
        }
        raw.auxScripts().forEach(script -> count(seen, "aux script"));
        for (RawDatum datum : raw.witnessDatums()) {
            count(seen, "witness datum");
            assertThat(datum.hash()).isEqualTo(blake2bHash256(datum.span().bytes()));
            datum.toPlutusData();
        }
        for (RawRedeemer redeemer : raw.redeemers()) {
            assertThat(redeemer.tag()).isBetween(0, 5);
            new RawDatum(redeemer.data()).toPlutusData();
        }
        raw.witnessField(5).ifPresent(field -> count(seen, field.majorType() == 4 ? "redeemers array form" : "redeemers map form"));
        for (RawOutput output : raw.outputs()) {
            if (output.span().majorType() == 4 && output.datumHash().isPresent())
                count(seen, "legacy output with datum hash");
            if (output.inlineDatum().isPresent()) {
                count(seen, "inline datum");
                output.inlineDatum().get().toPlutusData();
            }
            if (output.scriptRef().isPresent()) {
                count(seen, "reference script");
                assertThat(output.scriptRef().get().toScript().getScriptHash()).isEqualTo(output.scriptRef().get().hash());
            }
        }
        raw.collateralReturn().ifPresent(output -> count(seen, "collateral return"));
        if (!raw.bootstrapWitnesses().isEmpty())
            count(seen, "bootstrap witness");
        assertThat(raw.vkeyWitnesses().size() + raw.bootstrapWitnesses().size()).as(name).isPositive();
    }

    private static String auxShape(CborSpan aux) {
        if (aux.tag() == 259)
            return "#6.259";
        return aux.majorType() == 4 ? "[metadata, scripts]" : "metadata map";
    }

    private static Language language(String name) {
        switch (name) {
            case "PlutusV1":
                return Language.PLUTUS_V1;
            case "PlutusV2":
                return Language.PLUTUS_V2;
            default:
                return Language.PLUTUS_V3;
        }
    }

    // The model has at most every entry and at least one of each encoding.
    private static void assertBetween(String name, List<CborSpan> spans, int modelSize) {
        Set<String> encodings = new HashSet<>();
        spans.forEach(span -> encodings.add(encodeHexString(span.bytes())));
        assertThat(modelSize).as(name).isBetween(encodings.size(), spans.size());
    }

    private static int size(List<?> list) {
        return list != null ? list.size() : 0;
    }

    private static void count(Map<String, Integer> seen, String what) {
        seen.merge(what, 1, Integer::sum);
    }
}
