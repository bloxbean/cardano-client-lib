package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A transaction as received, read from its original bytes: every accessor returns spans of the caller's buffer, and
 * every hash is taken from the bytes the ledger hashes, never from a re-encoded model (ADR 0001 D2, D3). Reading is
 * lazy and iterative, so content of any nesting depth is handled on any thread; the unbounded types (Plutus data, native
 * scripts) stay spans here, and the models are built from them by {@code RawModels} in {@code transaction-spec}.
 * <p>
 * Accepts the submission form {@code [body, witnesses, aux / null]} (any era; valid), the Alonzo form
 * {@code [body, witnesses, isValid, aux / null]}, and the form of a transaction in a Dijkstra block,
 * {@code [body, witnesses, aux / null, isValid]}; the two forms of four items are told apart by which item is a CBOR
 * boolean, as aux data never is. A view is era-agnostic: records are read by key, unknown keys are passed
 * through, tag 258 is accepted on sets, and both redeemer forms and every aux data shape are read. Only
 * {@link #scriptDataHash(Era, byte[])} and the typed fields of {@link #body(Era)}, {@link #witnessSet(Era)} and
 * {@link #auxData(Era)} depend on the era. A record with a
 * repeated key is rejected, as the ledger rejects it: the body, the witness set and the tag-259 aux data when the view
 * is created, and an output in map form (and the collateral return) when it is read with {@link #outputs()} or
 * {@link #collateralReturn()}.
 * <p>
 * {@link #lenient(byte[])} reads what the models of {@code transaction-spec} have always accepted, for them to map.
 * <p>
 * Byron transactions are not supported.
 */
public final class RawTx {
    private static final int AUX_POSITION = 2;
    private static final int FLAG_LAST = 3;
    private static final int WITHOUT_FLAG_ITEMS = 3;

    private final CborSpan span;
    private final CborSpan body;
    private final CborSpan witnessSet;
    private final List<Map.Entry<CborSpan, CborSpan>> bodyFields;
    private final List<Map.Entry<CborSpan, CborSpan>> witnessFields;
    private final CborSpan auxData;
    private final List<Map.Entry<CborSpan, CborSpan>> auxFields;
    private final boolean valid;
    private final boolean validityFlag;
    private final boolean lenient;

    private RawTx(CborSpan span, CborSpan body, CborSpan witnessSet, CborSpan auxData, boolean valid, boolean validityFlag) {
        this.span = span;
        this.body = body;
        this.witnessSet = witnessSet;
        this.bodyFields = record(body, "transaction body");
        this.witnessFields = record(witnessSet, "witness set");
        this.auxData = auxData;
        this.auxFields = auxData != null && auxData.tag() == AuxDataField.RECORD.tag()
                ? record(auxData.untag(), "aux data") : null;
        this.valid = valid;
        this.validityFlag = validityFlag;
        this.lenient = false;
    }

    private RawTx(CborSpan span, List<CborSpan> items) {
        this.span = span;
        this.body = items.get(0);
        this.witnessSet = items.get(1);
        this.bodyFields = RawItems.record(body, "transaction body");
        this.witnessFields = null;
        int flag = flagPosition(items);
        this.auxData = aux(items.get(flag == AUX_POSITION ? FLAG_LAST : AUX_POSITION));
        this.auxFields = null;
        this.validityFlag = flag >= 0;
        this.valid = !validityFlag || items.get(flag).asBoolean();
        this.lenient = true;
    }

    /**
     * @param txCbor a transaction, exactly one CBOR item
     * @return the view
     * @throws CborRuntimeException if the bytes are not well-formed CBOR or not a Shelley-family transaction, or a record
     *                              repeats a key
     */
    public static RawTx of(byte[] txCbor) {
        CborSpan tx = CborSpan.of(txCbor);
        List<CborSpan> items = items(tx);
        int flag = flagPosition(items);
        if (flag < 0)
            return new RawTx(tx, items.get(0), items.get(1), aux(items.get(AUX_POSITION)), true, false);
        CborSpan aux = items.get(flag == AUX_POSITION ? FLAG_LAST : AUX_POSITION);
        return new RawTx(tx, items.get(0), items.get(1), aux(aux), items.get(flag).asBoolean(), true);
    }

    // A sub-transaction of a Dijkstra body, [body, witnesses, aux / null], which has no flag; read as lenient(byte[])
    // reads a transaction when the body is lenient.
    static RawTx subTransaction(CborSpan tx, boolean lenient) {
        List<CborSpan> items = RawItems.array(tx, "sub_transaction");
        if (items.size() != WITHOUT_FLAG_ITEMS)
            throw error("A sub-transaction has " + WITHOUT_FLAG_ITEMS + " items, found " + items.size(), tx);
        return lenient ? new RawTx(tx, items)
                : new RawTx(tx, items.get(0), items.get(1), aux(items.get(AUX_POSITION)), true, false);
    }

    /**
     * The position of a transaction's validity flag: the third item, the Alonzo form, unless only the fourth is a CBOR
     * boolean, the form of a transaction in a Dijkstra block; or -1 if it has three items.
     */
    private static int flagPosition(List<CborSpan> items) {
        if (items.size() == WITHOUT_FLAG_ITEMS)
            return -1;
        return !isBoolean(items.get(AUX_POSITION)) && isBoolean(items.get(FLAG_LAST)) ? FLAG_LAST : AUX_POSITION;
    }

    // An untagged CBOR true or false.
    static boolean isBoolean(CborSpan item) {
        int initial = item.buffer()[item.offset()] & 0xff;
        return item.length() == 1 && (initial == 0xf4 || initial == 0xf5);
    }

    /**
     * Reads a transaction as the models always have (ADR 0003 D7): a tag on the transaction or on a record is looked
     * through, and a key of any kind may not repeat in the body. The witness set and the aux data are read when they
     * are asked for. What decoding the body to a tree rejects is rejected here too, and for the witness set and the aux
     * data when they are read, so a model mapped from this view accepts what the tree did.
     *
     * @param txCbor a transaction, exactly one CBOR item
     * @return the view
     * @throws CborRuntimeException if the bytes are not well-formed CBOR, or not an array of 3 or 4 items whose first
     *                              is a map that repeats no key
     */
    public static RawTx lenient(byte[] txCbor) {
        CborSpan tx = CborSpan.of(txCbor);
        List<CborSpan> items = checkedItems(RawItems.array(tx, "transaction"), tx);
        // what is read from spans here: the witness set and the aux data are checked when they are read
        CborSerializationUtil.checkTags(tx.isTagged() ? tx : items.get(0));
        return new RawTx(tx, items);
    }

    /**
     * Walks the transaction with a policy (ADR 0003 D6): its own array, the body, the witness set, the validity flag
     * and the aux data. A view rejects a record that repeats a key when it is created, and a policy must see that
     * entry, so a walk takes the bytes. The form {@code [body, witnesses, aux / null]} has its rows to Mary and from
     * Dijkstra on, the form with the flag third from Alonzo on, and the form with the flag last, a transaction in a
     * Dijkstra block, from Dijkstra on: outside them, {@link FieldSpec#validIn(Era)} tells the policy. A Dijkstra
     * body's sub-transactions are walked with the body table's rows that a sub-transaction body may have
     * ({@link TxBodyField#SUB_RECORD}).
     *
     * @param txCbor a transaction, exactly one CBOR item
     * @param era    the era to read it in
     * @param policy the policy, which throws to reject
     * @throws CborRuntimeException if the bytes are not well-formed CBOR or not a Shelley-family transaction, or the
     *                              policy accepts a value the walk cannot enter
     */
    public static void walk(byte[] txCbor, Era era, FormatPolicy policy) {
        walk(CborSpan.of(txCbor), era, policy);
    }

    /**
     * Walks a transaction as {@link #walk(byte[], Era, FormatPolicy)} does. Given the {@link #span()} of a view, the
     * walk lists each container once for both: a span keeps its listing.
     *
     * @param tx     a transaction, as encoded
     * @param era    the era to read it in
     * @param policy the policy, which throws to reject
     * @throws CborRuntimeException if it is not a Shelley-family transaction, or the policy accepts a value the walk
     *                              cannot enter
     */
    public static void walk(CborSpan tx, Era era, FormatPolicy policy) {
        List<CborSpan> items = items(tx);
        RecordSpec form = items.size() == WITHOUT_FLAG_ITEMS ? Forms.WITHOUT_FLAG
                : flagPosition(items) == FLAG_LAST ? Forms.FLAG_LAST : Forms.WITH_FLAG;
        form.walk(tx, era, policy);
    }

    // The tables of the transaction's own array, by form: without a flag (to Mary, and again from Dijkstra), with the
    // flag third (from Alonzo) and with it last (from Dijkstra). A holder of its own, so that nothing reading a table
    // initializes RawTx.
    private static final class Forms {
        static final RecordSpec WITHOUT_FLAG = RecordSpec.positional("pre_alonzo_transaction", List.of(
                FieldSpec.of(0, FieldType.TRANSACTION_BODY, true, Era.Shelley, Era.Mary, TxBodyField.RECORD),
                FieldSpec.of(1, FieldType.TRANSACTION_WITNESS_SET, true, Era.Shelley, Era.Mary, WitnessSetField.RECORD),
                FieldSpec.of(2, FieldType.OPTIONAL_AUXILIARY_DATA, true, Era.Shelley, Era.Mary),
                FieldSpec.of(0, FieldType.TRANSACTION_BODY, true, Era.Dijkstra, null, TxBodyField.RECORD),
                FieldSpec.of(1, FieldType.TRANSACTION_WITNESS_SET, true, Era.Dijkstra, null, WitnessSetField.RECORD),
                FieldSpec.of(2, FieldType.OPTIONAL_AUXILIARY_DATA, true, Era.Dijkstra, null)));
        static final RecordSpec WITH_FLAG = RecordSpec.positional("transaction", List.of(
                FieldSpec.of(0, FieldType.TRANSACTION_BODY, true, Era.Alonzo, null, TxBodyField.RECORD),
                FieldSpec.of(1, FieldType.TRANSACTION_WITNESS_SET, true, Era.Alonzo, null, WitnessSetField.RECORD),
                FieldSpec.of(2, FieldType.BOOL, true, Era.Alonzo, null),
                FieldSpec.of(3, FieldType.OPTIONAL_AUXILIARY_DATA, true, Era.Alonzo, null)));
        static final RecordSpec FLAG_LAST = RecordSpec.positional("block_transaction", List.of(
                FieldSpec.of(0, FieldType.TRANSACTION_BODY, true, Era.Dijkstra, null, TxBodyField.RECORD),
                FieldSpec.of(1, FieldType.TRANSACTION_WITNESS_SET, true, Era.Dijkstra, null, WitnessSetField.RECORD),
                FieldSpec.of(2, FieldType.OPTIONAL_AUXILIARY_DATA, true, Era.Dijkstra, null),
                FieldSpec.of(3, FieldType.BOOL, true, Era.Dijkstra, null)));
    }

    // The items of a transaction: [body, witnesses, aux / null], [body, witnesses, isValid, aux / null] or
    // [body, witnesses, aux / null, isValid].
    private static List<CborSpan> items(CborSpan tx) {
        if (tx.majorType() != 4 || tx.tag() != -1)
            throw error("A transaction is an untagged array", tx);
        return checkedItems(tx.items(), tx);
    }

    private static List<CborSpan> checkedItems(List<CborSpan> items, CborSpan tx) {
        if (items.size() == 2)
            throw error("Byron transactions ([tx, witnesses]) are not supported", tx);
        if (items.size() != 3 && items.size() != 4)
            throw error("A transaction has 3 or 4 items, found " + items.size(), tx);
        return items;
    }

    // A transaction of a block, from the block's parallel arrays or a Dijkstra block's transaction; with no validity
    // flag, assemble() writes the form without one.
    static RawTx inBlock(CborSpan body, CborSpan witnessSet, CborSpan auxData, boolean valid, boolean validityFlag) {
        return new RawTx(null, body, witnessSet, auxData, valid, validityFlag);
    }

    private static CborSpan aux(CborSpan item) {
        return item.isNull() ? null : item;
    }

    /**
     * The entries of a CDDL record: an untagged map whose unsigned keys are all distinct (D6). {@link CborSpan#field}
     * rejects a record that repeats a key as a whole, whichever key is asked for, as the ledger does; the entries are
     * then looked up without walking the map again.
     */
    static List<Map.Entry<CborSpan, CborSpan>> record(CborSpan span, String what) {
        if (span.majorType() != 5 || span.tag() != -1)
            throw error("The " + what + " is an untagged map", span);
        span.field(0);
        return span.entries();
    }

    // The value of an unsigned key in a record's entries. Keys are compared as unsigned 64-bit values, so an unknown key
    // of 2^63 or more is passed over like any other unknown key.
    static Optional<CborSpan> field(List<Map.Entry<CborSpan, CborSpan>> record, long key) {
        // by index: a view looks up each of its fields, and an iterator for each was a lookup's main cost
        for (int i = 0; i < record.size(); i++) {
            Map.Entry<CborSpan, CborSpan> entry = record.get(i);
            CborSpan candidate = entry.getKey();
            // the usual key is one byte, which is the key itself if it is an unsigned integer below 24
            if (candidate.length() == 1) {
                if (key < 24 && (candidate.buffer()[candidate.offset()] & 0xff) == key)
                    return Optional.of(entry.getValue());
                continue;
            }
            if (candidate.majorType() != 0 || candidate.isTagged())
                continue;
            // an 8-byte argument may not fit a long
            boolean matches = candidate.length() == 9 ? candidate.asBigInteger().equals(BigInteger.valueOf(key))
                    : candidate.asLong() == key;
            if (matches)
                return Optional.of(entry.getValue());
        }
        return Optional.empty();
    }

    // The two items of an untagged array [a, b], such as a datum option or a script reference.
    static List<CborSpan> pair(CborSpan item, String what) {
        if (item.majorType() != 4 || item.tag() != -1 || item.size() != 2)
            throw error(what + " is an array of 2 items", item);
        return item.items();
    }

    // A small unsigned integer, such as a script type.
    static int smallInt(CborSpan item) {
        long value = item.asLong();
        if (value < 0 || value > Integer.MAX_VALUE)
            throw error("Expected a small unsigned integer, found " + value, item);
        return (int) value;
    }

    static CborRuntimeException error(String reason, CborSpan at) {
        return new CborRuntimeException(reason + " at offset " + at.offset());
    }

    /**
     * @return the transaction as encoded; for a transaction of a block, the transaction as it was submitted (assembled
     * into a new buffer, see {@link RawBlock#txBytes(int)})
     */
    public CborSpan span() {
        return span != null ? span : CborSpan.of(assemble());
    }

    byte[] assemble() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.length() + witnessSet.length() + 16);
        out.write(validityFlag ? 0x84 : 0x83);
        out.writeBytes(body.bytes());
        out.writeBytes(witnessSet.bytes());
        if (validityFlag)
            out.write(valid ? 0xf5 : 0xf4);
        out.writeBytes(auxData != null ? auxData.bytes() : new byte[]{(byte) 0xf6});
        return out.toByteArray();
    }

    public CborSpan body() {
        return body;
    }

    /**
     * @param era the era to read the body in
     * @return the body's typed fields
     * @throws IllegalArgumentException if the era is null
     */
    public RawTxBody body(Era era) {
        return new RawTxBody(body, bodyFields, era, lenient);
    }

    public CborSpan witnessSet() {
        return witnessSet;
    }

    /**
     * @param era the era to read the witness set in
     * @return the witness set's typed fields
     * @throws IllegalArgumentException if the era is null
     * @throws CborRuntimeException     if this view is {@link #lenient(byte[])} and the witness set is not a map that
     *                                  repeats no key
     */
    public RawWitnessSet witnessSet(Era era) {
        if (!lenient)
            return new RawWitnessSet(witnessSet, witnessFields, era, false);
        CborSerializationUtil.checkTags(witnessSet);
        return RawWitnessSet.lenient(witnessSet, era);
    }

    /**
     * @return the aux data in any shape (a metadata map, {@code [metadata, scripts]} or {@code #6.259({...})}), or empty
     * if it is null
     */
    public Optional<CborSpan> auxData() {
        return Optional.ofNullable(auxData);
    }

    /**
     * @param era the era to read the aux data in
     * @return the aux data's typed fields, or empty if it is null
     * @throws IllegalArgumentException if the era is null
     * @throws CborRuntimeException     if it is none of the forms
     */
    public Optional<RawAuxData> auxData(Era era) {
        if (auxData == null)
            return Optional.empty();
        if (!lenient)
            return Optional.of(RawAuxData.of(auxData, auxFields, era));
        CborSerializationUtil.checkTags(auxData);
        return Optional.of(RawAuxData.lenient(auxData, era));
    }

    /**
     * @return false when phase-2 validation failed: the {@code isValid} flag, or for a transaction of a block, its index is
     * in {@code invalid_transactions} or its Dijkstra flag; true before Alonzo and for a sub-transaction
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * @return the transaction id, {@code blake2b256} of the body as encoded
     */
    public byte[] txId() {
        return Blake2bUtil.blake2bHash256(body.bytes());
    }

    /**
     * @return the aux data hash, {@code blake2b256} of the aux data as encoded, whatever its shape; empty without aux data
     */
    public Optional<byte[]> auxDataHash() {
        return auxData().map(aux -> Blake2bUtil.blake2bHash256(aux.bytes()));
    }

    /**
     * The script integrity hash (body field 11) of this transaction, from its original bytes:
     * {@code blake2b256(redeemers ‖ datums ‖ languageViews)} (alonzo {@code Tx.hs}).
     * <ul>
     *     <li>redeemers: witness field 5 as encoded, even when empty; when absent, the era's empty encoding ({@code 80}
     *     until Babbage, {@code a0} in Conway);</li>
     *     <li>datums: witness field 4 as encoded, with its set tag, when it has an entry; an absent or empty field adds
     *     nothing;</li>
     *     <li>languageViews: the language views encoding for exactly the Plutus languages the transaction runs, from
     *     witness scripts and from reference scripts in the UTxO, which this view cannot know. Build it with
     *     {@code CostMdls.getLanguageViewEncoding()} (or {@code CostModelUtil.getLanguageViewsEncoding}) for those
     *     languages, or pass {@code a0} for none.</li>
     * </ul>
     *
     * @param era           the transaction's era, Alonzo or later
     * @param languageViews the language views encoding
     * @return the hash, or empty when the redeemers, the datums and the language views are all empty
     * @throws IllegalArgumentException if the era is before Alonzo, which has no script integrity hash
     */
    public Optional<byte[]> scriptDataHash(Era era, byte[] languageViews) {
        if (era == null || languageViews == null)
            throw new IllegalArgumentException("era and languageViews are required (a0 for no language views)");
        byte[] absentRedeemers = EraSpec.of(era).absentRedeemers();
        Optional<CborSpan> redeemers = witnessField(WitnessSetField.REDEEMERS_FIELD.key());
        Optional<CborSpan> datums = witnessField(WitnessSetField.PLUTUS_DATA.key());
        boolean noRedeemers = redeemers.map(field -> field.size() == 0).orElse(true);
        boolean noDatums = datums.map(field -> field.untagIf(258).size() == 0).orElse(true);
        boolean noLanguages = CborSpan.of(languageViews).size() == 0;
        if (noRedeemers && noDatums && noLanguages)
            return Optional.empty();
        ByteArrayOutputStream preimage = new ByteArrayOutputStream();
        preimage.writeBytes(redeemers.map(CborSpan::bytes).orElse(absentRedeemers));
        if (!noDatums)
            preimage.writeBytes(datums.get().bytes());
        preimage.writeBytes(languageViews);
        return Optional.of(Blake2bUtil.blake2bHash256(preimage.toByteArray()));
    }

    /**
     * @param key a body field key
     * @return the field as encoded, or empty if absent
     */
    public Optional<CborSpan> bodyField(int key) {
        return field(bodyFields, key);
    }

    /**
     * @param key a witness set field key
     * @return the field as encoded, or empty if absent
     */
    public Optional<CborSpan> witnessField(int key) {
        return witnessField((long) key);
    }

    private Optional<CborSpan> witnessField(long key) {
        return field(witnessFields != null ? witnessFields : RawItems.map(witnessSet, "witness set"), key);
    }

    /**
     * @return the outputs (body field 1), legacy and map form alike
     */
    public List<RawOutput> outputs() {
        List<RawOutput> outputs = new ArrayList<>();
        bodyField(1).ifPresent(field -> field.items().forEach(output -> outputs.add(RawOutput.of(output))));
        return outputs;
    }

    /**
     * @return the collateral return (body field 16); its output index is {@code outputs().size()}
     */
    public Optional<RawOutput> collateralReturn() {
        return bodyField(16).map(RawOutput::of);
    }

    /**
     * @return the witness datums (witness field 4), every entry in encoded order, read in Conway
     */
    public List<RawDatum> witnessDatums() {
        return conwayWitnessSet().datums().orElse(List.of());
    }

    /**
     * @return the redeemers (witness field 5), from the array form or the Conway map form, in encoded order with any
     * repeated key kept (the ledger keeps the last value of a repeated key), read in Conway
     */
    public List<RawRedeemer> redeemers() {
        return conwayWitnessSet().redeemers().orElse(List.of());
    }

    /**
     * @return the witness scripts: native (witness field 1), Plutus V1 (3), V2 (6) and V3 (7), read in Conway; read a
     * Dijkstra witness set with {@link #witnessSet(Era)} for its Plutus V4 scripts
     */
    public List<RawScript> scripts() {
        return conwayWitnessSet().scripts();
    }

    // For the accessors that take no era, which read as Conway does.
    private RawWitnessSet conwayWitnessSet() {
        return witnessSet(Era.Conway);
    }

    /**
     * @return the aux data scripts: native scripts of {@code [metadata, scripts]}, and native and Plutus V1-V3 scripts of
     * {@code #6.259({...})} (keys 1-4); none for a metadata map. Read in Conway: read Dijkstra aux data with
     * {@link #auxData(Era)} for its Plutus V4 scripts
     */
    public List<RawScript> auxScripts() {
        return auxData(Era.Conway).map(RawAuxData::scripts).orElse(List.of());
    }

    /**
     * @return the vkey witnesses {@code [vkey, signature]} (witness field 0)
     */
    public List<CborSpan> vkeyWitnesses() {
        return witnessSetItems((int) WitnessSetField.VKEY_WITNESSES.key());
    }

    /**
     * @return the bootstrap witnesses {@code [vkey, signature, chain_code, attributes]} (witness field 2)
     */
    public List<CborSpan> bootstrapWitnesses() {
        return witnessSetItems((int) WitnessSetField.BOOTSTRAP_WITNESSES.key());
    }

    /**
     * The items of a body field that is a set (in Conway CDDL {@code set<a>} or {@code nonempty_set<a>}), with or
     * without its tag 258 at any width: inputs (0), certificates (4), collateral inputs (13), required signers (14),
     * reference inputs (18), proposals (20).
     *
     * @param key the body field key
     * @return the items, none if the field is absent
     * @throws CborRuntimeException if the field is not an array, optionally tagged 258
     */
    public List<CborSpan> bodySetItems(int key) {
        return bodyField(key).map(field -> field.untagIf(258).items()).orElse(List.of());
    }

    /**
     * The items of a witness set field that is a set, with or without its tag 258 at any width: vkey witnesses (0),
     * native scripts (1), bootstrap witnesses (2), Plutus scripts (3, 6, 7), datums (4).
     *
     * @param key the witness set field key
     * @return the items, none if the field is absent
     * @throws CborRuntimeException if the field is not an array, optionally tagged 258
     */
    public List<CborSpan> witnessSetItems(int key) {
        return witnessField(key).map(field -> field.untagIf(258).items()).orElse(List.of());
    }
}
