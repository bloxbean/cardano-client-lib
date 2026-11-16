package com.bloxbean.cardano.client.ledger.conformance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.FieldType;
import com.bloxbean.cardano.client.transaction.raw.HaskellPolicy;
import com.bloxbean.cardano.client.transaction.raw.RawBlock;
import com.bloxbean.cardano.client.transaction.raw.RecordSpec;
import com.bloxbean.cardano.client.transaction.raw.TxBodyField;
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

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How one cardano-cbor-dataset rule is read, decoded by CCL's model and re-encoded (ADR 0005 D5).
 *
 * <p>Whether an item decodes is {@link #strict}'s alone: CCL's reader with {@link HaskellPolicy}, read by the rule's own
 * type or record where it has one, so the gate measures one reader and one policy. A rule whose checks belong to its
 * parent (a collection, a part of a transaction or of a header) is read in a minimal, valid parent.</p>
 *
 * <p>{@link #model} is CCL's model of an accepted item, which {@link #encoder} re-encodes: a model's writer, as
 * Haskell's re-encoding sorts and folds what the original bytes keep. Without an encoder, the rule re-encodes as its
 * original bytes, as Haskell's {@code MemoBytes} does and as a node keeps them. CCL has no model of a block header, so
 * the header rules have none ({@link #model} null): Yano attaches its own decoders to them, as diagnostics.</p>
 *
 * @param name    the dataset rule
 * @param exact   whether the dataset compares the bytes exactly ({@code mkByteExactRule} in its {@code EraSpec.hs});
 *                otherwise both sides are normalized ({@link CborNormalizer})
 * @param strict  the Haskell-strict read; rejects by throwing
 * @param model   CCL's model decoding, or null where CCL has no model; rejects by throwing
 * @param encoder the re-encoding of the model, or null to keep the original bytes
 */
public record CborRule(String name, boolean exact, Decoder strict, Decoder model, Encoder encoder) {

    /** A reading of the bytes of one sample: it returns what it read, or throws. */
    @FunctionalInterface
    public interface Decoder {
        Object decode(byte[] cbor) throws Exception;
    }

    /** The re-encoding of a model. */
    @FunctionalInterface
    public interface Encoder {
        /** @return a {@link DataItem} or the encoded bytes */
        Object encode(Object model) throws Exception;
    }

    static final HaskellPolicy P = HaskellPolicy.CONWAY;

    // Known-good parts of the wrappers.
    static final String HASH28 = "581c" + "00".repeat(28);
    static final String HASH32 = "5820" + "00".repeat(32);
    static final String ADDRESS = "581d60" + "00".repeat(28);
    static final String REWARD_ACCOUNT = "581de0" + "00".repeat(28);
    static final String INPUT = "82" + HASH32 + "00";
    static final String MIN_BODY_ENTRIES = "0081" + INPUT + "0180" + "0200";
    static final String MIN_BODY = "a3" + MIN_BODY_ENTRIES;
    private static final String KES_SIGNATURE = "5901c0" + "00".repeat(448);
    /** A proposal procedure, an item of the body's set of them. */
    private static final RecordSpec PROPOSAL = TxBodyField.PROPOSAL_PROCEDURES.spec().record();

    /**
     * @param model the item's model, as {@link #model} decoded it (null for a rule without a model)
     * @param input the item as encoded
     * @return the re-encoding of an item whose model is {@code model}, or {@code input} for a rule without an encoder.
     *         Not the canonical form, which would sort a Plutus {@code Data} map: that is an association list, whose
     *         order Haskell keeps.
     * @throws Exception if the model cannot be written
     */
    public byte[] encode(Object model, byte[] input) throws Exception {
        if (encoder == null) {
            return input;
        }
        Object encoded = encoder.encode(model);
        return encoded instanceof byte[] ? (byte[]) encoded
                : CborSerializationUtil.serialize((DataItem) encoded, false);
    }

    /** @return the Conway rules this harness knows, by name, sorted */
    public static Map<String, CborRule> conway() {
        Map<String, CborRule> base = base();
        return Collections.unmodifiableMap(Stream.concat(base.values().stream(), CborPlacedRules.all(base))
                .collect(Collectors.toMap(CborRule::name, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("two rules named " + a.name());
                }, TreeMap::new)));
    }

    /** @return the rules for items read as they are, or with one wrapper */
    static Map<String, CborRule> base() {
        return Stream.of(
                hashed("auxiliary_data", reads(FieldType.OPTIONAL_AUXILIARY_DATA),
                        cbor -> Transaction.deserialize(tx(hex(MIN_BODY), hex("a0"), cbor))),
                kept("block", P::block, CborRule::blockModel),
                model("certificate", reads(CertKind.RECORD), cbor -> Certificate.deserialize((Array) item(cbor)),
                        c -> ((Certificate) c).serialize()),
                model("cost_models", reads(FieldType.COST_MODELS), cbor -> CostMdls.deserialize(item(cbor)),
                        c -> ((CostMdls) c).serialize()),
                model("credential", reads(FieldType.CREDENTIAL),
                        cbor -> StakeCredential.deserialize((Array) item(cbor)),
                        c -> ((StakeCredential) c).serialize()),
                kept("datum_option", reads(FieldType.DATUM_OPTION),
                        cbor -> TransactionOutput.deserialize(item(datumOutput(cbor)))),
                model("drep", reads(FieldType.DREP), cbor -> DRep.deserialize(item(cbor)), c -> ((DRep) c).serialize()),
                model("gov_action", reads(FieldType.GOV_ACTION), cbor -> GovAction.deserialize((Array) item(cbor)),
                        c -> ((GovAction) c).serialize()),
                hashed("header", HaskellPolicy::header, null),
                kept("header_body", cbor -> HaskellPolicy.header(headerOf(cbor)), null),
                hashed("native_script", reads(FieldType.NATIVE_SCRIPT),
                        cbor -> NativeScript.deserialize((Array) item(cbor))),
                hashed("plutus_data", reads(FieldType.PLUTUS_DATA), PlutusData::deserialize),
                model("proposal_procedure", reads(PROPOSAL), cbor -> ProposalProcedure.deserialize(item(cbor)),
                        c -> ((ProposalProcedure) c).serialize()),
                model("protocol_param_update", reads(FieldType.PROTOCOL_PARAM_UPDATE),
                        cbor -> ProtocolParamUpdate.deserialize(item(cbor)),
                        c -> ((ProtocolParamUpdate) c).serialize()),
                model("redeemer", inTransaction(CborRule::redeemerTx),
                        cbor -> Redeemer.deserializePreConway((Array) item(cbor)),
                        c -> ((Redeemer) c).serializePreConway()),
                inTransaction("redeemers", cbor -> tx(hex(MIN_BODY), cat("a105", cbor), null)),
                model("relay", reads(FieldType.RELAY_LIST.record()), CborRule::relay, c -> ((Relay) c).serialize()),
                model("script", P::script, CborRule::scriptModel,
                        c -> c instanceof NativeScript ? ((NativeScript) c).scriptRefBytes()
                                : ((PlutusScript) c).scriptRefBytes()),
                kept("transaction", P::transaction, Transaction::deserialize),
                inTransaction("transaction_body", cbor -> tx(cbor, hex("a0"), null)),
                model("transaction_input", reads(TxBodyField.INPUTS.spec().record()),
                        cbor -> TransactionInput.deserialize((Array) item(cbor)),
                        c -> ((TransactionInput) c).serialize()),
                model("transaction_output", reads(FieldType.TRANSACTION_OUTPUT),
                        cbor -> TransactionOutput.deserialize(item(cbor)),
                        c -> ((TransactionOutput) c).serializeInLedgerOrder()),
                inTransaction("transaction_witness_set", cbor -> tx(hex(MIN_BODY), cbor, null)),
                model("value", reads(FieldType.VALUE), cbor -> Value.deserialize(item(cbor)),
                        c -> ((Value) c).serializeInLedgerOrder()),
                model("voting_procedure", reads(FieldType.VOTING_PROCEDURE),
                        cbor -> VotingProcedure.deserialize((Array) item(cbor)), c -> ((VotingProcedure) c).serialize())
        ).collect(Collectors.toMap(CborRule::name, Function.identity()));
    }

    static CborRule model(String name, Decoder strict, Decoder model, Encoder encoder) {
        return new CborRule(name, false, strict, model, encoder);
    }

    /** The dataset compares the bytes exactly, and a node keeps them. */
    private static CborRule hashed(String name, Decoder strict, Decoder model) {
        return new CborRule(name, true, strict, model, null);
    }

    /** A node keeps the bytes, and the dataset compares them normalized. */
    static CborRule kept(String name, Decoder strict, Decoder model) {
        return new CborRule(name, false, strict, model, null);
    }

    /** A hashed rule read inside (or as) a transaction: the strict read and CCL's model of that transaction. */
    private static CborRule inTransaction(String name, Wrapper wrapper) {
        return hashed(name, inTransaction(wrapper), cbor -> Transaction.deserialize(wrapper.wrap(cbor)));
    }

    /** The strict read of a transaction, which a rule whose checks are a transaction's is placed in. */
    static Decoder inTransaction(Wrapper wrapper) {
        return cbor -> P.transaction(wrapper.wrap(cbor));
    }

    /** The reader of a rule that is one type: {@link HaskellPolicy} on CCL's walk of it. */
    static Decoder reads(FieldType type) {
        return cbor -> {
            P.walk(type, cbor);
            return null;
        };
    }

    /** The reader of a rule that is one record of CCL's tables. */
    static Decoder reads(RecordSpec record) {
        return cbor -> {
            P.walk(record, cbor);
            return null;
        };
    }

    /** Wraps a sample into its parent. */
    @FunctionalInterface
    interface Wrapper {
        byte[] wrap(byte[] cbor);
    }

    /** CCL reads a relay only inside a pool registration. */
    static Relay relay(byte[] relay) throws Exception {
        return PoolRegistration.deserialize(item(cat("8a03" + HASH28 + HASH32 + "0000d81e820001" + REWARD_ACCOUNT
                + "8081", relay, "f6"))).getRelays().get(0);
    }

    /** A script reference's script, by CCL's model of its language. */
    private static Object scriptModel(byte[] cbor) throws Exception {
        return ((Array) item(cbor)).getDataItems().get(0).equals(new UnsignedInteger(0))
                ? NativeScript.deserializeScriptRef(cbor) : PlutusScript.deserializeScriptRef(cbor);
    }

    /** A Conway block as CCL's models read one: the raw view, then every transaction's model. */
    private static Object blockModel(byte[] cbor) throws Exception {
        RawBlock block = RawBlock.of(cbor, Era.Conway);
        for (int i = 0; i < block.txCount(); i++) {
            Transaction.deserialize(block.txBytes(i));
        }
        return block;
    }

    static byte[] datumOutput(byte[] datumOption) {
        return cat("a300" + ADDRESS + "0100" + "02", datumOption);
    }

    static byte[] redeemerTx(byte[] redeemer) {
        return tx(hex(MIN_BODY), cat("a10581", redeemer), null);
    }

    /**
     * For a caller's own header decoders (Yano's, as diagnostics): the header a {@code header_body} sample, or a part
     * of one, is read in.
     *
     * @param rule   {@code header_body}, or a header body part: {@code block_number}, {@code kes_period},
     *               {@code kes_vkey}, {@code operational_cert} or {@code vrf_vkey}
     * @param sample the sample
     * @return the header, {@code [header_body, kes_signature]}, or null for any other rule
     */
    public static byte[] headerFor(String rule, byte[] sample) {
        if (rule.equals("header_body")) {
            return headerOf(sample);
        }
        byte[] body = CborPlacedRules.headerBodyOf(rule, sample);
        return body == null ? null : headerOf(body);
    }

    /** {@code [header_body, kes_signature]} */
    static byte[] headerOf(byte[] headerBody) {
        return cat(hex("82"), headerBody, hex(KES_SIGNATURE));
    }

    static DataItem item(byte[] cbor) {
        return CborSerializationUtil.deserialize(cbor);
    }

    /** {@code [body, witness_set, true, auxiliary_data / null]} */
    static byte[] tx(byte[] body, byte[] witnessSet, byte[] auxData) {
        return cat(hex("84"), body, witnessSet, auxData == null ? hex("f5f6") : cat(hex("f5"), auxData));
    }

    static byte[] cat(String prefix, byte[] item) {
        return cat(hex(prefix), item);
    }

    static byte[] cat(String prefix, byte[] item, String suffix) {
        return cat(hex(prefix), item, hex(suffix));
    }

    static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
