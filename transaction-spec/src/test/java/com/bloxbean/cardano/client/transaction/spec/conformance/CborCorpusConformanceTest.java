package com.bloxbean.cardano.client.transaction.spec.conformance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.bloxbean.cardano.client.transaction.spec.cert.Relay;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Conformance gate over cardano-cbor-dataset's valid Conway samples of the rules CCL has a model for: each must decode
 * into that model and re-encode to the reference bytes after the dataset's normalization. Samples that do not yet are listed with how they miss in
 * {@code cbor-conformance/expected-misses.txt}. A new miss fails, a listed sample that now passes fails, and so does
 * one that misses in a different way, so the list stays exact and can only shrink.
 *
 * <p>Runs only with {@code -PcborDataset=<dataset dir>}, as in CI's CBOR conformance job. The dataset is fetched at a
 * pinned commit, 4fd977b2 ({@code CBOR_DATASET_REVISION} in CI), never copied here, because it has no licence.</p>
 *
 * <p>What it does not cover: invalid samples, as rejecting them is the job of a strict reader, not of these model
 * decoders (transaction-spec/adr/0002-cbor-conformance-across-ccl-yaci-yano-julc.md, D10); cbor-java's decoder trees,
 * as {@link CborSerializationUtil#deserialize(byte[])} is the only decoder here; a datum hash's encoding, which the
 * model does not hold apart from its bytes; and the dataset's other rules, which are parts of these and which Yano's
 * node-diversity report measures. Values and outputs are written in the ledger's order, as the reference is
 * (ADR 0002 Q5).</p>
 */
@EnabledIfSystemProperty(named = "cbor.dataset", matches = ".+")
class CborCorpusConformanceTest {

    private static final String INPUT = ".input.cbor";
    private static final String EXPECTED = ".expected.cbor";
    private static final String MISSES = "/cbor-conformance/expected-misses.txt";

    private static final String HASH28 = "581c" + "00".repeat(28);
    private static final String HASH32 = "5820" + "00".repeat(32);
    private static final String ADDRESS = "581d60" + "00".repeat(28);
    private static final String REWARD_ACCOUNT = "581de0" + "00".repeat(28);
    private static final String MIN_BODY = "a30081" + "82" + HASH32 + "00" + "0180" + "0200";

    @FunctionalInterface
    private interface Decoder {
        Object decode(byte[] cbor) throws Exception;
    }

    @FunctionalInterface
    private interface Encoder {
        /** @return a {@link DataItem} or the encoded bytes */
        Object encode(Object model) throws Exception;
    }

    /** @param encoder null for an item whose bytes are a hash preimage: CCL keeps them, so only decoding counts */
    private record Rule(Decoder decoder, Encoder encoder) {
    }

    private static final Map<String, Rule> RULES = rules();

    private static Map<String, Rule> rules() {
        Map<String, Rule> rules = new HashMap<>();
        rules.put("auxiliary_data", new Rule(cbor -> Transaction.deserialize(tx(hex("a0"), cbor)), null));
        rules.put("native_script", new Rule(cbor -> NativeScript.deserialize((Array) item(cbor)), null));
        rules.put("plutus_data", new Rule(PlutusData::deserialize, null));
        rules.put("redeemers", new Rule(cbor -> Transaction.deserialize(tx(cat(hex("a105"), cbor), hex("f6"))), null));
        rules.put("certificate", new Rule(cbor -> Certificate.deserialize((Array) item(cbor)),
                model -> ((Certificate) model).serialize()));
        rules.put("cost_models", new Rule(cbor -> CostMdls.deserialize(item(cbor)),
                model -> ((CostMdls) model).serialize()));
        rules.put("credential", new Rule(cbor -> StakeCredential.deserialize((Array) item(cbor)),
                model -> ((StakeCredential) model).serialize()));
        rules.put("datum_option", new Rule(CborCorpusConformanceTest::datumOutput,
                CborCorpusConformanceTest::datumOption));
        rules.put("drep", new Rule(cbor -> DRep.deserialize(item(cbor)), model -> ((DRep) model).serialize()));
        rules.put("gov_action", new Rule(cbor -> GovAction.deserialize((Array) item(cbor)),
                model -> ((GovAction) model).serialize()));
        rules.put("proposal_procedure", new Rule(cbor -> ProposalProcedure.deserialize(item(cbor)),
                model -> ((ProposalProcedure) model).serialize()));
        rules.put("protocol_param_update", new Rule(cbor -> ProtocolParamUpdate.deserialize(item(cbor)),
                model -> ((ProtocolParamUpdate) model).serialize()));
        rules.put("redeemer", new Rule(cbor -> Redeemer.deserializePreConway((Array) item(cbor)),
                model -> ((Redeemer) model).serializePreConway()));
        rules.put("relay", new Rule(CborCorpusConformanceTest::relay, model -> ((Relay) model).serialize()));
        rules.put("script", new Rule(CborCorpusConformanceTest::script, model -> model instanceof NativeScript n
                ? n.scriptRefBytes() : ((PlutusScript) model).scriptRefBytes()));
        rules.put("transaction_input", new Rule(cbor -> TransactionInput.deserialize((Array) item(cbor)),
                model -> ((TransactionInput) model).serialize()));
        rules.put("transaction_output", new Rule(cbor -> TransactionOutput.deserialize(item(cbor)),
                model -> ((TransactionOutput) model).serializeInLedgerOrder()));
        rules.put("value", new Rule(cbor -> Value.deserialize(item(cbor)),
                model -> ((Value) model).serializeInLedgerOrder()));
        rules.put("voting_procedure", new Rule(cbor -> VotingProcedure.deserialize((Array) item(cbor)),
                model -> ((VotingProcedure) model).serialize()));
        return rules;
    }

    @Test
    void normalizerMatchesTheDatasetVectors() throws IOException {
        List<Path> inputs = files(dataset().resolve("normalization"), INPUT);
        assertThat(inputs).isNotEmpty();
        for (Path input : inputs) {
            assertThat(CborNormalizer.normalize(Files.readAllBytes(input))).as(input.getFileName().toString())
                    .isEqualTo(Files.readAllBytes(expectedOf(input)));
        }
    }

    @Test
    void onlyTheListedSamplesMiss() throws IOException {
        Map<String, String> misses = new TreeMap<>();
        Map<String, String> details = new TreeMap<>();
        int samples = 0;
        for (Path input : validSamples()) {
            samples++;
            String rule = input.getParent().getParent().getFileName().toString();
            String id = rule + "/" + input.getFileName().toString().replace(INPUT, "");
            Miss miss = miss(rule, input);
            if (miss != null) {
                misses.put(id, miss.kind());
                details.put(id, miss.detail());
            }
        }
        Map<String, String> listed = listedMisses();

        assertThat(samples).as("valid samples in the pinned corpus").isEqualTo(2837);
        assertThat(misses.keySet().stream().filter(id -> !misses.get(id).equals(listed.get(id)))
                .map(id -> id + " " + misses.get(id) + ": " + details.get(id)).collect(Collectors.toList()))
                .as("misses that are new, or differ from the list").isEmpty();
        assertThat(listed.keySet().stream().filter(id -> !misses.containsKey(id)).collect(Collectors.toList()))
                .as("listed samples that now pass: remove them from " + MISSES).isEmpty();
    }

    /** A listed sample must not hide a new overflow, so an overflow is never a miss. */
    @Test
    void aStackOverflowFailsTheGate() throws IOException {
        Path input = validSamples().get(0);
        Rule decoderOverflows = new Rule(cbor -> {
            throw new StackOverflowError();
        }, null);
        Rule encoderOverflows = new Rule(cbor -> cbor, model -> {
            throw new StackOverflowError();
        });

        assertThatThrownBy(() -> miss(decoderOverflows, input)).isInstanceOf(StackOverflowError.class);
        assertThatThrownBy(() -> miss(encoderOverflows, input)).isInstanceOf(StackOverflowError.class);
    }

    /** @param kind {@code rejected} by the decoder, {@code encoder} failure, or re-encoding {@code differs} */
    private record Miss(String kind, String detail) {
    }

    /** @return how the valid sample misses, or null if it does not */
    private static Miss miss(String ruleName, Path input) throws IOException {
        return miss(RULES.get(ruleName), input);
    }

    private static Miss miss(Rule rule, Path input) throws IOException {
        Object model;
        try {
            model = rule.decoder().decode(Files.readAllBytes(input));
        } catch (Exception e) {
            return new Miss("rejected", e.toString());
        }
        if (rule.encoder() == null) {
            return null;
        }
        byte[] normalized;
        try {
            Object encoded = rule.encoder().encode(model);
            byte[] bytes = encoded instanceof byte[] b ? b : CborSerializationUtil.serialize((DataItem) encoded, false);
            normalized = CborNormalizer.normalize(bytes);
        } catch (Exception e) {
            return new Miss("encoder", e.toString());
        }
        return Arrays.equals(normalized, CborNormalizer.normalize(Files.readAllBytes(expectedOf(input)))) ? null
                : new Miss("differs", "re-encoding differs from the reference");
    }

    private static Relay relay(byte[] relay) throws Exception {
        byte[] pool = cat(hex("8a03" + HASH28 + HASH32 + "0000d81e820001" + REWARD_ACCOUNT + "8081"), relay, hex("f6"));
        return PoolRegistration.deserialize(item(pool)).getRelays().get(0);
    }

    private static Object script(byte[] cbor) throws Exception {
        return new UnsignedInteger(0).equals(((Array) item(cbor)).getDataItems().get(0))
                ? NativeScript.deserializeScriptRef(cbor) : PlutusScript.deserializeScriptRef(cbor);
    }

    private static TransactionOutput datumOutput(byte[] datumOption) throws Exception {
        return TransactionOutput.deserialize(item(cat(hex("a300" + ADDRESS + "010002"), datumOption)));
    }

    /** A datum hash output serializes in the pre-Babbage array form, so only an inline datum is taken from a map. */
    private static DataItem datumOption(Object model) throws Exception {
        TransactionOutput output = (TransactionOutput) model;
        if (output.getInlineDatum() != null) {
            return ((co.nstant.in.cbor.model.Map) output.serialize()).get(new UnsignedInteger(2));
        }
        Array option = new Array();
        option.add(new UnsignedInteger(0));
        option.add(new ByteString(output.getDatumHash()));
        return option;
    }

    /** A minimal transaction carrying the witness set and auxiliary data. */
    private static byte[] tx(byte[] witnessSet, byte[] auxiliaryData) {
        return cat(hex("84" + MIN_BODY), witnessSet, hex("f5"), auxiliaryData);
    }

    private static DataItem item(byte[] cbor) {
        return CborSerializationUtil.deserialize(cbor);
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Stream.of(parts).forEach(out::writeBytes);
        return out.toByteArray();
    }

    private static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    private static Path dataset() {
        return Path.of(System.getProperty("cbor.dataset"));
    }

    private static List<Path> validSamples() throws IOException {
        List<Path> samples = new ArrayList<>();
        for (String rule : RULES.keySet()) {
            for (String category : List.of("valid", "manual-valid")) {
                Path directory = dataset().resolve("conway").resolve(rule).resolve(category);
                if (Files.isDirectory(directory)) {
                    samples.addAll(files(directory, INPUT));
                }
            }
        }
        return samples;
    }

    private static List<Path> files(Path directory, String suffix) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().endsWith(suffix)).sorted()
                    .collect(Collectors.toList());
        }
    }

    private static Path expectedOf(Path input) {
        return input.resolveSibling(input.getFileName().toString().replace(INPUT, EXPECTED));
    }

    private static Map<String, String> listedMisses() throws IOException {
        try (InputStream in = CborCorpusConformanceTest.class.getResourceAsStream(MISSES)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")).map(line -> line.split(" "))
                    .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
        }
    }
}
