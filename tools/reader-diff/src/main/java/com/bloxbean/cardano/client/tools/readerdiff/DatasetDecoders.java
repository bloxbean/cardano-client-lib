package com.bloxbean.cardano.client.tools.readerdiff;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.RawBlock;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.yaci.core.model.serializers.AuxDataSerializer;
import com.bloxbean.cardano.yaci.core.model.serializers.BlockHeaderSerializer;
import com.bloxbean.cardano.yaci.core.model.serializers.BlockSerializer;
import com.bloxbean.cardano.yaci.core.model.serializers.CertificateSerializer;
import com.bloxbean.cardano.yaci.core.model.serializers.StakeCredentialSerializer;
import com.bloxbean.cardano.yaci.core.model.serializers.TransactionBodySerializer;
import com.bloxbean.cardano.yaci.core.model.serializers.TransactionInputSerializer;
import com.bloxbean.cardano.yaci.core.model.serializers.TransactionOutputSerializer;
import com.bloxbean.cardano.yaci.core.model.serializers.WitnessesSerializer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.yaci.core.util.CborSerializationUtil.deserializeOne;

/**
 * The CCL ({@code ccl:<rule>}) and Yaci ({@code yaci:<rule>}) decoders of the dataset's rules, as Yano's harness maps
 * them (its {@code CborRule}, {@code CborPlacedRules}). A rule for a part of a larger item places the sample in a
 * minimal, valid one. Yano's gates on a sample's major type are left out: here more input reaching a decoder is better.
 * Decoding lives in lambdas so a class missing from a side fails when its decoder runs, and fails the run.
 */
final class DatasetDecoders {
    private static final String HASH28 = "581c" + "00".repeat(28);
    private static final String HASH32 = "5820" + "00".repeat(32);
    private static final String HASH64 = "5840" + "00".repeat(64);
    private static final String ADDRESS = "581d60" + "00".repeat(28);
    private static final String REWARD_ACCOUNT = "581de0" + "00".repeat(28);
    private static final String INPUT = "82" + HASH32 + "00";
    private static final String MIN_BODY_ENTRIES = "0081" + INPUT + "0180" + "0200";
    private static final String MIN_BODY = "a3" + MIN_BODY_ENTRIES;
    private static final String KES_SIGNATURE = "5901c0" + "00".repeat(448);
    private static final String ANCHOR = "8260" + HASH32;
    private static final String INFO_ACTION = "8106";
    private static final String PROPOSAL = "8400" + REWARD_ACCOUNT;
    private static final String POOL_START = "8a03" + HASH28;
    private static final String POOL_AFTER_VRF = "0000d81e820001" + REWARD_ACCOUNT;
    private static final String POOL_OWNERS_RELAYS = "81" + HASH28 + "80";
    private static final String VRF_CERT = "82" + HASH64 + "5850" + "00".repeat(80);
    private static final String HEADER_SLOT_TO_ISSUER = "00f6" + HASH32;
    private static final String HEADER_VRF_TO_BODY_HASH = VRF_CERT + "00" + HASH32;
    private static final String HEADER_PROTOCOL_VERSION = "820a00";

    private DatasetDecoders() {
    }

    static Map<String, Probes.Decoder> all() {
        Map<String, Probes.Decoder> decoders = new LinkedHashMap<>();
        ccl(decoders);
        yaci(decoders);
        return decoders;
    }

    private static void ccl(Map<String, Probes.Decoder> d) {
        // The item as it is, or with one wrapper
        inTx(d, "auxiliary_data", c -> tx(hex(MIN_BODY), hex("a0"), c));
        d.put("ccl:block", DatasetDecoders::cclBlock);
        d.put("ccl:certificate", DatasetDecoders::certificate);
        d.put("ccl:cost_models", c -> CostMdls.deserialize(item(c)));
        d.put("ccl:credential", DatasetDecoders::credential);
        d.put("ccl:stake_credential", DatasetDecoders::credential);
        d.put("ccl:datum_option", c -> TransactionOutput.deserialize(item(cat(hex("a300" + ADDRESS + "010002"), c))));
        d.put("ccl:drep", c -> DRep.deserialize(item(c)));
        d.put("ccl:gov_action", c -> GovAction.deserialize((Array) item(c)));
        d.put("ccl:native_script", c -> NativeScript.deserialize((Array) item(c)));
        d.put("ccl:plutus_data", PlutusData::deserialize);
        d.put("ccl:%constr<plutus_data>", PlutusData::deserialize);
        d.put("ccl:big_int", PlutusData::deserialize);
        d.put("ccl:bounded_bytes", PlutusData::deserialize);
        d.put("ccl:proposal_procedure", DatasetDecoders::proposal);
        d.put("ccl:protocol_param_update", DatasetDecoders::paramUpdate);
        d.put("ccl:redeemer", DatasetDecoders::redeemer);
        inTx(d, "redeemers", c -> tx(hex(MIN_BODY), cat(hex("a105"), c), null));
        d.put("ccl:relay", DatasetDecoders::relay);
        d.put("ccl:script", c -> script(c));
        d.put("ccl:transaction", Transaction::deserialize);
        inTx(d, "transaction_body", c -> tx(c, hex("a0"), null));
        d.put("ccl:transaction_input", c -> List.of(
                Transaction.deserialize(tx(cat(hex("a30081"), c, hex("01800200")), hex("a0"), null)),
                TransactionInput.deserialize((Array) item(c))));
        for (String rule : List.of("transaction_output", "alonzo_transaction_output", "babbage_transaction_output"))
            d.put("ccl:" + rule, c -> TransactionOutput.deserialize(item(c)));
        inTx(d, "transaction_witness_set", c -> tx(hex(MIN_BODY), c, null));
        d.put("ccl:value", DatasetDecoders::value);
        d.put("ccl:voting_procedure", DatasetDecoders::votingProcedure);

        // Parts of a transaction body
        inBody(d, "addr_keyhash", "0e81", "");
        inBody(d, "auxiliary_data_hash", "07", "");
        inBody(d, "certificates", "04", "");
        inBody(d, "mint", "09", "");
        inBody(d, "network_id", "0f", "");
        inBody(d, "nonzero_int64", "09a1" + HASH28 + "a140", "");
        inBody(d, "proposal_procedures", "14", "");
        inBody(d, "script_data_hash", "0b", "");
        inBody(d, "slot", "03", "");
        inBody(d, "voter", "13a1", "a1" + INPUT + "8200f6");
        inBody(d, "voting_procedures", "13", "");
        inBody(d, "withdrawals", "05", "");
        placedInBody(d, "coin", "a30081" + INPUT + "018002", "");
        placedInBody(d, "hash32", "a3008182", "00" + "01800200");
        placedInBody(d, "transaction_id", "a3008182", "00" + "01800200");

        // Parts of a witness set
        inWitnessSet(d, "bootstrap_witness", "a10281", "");
        inWitnessSet(d, "plutus_v1_script", "a10381", "");
        inWitnessSet(d, "plutus_v2_script", "a10681", "");
        inWitnessSet(d, "plutus_v3_script", "a10781", "");
        inWitnessSet(d, "signature", "a1008182" + HASH32, "");
        inWitnessSet(d, "vkey", "a1008182", HASH64);
        inWitnessSet(d, "vkeywitness", "a10081", "");

        // Parts of a certificate
        placed(d, "committee_cold_credential", "830e", "8200" + HASH28, DatasetDecoders::certificate);
        placed(d, "committee_hot_credential", "830e8200" + HASH28, "", DatasetDecoders::certificate);
        placed(d, "drep_credential", "8410", "00f6", DatasetDecoders::certificate);
        placed(d, "epoch", "8304" + HASH28, "", DatasetDecoders::certificate);
        placed(d, "pool_keyhash", "8304", "00", DatasetDecoders::certificate);
        placed(d, "pool_metadata", POOL_START + HASH32 + POOL_AFTER_VRF + POOL_OWNERS_RELAYS, "",
                DatasetDecoders::certificate);
        placed(d, "vrf_keyhash", POOL_START, POOL_AFTER_VRF + POOL_OWNERS_RELAYS + "f6",
                DatasetDecoders::certificate);

        // Parts of a relay
        placed(d, "dns_name", "8301f6", "", DatasetDecoders::relay);
        placed(d, "ipv4", "8400f6", "f6", DatasetDecoders::relay);
        placed(d, "ipv6", "8400f6f6", "", DatasetDecoders::relay);

        // Parts of a value
        placed(d, "%multiasset<positive_coin>", "8200", "", DatasetDecoders::value);
        placed(d, "asset_name", "8200a1" + HASH28 + "a1", "01", DatasetDecoders::value);
        placed(d, "policy_id", "8200a1", "a14001", DatasetDecoders::value);
        placed(d, "positive_coin", "8200a1" + HASH28 + "a140", "", DatasetDecoders::value);

        // Parts of a proposal
        placed(d, "anchor", PROPOSAL + INFO_ACTION, "", DatasetDecoders::proposal);
        placed(d, "constitution", PROPOSAL + "8305f6", ANCHOR, DatasetDecoders::proposal);
        placed(d, "gov_action_id", PROPOSAL + "8203", ANCHOR, DatasetDecoders::proposal);
        placed(d, "guardrails_script_hash", PROPOSAL + "8302a0", ANCHOR, DatasetDecoders::proposal);
        placed(d, "major_protocol_version", PROPOSAL + "8301f682", "00" + ANCHOR, DatasetDecoders::proposal);
        placed(d, "protocol_version", PROPOSAL + "8301f6", ANCHOR, DatasetDecoders::proposal);
        placed(d, "reward_account", "8400", INFO_ACTION + ANCHOR, DatasetDecoders::proposal);
        placed(d, "url", PROPOSAL + INFO_ACTION + "82", HASH32, DatasetDecoders::proposal);

        // Parts of a parameter update
        placed(d, "drep_voting_thresholds", "a1181a", "", DatasetDecoders::paramUpdate);
        placed(d, "ex_unit_prices", "a113", "", DatasetDecoders::paramUpdate);
        placed(d, "nonnegative_interval", "a109", "", DatasetDecoders::paramUpdate);
        placed(d, "pool_voting_thresholds", "a11819", "", DatasetDecoders::paramUpdate);
        placed(d, "unit_interval", "a10a", "", DatasetDecoders::paramUpdate);

        // Other parts
        placed(d, "hash28", "8201", "", DatasetDecoders::credential);
        placed(d, "script_hash", "8201", "", DatasetDecoders::credential);
        d.put("ccl:address", c -> TransactionOutput.deserialize(item(cat(hex("a200"), c, hex("0100")))));
        d.put("ccl:data", c -> TransactionOutput.deserialize(
                item(cat(hex("a300" + ADDRESS + "0100" + "028201"), c))));
        placed(d, "ex_units", "84000000", "", DatasetDecoders::redeemer);
        placed(d, "language", "a1", "80", c -> CostMdls.deserialize(item(c)));
        placed(d, "vote", "82", "f6", DatasetDecoders::votingProcedure);
        inTx(d, "auxiliary_data_array", c -> tx(hex(MIN_BODY), hex("a0"), c));
        inTx(d, "auxiliary_data_map", c -> tx(hex(MIN_BODY), hex("a0"), c));
        inTx(d, "auxiliary_scripts", c -> tx(hex(MIN_BODY), hex("a0"), cat(hex("82a0"), c)));
        inTx(d, "metadata", c -> tx(hex(MIN_BODY), hex("a0"), c));
        inTx(d, "metadatum", c -> tx(hex(MIN_BODY), hex("a0"), cat(hex("a100"), c)));
    }

    private static void yaci(Map<String, Probes.Decoder> d) {
        d.put("yaci:auxiliary_data", c -> AuxDataSerializer.INSTANCE.deserialize(c));
        d.put("yaci:block", c -> BlockSerializer.INSTANCE.deserialize(cat(hex("8207"), c)));
        d.put("yaci:certificate", c -> CertificateSerializer.INSTANCE.deserializeDI(deserializeOne(c)));
        d.put("yaci:credential", c -> StakeCredentialSerializer.INSTANCE.deserializeDI(deserializeOne(c)));
        d.put("yaci:transaction_body", c -> TransactionBodySerializer.INSTANCE.deserializeDI(deserializeOne(c), c));
        d.put("yaci:transaction_input", c -> TransactionInputSerializer.INSTANCE.deserializeDI(deserializeOne(c)));
        d.put("yaci:transaction_output", c -> TransactionOutputSerializer.INSTANCE.deserializeDI(deserializeOne(c)));
        d.put("yaci:transaction_witness_set", c -> WitnessesSerializer.INSTANCE.deserialize(c));
        d.put("yaci:header", DatasetDecoders::yaciHeader);
        d.put("yaci:header_body", c -> yaciHeader(headerOf(c)));

        String beforeVrfVkey = "8a00" + HEADER_SLOT_TO_ISSUER;
        String beforeCertificate = beforeVrfVkey + HASH32 + HEADER_VRF_TO_BODY_HASH;
        String certificateEnd = "0000" + HASH64;
        String certificate = "84" + HASH32 + certificateEnd;
        inHeaderBody(d, "block_number", "8a",
                HEADER_SLOT_TO_ISSUER + HASH32 + HEADER_VRF_TO_BODY_HASH + certificate + HEADER_PROTOCOL_VERSION);
        inHeaderBody(d, "kes_period", beforeCertificate + "84" + HASH32 + "00", HASH64 + HEADER_PROTOCOL_VERSION);
        inHeaderBody(d, "kes_vkey", beforeCertificate + "84", certificateEnd + HEADER_PROTOCOL_VERSION);
        inHeaderBody(d, "operational_cert", beforeCertificate, HEADER_PROTOCOL_VERSION);
        inHeaderBody(d, "vrf_vkey", beforeVrfVkey, HEADER_VRF_TO_BODY_HASH + certificate + HEADER_PROTOCOL_VERSION);
    }

    @FunctionalInterface
    private interface Wrapper {
        byte[] wrap(byte[] cbor);
    }

    private static void inTx(Map<String, Probes.Decoder> d, String rule, Wrapper wrapper) {
        d.put("ccl:" + rule, c -> Transaction.deserialize(wrapper.wrap(c)));
    }

    private static void inBody(Map<String, Probes.Decoder> d, String rule, String keyAndPrefix, String suffix) {
        placedInBody(d, rule, "a4" + MIN_BODY_ENTRIES + keyAndPrefix, suffix);
    }

    private static void placedInBody(Map<String, Probes.Decoder> d, String rule, String prefix, String suffix) {
        inTx(d, rule, c -> tx(cat(hex(prefix), c, hex(suffix)), hex("a0"), null));
    }

    private static void inWitnessSet(Map<String, Probes.Decoder> d, String rule, String prefix, String suffix) {
        inTx(d, rule, c -> tx(hex(MIN_BODY), cat(hex(prefix), c, hex(suffix)), null));
    }

    private static void placed(Map<String, Probes.Decoder> d, String rule, String prefix, String suffix,
                               Probes.Decoder decoder) {
        d.put("ccl:" + rule, c -> decoder.decode(cat(hex(prefix), c, hex(suffix))));
    }

    private static void inHeaderBody(Map<String, Probes.Decoder> d, String rule, String prefix, String suffix) {
        d.put("yaci:" + rule, c -> yaciHeader(headerOf(cat(hex(prefix), c, hex(suffix)))));
    }

    /** A Conway block as a raw view, with each of its transactions as a model. */
    private static Object cclBlock(byte[] cbor) throws Exception {
        RawBlock block = RawBlock.of(cbor, Era.Conway);
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < block.txCount(); i++)
            transactions.add(Transaction.deserialize(block.txBytes(i)));
        return transactions;
    }

    private static Object certificate(byte[] cbor) throws Exception {
        return Certificate.deserialize((Array) item(cbor));
    }

    private static Object credential(byte[] cbor) throws Exception {
        return StakeCredential.deserialize((Array) item(cbor));
    }

    private static Object proposal(byte[] cbor) throws Exception {
        return ProposalProcedure.deserialize(item(cbor));
    }

    private static Object paramUpdate(byte[] cbor) throws Exception {
        return ProtocolParamUpdate.deserialize(item(cbor));
    }

    private static Object value(byte[] cbor) throws Exception {
        return Value.deserialize(item(cbor));
    }

    /** CCL reads a relay only inside a pool registration. */
    private static Object relay(byte[] relay) throws Exception {
        return PoolRegistration.deserialize(item(cat(hex(POOL_START + HASH32 + POOL_AFTER_VRF + "8081"), relay,
                hex("f6")))).getRelays();
    }

    private static Object redeemer(byte[] cbor) throws Exception {
        return List.of(Transaction.deserialize(tx(hex(MIN_BODY), cat(hex("a10581"), cbor), null)),
                Redeemer.deserializePreConway((Array) item(cbor)));
    }

    private static Object script(byte[] cbor) throws Exception {
        return ((Array) item(cbor)).getDataItems().getFirst().equals(new co.nstant.in.cbor.model.UnsignedInteger(0))
                ? NativeScript.deserializeScriptRef(cbor) : PlutusScript.deserializeScriptRef(cbor);
    }

    private static Object votingProcedure(byte[] cbor) throws Exception {
        byte[] body = cat(hex("a4" + MIN_BODY_ENTRIES + "13a1" + "8200" + HASH28 + "a1" + INPUT), cbor);
        return List.of(Transaction.deserialize(tx(body, hex("a0"), null)),
                VotingProcedure.deserialize((Array) item(cbor)));
    }

    /** A header as chain sync receives one: wrapped in a byte string. */
    private static Object yaciHeader(byte[] cbor) throws Exception {
        return BlockHeaderSerializer.INSTANCE.deserialize(CborSerializationUtil.serialize(new ByteString(cbor)));
    }

    /** {@code [header_body, kes_signature]} */
    private static byte[] headerOf(byte[] headerBody) {
        return cat(hex("82"), headerBody, hex(KES_SIGNATURE));
    }

    private static DataItem item(byte[] cbor) {
        return CborSerializationUtil.deserialize(cbor);
    }

    /** {@code [body, witness_set, true, auxiliary_data / null]} */
    private static byte[] tx(byte[] body, byte[] witnessSet, byte[] auxData) {
        return cat(hex("84"), body, witnessSet, auxData == null ? hex("f5f6") : cat(hex("f5"), auxData));
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts)
            out.writeBytes(part);
        return out.toByteArray();
    }

    private static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
