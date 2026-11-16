package com.bloxbean.cardano.client.ledger.conformance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.raw.FieldType;
import com.bloxbean.cardano.client.transaction.raw.WitnessSetField;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.Relay;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedure;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.bloxbean.cardano.client.ledger.conformance.CborRule.*;

/**
 * The dataset's rules for parts of a larger item. A part is read by its own type or record ({@link CborRule#reads}),
 * or in a minimal, valid parent where the part's checks are the parent's: a collection's (non-empty, distinct items),
 * a map's, or a part that is read only in a transaction or a header. CCL has a model of the parent only, so the sample
 * is placed in one for the re-encoding, and a valid sample has to come back out of it: the part is taken from the
 * re-encoded model, at the path it was placed, so a value the model drops or changes fails.
 *
 * <p>A part of a header, of auxiliary data or of a block has no re-encoding to read: the node keeps those items' bytes
 * ({@link CborRule#kept}).</p>
 */
final class CborPlacedRules {


    /** A path step: the first key of a map. */
    private static final Object KEY = new Object();
    /** A path step: the first value of a map. */
    private static final Object VALUE = new Object();

    private static final int BYTES = 2;
    private static final int ARRAY = 4;
    private static final int MAP = 5;
    private static final int TAG = 6;
    /** {@code PlutusV4}, the last of Haskell's {@code Language} at 6176e413 (Plutus/Language.hs:273-277). */
    private static final int MAX_LANGUAGE = 3;

    private static final String HASH64 = "5840" + "00".repeat(64);
    private static final String ANCHOR = "8260" + HASH32;
    private static final String INFO_ACTION = "8106";
    private static final String PROPOSAL = "8400" + REWARD_ACCOUNT;
    /** {@code [operator, vrf, pledge, cost, margin, reward account]}, in two parts around {@code vrf}. */
    private static final String POOL_START = "8a03" + HASH28;
    private static final String POOL_AFTER_VRF = "0000d81e820001" + REWARD_ACCOUNT;
    /** {@code owners, relays} */
    private static final String POOL_OWNERS_RELAYS = "81" + HASH28 + "80";
    private static final String VRF_CERT = "82" + HASH64 + "5850" + "00".repeat(80);
    private static final String HEADER_BODY_START = "8a";
    /** {@code slot, prev_hash, issuer_vkey} */
    private static final String HEADER_SLOT_TO_ISSUER = "00f6" + HASH32;
    /** {@code vrf_result, block_body_size, block_body_hash} */
    private static final String HEADER_VRF_TO_BODY_HASH = VRF_CERT + "00" + HASH32;
    private static final String HEADER_PROTOCOL_VERSION = "820a00";
    private static final Map<String, String[]> HEADER_BODY_PARTS = headerBodyParts();

    private CborPlacedRules() {
    }

    /** @param base the rules these can share decoders with */
    static Stream<CborRule> all(Map<String, CborRule> base) {
        return Stream.of(bodyParts(), witnessParts(), certificateParts(), relayParts(), valueParts(), proposalParts(),
                paramParts(), plutusParts(), otherParts(base), headerParts(), auxiliaryDataParts())
                .flatMap(List::stream);
    }

    private static List<CborRule> bodyParts() {
        return List.of(
                inBody("addr_keyhash", reads(FieldType.HASH28), "0e81", "", 14, 0),
                inBody("auxiliary_data_hash", reads(FieldType.HASH32), "07", "", 7),
                inBody("certificates", null, "04", "", 4),
                inBodyInLedgerOrder("mint", "09", body -> MultiAsset.serializeInLedgerOrder(body.getMint())),
                inBody("network_id", reads(FieldType.NETWORK_ID), "0f", "", 15),
                inBody("nonzero_int64", reads(FieldType.NONZERO_INT64), "09a1" + HASH28 + "a140", "", 9, VALUE, VALUE),
                inBody("proposal_procedures", null, "14", "", 20),
                inBody("script_data_hash", reads(FieldType.HASH32), "0b", "", 11),
                inBody("slot", reads(FieldType.UINT), "03", "", 3),
                inBody("voter", reads(FieldType.VOTER), "13a1", "a1" + INPUT + "82" + "00f6", 19, KEY),
                inBody("voting_procedures", null, "13", "", 19),
                inBodyInLedgerOrder("withdrawals", "05", body -> Withdrawal.serializeInLedgerOrder(body.getWithdrawals())),
                placedInBody("coin", reads(FieldType.COIN), "a30081" + INPUT + "018002", "", 2),
                placedInBody("hash32", reads(FieldType.HASH32), "a3008182", "00" + "01800200", 0, 0, 0),
                placedInBody("transaction_id", reads(FieldType.HASH32), "a3008182", "00" + "01800200", 0, 0, 0));
    }

    private static List<CborRule> witnessParts() {
        return List.of(
                inWitnessSet("bootstrap_witness", reads(WitnessSetField.BOOTSTRAP_WITNESSES.spec().record()), "a10281",
                        "", 2, 0),
                inWitnessSet("plutus_v1_script", reads(FieldType.PLUTUS_SCRIPT), "a10381", "", 3, 0),
                inWitnessSet("plutus_v2_script", reads(FieldType.PLUTUS_SCRIPT), "a10681", "", 6, 0),
                inWitnessSet("plutus_v3_script", reads(FieldType.PLUTUS_SCRIPT), "a10781", "", 7, 0),
                inWitnessSet("signature", reads(FieldType.SIGNATURE), "a1008182" + HASH32, "", 0, 0, 1),
                inWitnessSet("vkey", reads(FieldType.VKEY), "a1008182", HASH64, 0, 0, 0),
                inWitnessSet("vkeywitness", reads(WitnessSetField.VKEY_WITNESSES.spec().record()), "a10081", "", 0,
                        0));
    }

    private static List<CborRule> certificateParts() {
        return List.of(
                inCertificate("committee_cold_credential", reads(FieldType.CREDENTIAL), "830e", "8200" + HASH28, 1),
                inCertificate("committee_hot_credential", reads(FieldType.CREDENTIAL), "830e8200" + HASH28, "", 2),
                inCertificate("drep_credential", reads(FieldType.CREDENTIAL), "8410", "00f6", 1),
                inCertificate("epoch", reads(FieldType.UINT), "8304" + HASH28, "", 2),
                inCertificate("pool_keyhash", reads(FieldType.HASH28), "8304", "00", 1),
                only(ARRAY, inCertificate("pool_metadata", reads(FieldType.OPTIONAL_POOL_METADATA),
                        POOL_START + HASH32 + POOL_AFTER_VRF + POOL_OWNERS_RELAYS, "", 9)),
                inCertificate("vrf_keyhash", reads(FieldType.HASH32), POOL_START,
                        POOL_AFTER_VRF + POOL_OWNERS_RELAYS + "f6", 2));
    }

    private static List<CborRule> relayParts() {
        return List.of(
                inRelay("dns_name", reads(FieldType.DNS_NAME), "8301f6", "", 2),
                only(BYTES, inRelay("ipv4", reads(FieldType.OPTIONAL_IPV4), "8400f6", "f6", 2)),
                only(BYTES, inRelay("ipv6", reads(FieldType.OPTIONAL_IPV6), "8400f6f6", "", 3)));
    }

    private static List<CborRule> valueParts() {
        return List.of(
                // Haskell writes the multi-asset alone (EraSpec.hs:404-409): an empty one is a0, where a value of
                // coin and an empty multi-asset is written as the coin
                placed("%multiasset<positive_coin>", placedIn(reads(FieldType.VALUE), "8200", ""), "8200", "",
                        cbor -> Value.deserialize(item(cbor)),
                        c -> MultiAsset.serializeInLedgerOrder(multiAssets((Value) c))),
                inValue("asset_name", reads(FieldType.ASSET_NAME), "8200a1" + HASH28 + "a1", "01", 1, VALUE, KEY),
                inValue("policy_id", reads(FieldType.HASH28), "8200a1", "a14001", 1, KEY),
                inValue("positive_coin", reads(FieldType.POSITIVE_COIN), "8200a1" + HASH28 + "a140", "", 1, VALUE,
                        VALUE));
    }

    private static List<CborRule> proposalParts() {
        return List.of(
                inProposal("anchor", reads(FieldType.ANCHOR), PROPOSAL + INFO_ACTION, "", 3),
                inProposal("constitution", reads(FieldType.CONSTITUTION), PROPOSAL + "8305f6", ANCHOR, 2, 2),
                only(ARRAY, inProposal("gov_action_id", reads(FieldType.GOV_ACTION_ID), PROPOSAL + "8203", ANCHOR, 2,
                        1)),
                only(BYTES, inProposal("guardrails_script_hash", reads(FieldType.OPTIONAL_HASH28), PROPOSAL + "8302a0",
                        ANCHOR, 2, 2)),
                inProposal("major_protocol_version", reads(FieldType.MAJOR_PROTOCOL_VERSION), PROPOSAL + "8301f682",
                        "00" + ANCHOR, 2, 2, 0),
                inProposal("protocol_version", reads(FieldType.PROTOCOL_VERSION), PROPOSAL + "8301f6", ANCHOR, 2, 2),
                inProposal("reward_account", reads(FieldType.REWARD_ACCOUNT), "8400", INFO_ACTION + ANCHOR, 1),
                inProposal("url", reads(FieldType.URL), PROPOSAL + INFO_ACTION + "82", HASH32, 3, 0));
    }

    private static List<CborRule> paramParts() {
        return List.of(
                inParamUpdate("drep_voting_thresholds", FieldType.DREP_VOTING_THRESHOLDS, "a1181a", 26),
                inParamUpdate("ex_unit_prices", FieldType.EX_UNIT_PRICES, "a113", 19),
                inParamUpdate("nonnegative_interval", FieldType.NONNEGATIVE_INTERVAL, "a109", 9),
                inParamUpdate("pool_voting_thresholds", FieldType.POOL_VOTING_THRESHOLDS, "a11819", 25),
                inParamUpdate("unit_interval", FieldType.UNIT_INTERVAL, "a10a", 10));
    }

    /**
     * The alternatives of {@code plutus_data} (PlutusData.hs's {@code decodeData}), told apart by the head: the strict read
     * reads them all as one type, so another alternative is a rejection here. Like {@code plutus_data}, an alternative is
     * written as its bytes, which the node keeps ({@code Data} is {@code MemoBytes}), and the dataset compares
     * normalized.
     */
    private static List<CborRule> plutusParts() {
        return List.of(
                data("%constr<plutus_data>", head -> head >>> 5 == TAG && !isBignum(head), ConstrPlutusData.class),
                data("big_int", head -> head >>> 5 <= 1 || isBignum(head), BigIntPlutusData.class),
                data("bounded_bytes", head -> head >>> 5 == BYTES, BytesPlutusData.class));
    }

    /** cborg reads only the one-byte heads of tags 2 and 3 as a bignum. */
    private static boolean isBignum(int head) {
        return head == 0xc2 || head == 0xc3;
    }

    private static CborRule data(String name, Predicate<Integer> head, Class<? extends PlutusData> alternative) {
        return only(head, kept(name, reads(FieldType.PLUTUS_DATA),
                cbor -> alternative.cast(PlutusData.deserialize(cbor))));
    }

    private static List<CborRule> otherParts(Map<String, CborRule> base) {
        return List.of(
                sameAs("stake_credential", base.get("credential")),
                sameAs("alonzo_transaction_output", base.get("transaction_output")),
                sameAs("babbage_transaction_output", base.get("transaction_output")),
                inCredential("hash28"),
                inCredential("script_hash"),
                model("address", reads(FieldType.ADDRESS),
                        cbor -> TransactionOutput.deserialize(item(addressOutput(cbor))),
                        c -> at(((TransactionOutput) c).serialize(), 0)),
                kept("data", reads(FieldType.DATA),
                        cbor -> TransactionOutput.deserialize(item(datumOutput(cat("8201", cbor))))),
                model("ex_units", reads(FieldType.EX_UNITS),
                        cbor -> Redeemer.deserializePreConway((Array) item(cat("84000000", cbor))),
                        c -> at(((Redeemer) c).serializePreConway(), 3)),
                // Haskell's Language alone (decodeEnumBounded, PlutusV1 to PlutusV4 at 6176e413), which no Conway
                // transaction decodes: the strict read takes a language as a cost model's key, a Word8 (decodeCostModelsLenient)
                model("language", CborPlacedRules::strictLanguage, CborPlacedRules::language,
                        c -> at(((CostMdls) c).serialize(), KEY)),
                model("vote", reads(FieldType.VOTE),
                        cbor -> VotingProcedure.deserialize((Array) item(cat("82", cbor, "f6"))),
                        c -> at(((VotingProcedure) c).serialize(), 0)));
    }

    private static List<CborRule> headerParts() {
        return List.of(
                inHeaderBody("block_number", reads(FieldType.UINT)),
                inHeaderBody("kes_period", reads(FieldType.KES_PERIOD)),
                inHeaderBody("kes_vkey", reads(FieldType.KES_VKEY)),
                inHeaderBody("operational_cert", reads(FieldType.OPERATIONAL_CERT)),
                inHeaderBody("vrf_vkey", reads(FieldType.VRF_VKEY)));
    }

    /** Where each header body part is placed: its prefix and suffix in a header body. Fixed; built once. */
    private static Map<String, String[]> headerBodyParts() {
        String beforeVrfVkey = HEADER_BODY_START + "00" + HEADER_SLOT_TO_ISSUER;
        String beforeCertificate = beforeVrfVkey + HASH32 + HEADER_VRF_TO_BODY_HASH;
        String certificateEnd = "0000" + HASH64;
        String certificate = "84" + HASH32 + certificateEnd;
        return Map.of(
                "block_number", new String[]{HEADER_BODY_START, HEADER_SLOT_TO_ISSUER + HASH32
                        + HEADER_VRF_TO_BODY_HASH + certificate + HEADER_PROTOCOL_VERSION},
                "kes_period", new String[]{beforeCertificate + "84" + HASH32 + "00", HASH64 + HEADER_PROTOCOL_VERSION},
                "kes_vkey", new String[]{beforeCertificate + "84", certificateEnd + HEADER_PROTOCOL_VERSION},
                "operational_cert", new String[]{beforeCertificate, HEADER_PROTOCOL_VERSION},
                "vrf_vkey", new String[]{beforeVrfVkey, HEADER_VRF_TO_BODY_HASH + certificate
                        + HEADER_PROTOCOL_VERSION});
    }

    private static List<CborRule> auxiliaryDataParts() {
        return List.of(
                only(ARRAY, inAuxiliaryData("auxiliary_data_array", reads(FieldType.OPTIONAL_AUXILIARY_DATA), "", "")),
                only(TAG, inAuxiliaryData("auxiliary_data_map", reads(FieldType.OPTIONAL_AUXILIARY_DATA), "", "")),
                inAuxiliaryData("auxiliary_scripts", null, "82a0", ""),
                only(MAP, inAuxiliaryData("metadata", reads(FieldType.METADATA), "", "")),
                inAuxiliaryData("metadatum", null, "a100", ""));
    }

    private static CborRule inBody(String name, Decoder strict, String keyAndPrefix, String suffix, Object... path) {
        return placedInBody(name, strict, "a4" + MIN_BODY_ENTRIES + keyAndPrefix, suffix, path);
    }

    /** The sample inside a transaction body: {@code prefix} and {@code suffix} make the rest of the body. */
    private static CborRule placedInBody(String name, Decoder strict, String prefix, String suffix, Object... path) {
        Wrapper wrapper = placedInTransaction(name, cbor -> tx(cat(prefix, cbor, suffix), hex("a0"), null));
        return model(name, orIn(strict, inTransaction(wrapper)), cbor -> Transaction.deserialize(wrapper.wrap(cbor)),
                c -> at(((Transaction) c).getBody().serialize(), path));
    }

    /**
     * A body writes CIP-21's order for hardware wallets, so the part is written on its own in the ledger's order. The
     * part is a map, read in the body.
     */
    private static CborRule inBodyInLedgerOrder(String name, String prefix, LedgerOrderWriter writer) {
        Wrapper wrapper = placedInTransaction(name, cbor -> tx(cat("a4" + MIN_BODY_ENTRIES + prefix, cbor), hex("a0"),
                null));
        return model(name, inTransaction(wrapper), cbor -> Transaction.deserialize(wrapper.wrap(cbor)),
                c -> writer.write(((Transaction) c).getBody()));
    }

    @FunctionalInterface
    private interface LedgerOrderWriter {
        DataItem write(com.bloxbean.cardano.client.transaction.spec.TransactionBody body) throws Exception;
    }

    private static CborRule inWitnessSet(String name, Decoder strict, String prefix, String suffix, Object... path) {
        Wrapper wrapper = placedInTransaction(name, cbor -> tx(hex(MIN_BODY), cat(prefix, cbor, suffix), null));
        return model(name, strict, cbor -> Transaction.deserialize(wrapper.wrap(cbor)),
                c -> at(((Transaction) c).getWitnessSet().serialize(), path));
    }

    private static CborRule inCertificate(String name, Decoder strict, String prefix, String suffix, Object... path) {
        return placed(name, strict, prefix, suffix, cbor -> Certificate.deserialize((Array) item(cbor)),
                c -> ((Certificate) c).serialize(), path);
    }

    private static CborRule inRelay(String name, Decoder strict, String prefix, String suffix, Object... path) {
        return placed(name, strict, prefix, suffix, CborRule::relay, c -> ((Relay) c).serialize(), path);
    }

    private static CborRule inValue(String name, Decoder strict, String prefix, String suffix, Object... path) {
        return placed(name, orIn(strict, placedIn(reads(FieldType.VALUE), prefix, suffix)), prefix, suffix,
                cbor -> Value.deserialize(item(cbor)), c -> ((Value) c).serializeInLedgerOrder(), path);
    }

    private static CborRule inProposal(String name, Decoder strict, String prefix, String suffix, Object... path) {
        return placed(name, strict, prefix, suffix, cbor -> ProposalProcedure.deserialize(item(cbor)),
                c -> ((ProposalProcedure) c).serialize(), path);
    }

    private static CborRule inParamUpdate(String name, FieldType type, String prefix, int key) {
        return placed(name, reads(type), prefix, "", cbor -> ProtocolParamUpdate.deserialize(item(cbor)),
                c -> ((ProtocolParamUpdate) c).serialize(), key);
    }

    private static CborRule inCredential(String name) {
        return placed(name, reads(FieldType.HASH28), "8201", "",
                cbor -> StakeCredential.deserialize((Array) item(cbor)), c -> ((StakeCredential) c).serialize(), 1);
    }

    /** The sample read by {@code strict}, and placed in an item with a model of its own, taken from it at the path. */
    private static CborRule placed(String name, Decoder strict, String prefix, String suffix, Decoder library,
                                   Serializer serializer, Object... path) {
        return model(name, strict, cbor -> library.decode(cat(prefix, cbor, suffix)),
                c -> at(serializer.serialize(c), path));
    }

    /** {@code strict}, or for a part whose checks are its parent's ({@code strict} null), the parent's reader. */
    private static Decoder orIn(Decoder strict, Decoder parent) {
        return strict != null ? strict : parent;
    }

    private static Decoder placedIn(Decoder parent, String prefix, String suffix) {
        return cbor -> parent.decode(cat(prefix, cbor, suffix));
    }

    @FunctionalInterface
    private interface Serializer {
        DataItem serialize(Object model) throws Exception;
    }

    /**
     * A header body's layout is its era's, so its rule is read in a header; its parts by their own types. CCL has no
     * model of a header, so the part has no model stage; {@code prefix} and {@code suffix} are kept for a caller's
     * own header decoders, such as Yano's ({@link #headerBodyOf}).
     */
    private static CborRule inHeaderBody(String name, Decoder strict) {
        if (!HEADER_BODY_PARTS.containsKey(name)) {
            throw new IllegalStateException("no header body placement for " + name);
        }
        return kept(name, strict, null);
    }


    /**
     * @param rule   one of the header body's part rules ({@code block_number}, {@code kes_period}, {@code kes_vkey},
     *               {@code operational_cert}, {@code vrf_vkey})
     * @param sample the sample
     * @return the header body the sample is placed in, or null for another rule
     */
    static byte[] headerBodyOf(String rule, byte[] sample) {
        String[] parts = HEADER_BODY_PARTS.get(rule);
        return parts == null ? null : cat(parts[0], sample, parts[1]);
    }


    private static CborRule inAuxiliaryData(String name, Decoder strict, String prefix, String suffix) {
        Wrapper wrapper = placedInTransaction(name, cbor -> tx(hex(MIN_BODY), hex("a0"), cat(prefix, cbor, suffix)));
        return kept(name, orIn(strict, inTransaction(wrapper)), cbor -> Transaction.deserialize(wrapper.wrap(cbor)));
    }

    /**
     * The strict reading of a cost model's key, narrowed to Haskell's {@code Language} by value: a head of any width
     * ({@code 1802}) is the same language, as {@code decodeEnumBounded} reads it.
     */
    private static Object strictLanguage(byte[] cbor) throws Exception {
        Object read = reads(FieldType.LANGUAGE).decode(cbor);
        DataItem key = item(cbor);
        if (!(key instanceof UnsignedInteger)
                || ((UnsignedInteger) key).getValue().compareTo(BigInteger.valueOf(MAX_LANGUAGE)) > 0) {
            throw notOfThisRule();
        }
        return read;
    }

    /**
     * A deliberate restriction of a dataset rule in the harness: a rejection (ADR 0005 D10), as Haskell's decoder of
     * the narrower type would reject the item.
     */
    private static RuleRestriction notOfThisRule() {
        return new RuleRestriction("not of this rule's form");
    }

    /** A cost model keeps a key it does not know, as Haskell's does, but {@code Language} has only the known ones. */
    private static CostMdls language(byte[] cbor) throws Exception {
        BigInteger key = ((UnsignedInteger) item(cbor)).getValue();
        if (Stream.of(Language.values()).noneMatch(language -> BigInteger.valueOf(language.getKey()).equals(key))) {
            throw new CborDeserializationException("unknown language " + key);
        }
        return CostMdls.deserialize(item(cat("a1", cbor, "80")));
    }

    /**
     * A rule narrower than the type the strict read takes it as, such as a nullable position, another form of auxiliary data or
     * another alternative of {@code plutus_data}: the type's reader would accept that item, but it is not one of
     * this rule's.
     */
    private static CborRule only(int majorType, CborRule rule) {
        return only(head -> head >>> 5 == majorType, rule);
    }

    private static CborRule only(Predicate<Integer> head, CborRule rule) {
        CborRule.Decoder strict = rule.strict();
        return new CborRule(rule.name(), rule.exact(), cbor -> {
            if (cbor.length == 0 || !head.test(cbor[0] & 0xff)) {
                throw notOfThisRule();
            }
            return strict.decode(cbor);
        }, rule.model(), rule.encoder());
    }

    /** The same decoders under a second rule name: the dataset tests one Haskell type under both. */
    private static CborRule sameAs(String name, CborRule same) {
        return new CborRule(name, same.exact(), same.strict(), same.model(), same.encoder());
    }

    private static List<MultiAsset> multiAssets(Value value) {
        return value.getMultiAssets() != null ? value.getMultiAssets() : List.of();
    }

    private static byte[] addressOutput(byte[] address) {
        return cat("a200", address, "0100");
    }

    /** @return the item at the path: an int is an array index or an integer map key, or {@link #KEY}, {@link #VALUE} */
    private static DataItem at(DataItem root, Object... path) {
        DataItem item = root;
        for (Object step : path) {
            if (item instanceof Array) {
                item = ((Array) item).getDataItems().get((Integer) step);
            } else if (item instanceof co.nstant.in.cbor.model.Map) {
                co.nstant.in.cbor.model.Map map = (co.nstant.in.cbor.model.Map) item;
                if (step == KEY) {
                    item = map.getKeys().iterator().next();
                } else if (step == VALUE) {
                    item = map.getValues().iterator().next();
                } else {
                    item = map.get(new UnsignedInteger((Integer) step));
                }
            } else {
                throw new IllegalArgumentException("no " + step + " in " + item);
            }
        }
        return item;
    }
}
