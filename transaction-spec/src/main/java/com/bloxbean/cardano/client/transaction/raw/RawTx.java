package com.bloxbean.cardano.client.transaction.raw;

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
 * scripts) are only decoded on request ({@link RawDatum#toPlutusData()}, {@link RawScript#toScript()}).
 * <p>
 * Accepts the submission form {@code [body, witnesses, aux / null]} (any era; valid) and the Alonzo form
 * {@code [body, witnesses, isValid, aux / null]}. A view is era-agnostic: records are read by key, unknown keys are passed
 * through, tag 258 is accepted on sets, and both redeemer forms and every aux data shape are read. Only
 * {@link #scriptDataHash(Era, byte[])} depends on the era. A record with a repeated key is rejected, as the ledger
 * rejects it: the body, the witness set and the tag-259 aux data when the view is created, and an output in map form
 * (and the collateral return) when it is read with {@link #outputs()} or {@link #collateralReturn()}.
 * <p>
 * Byron transactions are not supported.
 */
public final class RawTx {
    private static final int NATIVE = 0;

    private final CborSpan span;
    private final CborSpan body;
    private final CborSpan witnessSet;
    private final List<Map.Entry<CborSpan, CborSpan>> bodyFields;
    private final List<Map.Entry<CborSpan, CborSpan>> witnessFields;
    private final CborSpan auxData;
    private final List<Map.Entry<CborSpan, CborSpan>> auxFields;
    private final boolean valid;
    private final boolean validityFlag;

    private RawTx(CborSpan span, CborSpan body, CborSpan witnessSet, CborSpan auxData, boolean valid, boolean validityFlag) {
        this.span = span;
        this.body = body;
        this.witnessSet = witnessSet;
        this.bodyFields = record(body, "transaction body");
        this.witnessFields = record(witnessSet, "witness set");
        this.auxData = auxData;
        this.auxFields = auxData != null && auxData.tag() == 259 ? record(auxData.untag(), "aux data") : null;
        this.valid = valid;
        this.validityFlag = validityFlag;
    }

    /**
     * @param txCbor a transaction, exactly one CBOR item
     * @return the view
     * @throws CborRuntimeException if the bytes are not well-formed CBOR or not a Shelley-family transaction, or a record
     *                              repeats a key
     */
    public static RawTx of(byte[] txCbor) {
        CborSpan tx = CborSpan.of(txCbor);
        if (tx.majorType() != 4 || tx.tag() != -1)
            throw error("A transaction is an untagged array", tx);
        List<CborSpan> items = tx.items();
        switch (items.size()) {
            case 3:
                return new RawTx(tx, items.get(0), items.get(1), aux(items.get(2)), true, false);
            case 4:
                return new RawTx(tx, items.get(0), items.get(1), aux(items.get(3)), items.get(2).asBoolean(), true);
            case 2:
                throw error("Byron transactions ([tx, witnesses]) are not supported", tx);
            default:
                throw error("A transaction has 3 or 4 items, found " + items.size(), tx);
        }
    }

    // A transaction of a block, from the block's parallel arrays.
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
        for (Map.Entry<CborSpan, CborSpan> entry : record) {
            CborSpan candidate = entry.getKey();
            if (candidate.majorType() != 0 || candidate.tag() != -1)
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

    // A small unsigned integer, such as a redeemer tag or a script type.
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

    public CborSpan witnessSet() {
        return witnessSet;
    }

    /**
     * @return the aux data in any shape (a metadata map, {@code [metadata, scripts]} or {@code #6.259({...})}), or empty
     * if it is null
     */
    public Optional<CborSpan> auxData() {
        return Optional.ofNullable(auxData);
    }

    /**
     * @return false when phase-2 validation failed: the {@code isValid} flag, or for a transaction of a block, its index is
     * in {@code invalid_transactions}; true before Alonzo
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
        byte[] absentRedeemers = EraRules.of(era).absentRedeemers();
        Optional<CborSpan> redeemers = witnessField(5);
        Optional<CborSpan> datums = witnessField(4);
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
        return field(witnessFields, key);
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
     * @return the witness datums (witness field 4), every entry in encoded order
     */
    public List<RawDatum> witnessDatums() {
        List<RawDatum> datums = new ArrayList<>();
        witnessSetItems(4).forEach(datum -> datums.add(new RawDatum(datum)));
        return datums;
    }

    /**
     * @return the redeemers (witness field 5), from the array form or the Conway map form, in encoded order with any
     * repeated key kept (the ledger keeps the last value of a repeated key)
     */
    public List<RawRedeemer> redeemers() {
        List<RawRedeemer> redeemers = new ArrayList<>();
        Optional<CborSpan> field = witnessField(5);
        if (field.isEmpty())
            return redeemers;
        if (field.get().majorType() == 4) {
            for (CborSpan redeemer : field.get().items()) {
                List<CborSpan> items = redeemer.items();
                if (items.size() != 4)
                    throw error("A redeemer has 4 items, found " + items.size(), redeemer);
                redeemers.add(new RawRedeemer(smallInt(items.get(0)), items.get(1).asLong(), items.get(2), items.get(3)));
            }
        } else {
            for (Map.Entry<CborSpan, CborSpan> entry : field.get().entries()) {
                List<CborSpan> key = entry.getKey().items();
                List<CborSpan> value = entry.getValue().items();
                if (key.size() != 2 || value.size() != 2)
                    throw error("A redeemer map entry is [tag, index] => [data, ex_units]", entry.getKey());
                redeemers.add(new RawRedeemer(smallInt(key.get(0)), key.get(1).asLong(), value.get(0), value.get(1)));
            }
        }
        return redeemers;
    }

    /**
     * @return the witness scripts: native (witness field 1), Plutus V1 (3), V2 (6) and V3 (7)
     */
    public List<RawScript> scripts() {
        List<RawScript> scripts = new ArrayList<>();
        witnessSetItems(1).forEach(script -> scripts.add(new RawScript(NATIVE, script)));
        witnessSetItems(3).forEach(script -> scripts.add(new RawScript(1, script)));
        witnessSetItems(6).forEach(script -> scripts.add(new RawScript(2, script)));
        witnessSetItems(7).forEach(script -> scripts.add(new RawScript(3, script)));
        return scripts;
    }

    /**
     * @return the aux data scripts: native scripts of {@code [metadata, scripts]}, and native and Plutus V1-V3 scripts of
     * {@code #6.259({...})} (keys 1-4); none for a metadata map
     */
    public List<RawScript> auxScripts() {
        List<RawScript> scripts = new ArrayList<>();
        if (auxData == null || (auxData.majorType() == 5 && auxData.tag() == -1))
            return scripts;
        if (auxData.majorType() == 4 && auxData.tag() == -1) {
            List<CborSpan> items = auxData.items();
            if (items.size() != 2)
                throw error("Aux data [metadata, scripts] has 2 items, found " + items.size(), auxData);
            items.get(1).items().forEach(script -> scripts.add(new RawScript(NATIVE, script)));
            return scripts;
        }
        if (auxData.tag() != 259)
            throw error("Unknown aux data shape", auxData);
        for (int key = 1; key <= 4; key++) {
            int type = key - 1;
            field(auxFields, key).ifPresent(list -> list.items().forEach(script -> scripts.add(new RawScript(type, script))));
        }
        return scripts;
    }

    /**
     * @return the vkey witnesses {@code [vkey, signature]} (witness field 0)
     */
    public List<CborSpan> vkeyWitnesses() {
        return witnessSetItems(0);
    }

    /**
     * @return the bootstrap witnesses {@code [vkey, signature, chain_code, attributes]} (witness field 2)
     */
    public List<CborSpan> bootstrapWitnesses() {
        return witnessSetItems(2);
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
