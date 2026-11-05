package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;

import static com.bloxbean.cardano.client.transaction.raw.RawTx.error;

/**
 * A transaction body read in an era (ADR 0003 D3): {@link TxBodyField} says which key a field has, and a key that means
 * something else in the era reads as absent. A field outside its era is otherwise read like any other: that is the
 * policy's to reject (D5), through {@link RawTx#walk}. Accessors read an item when they are called, and throw
 * {@link CborRuntimeException} if it is not what the table says. Tags on an item are looked through, sets may have tag
 * 258 or not and any array may be indefinite. Hash lengths, ranges and empty sets are not checked.
 * <p>
 * A view from {@link #lenient(CborSpan, Era)} reads what the models of {@code transaction-spec} have always accepted,
 * which D7 keeps until a separate decision changes it. See {@link RawOutput}, {@link RawValue}, {@link RawCertificate},
 * {@link RawUpdate}, {@link RawVotingProcedures} and {@link RawProposal} for what that means for them.
 * {@link #tolerant(CborSpan, Era)} is for a chain follower, which must not lose a block its node has accepted; it reads
 * the update and the governance fields as {@code lenient} does, and the treasury value and the donation as any integer
 * or bignum, or absent if it is no number.
 */
public final class RawTxBody {
    private final CborSpan span;
    private final List<Map.Entry<CborSpan, CborSpan>> fields;
    private final Era era;
    private final boolean lenient;
    private final boolean tolerant;

    RawTxBody(CborSpan span, List<Map.Entry<CborSpan, CborSpan>> fields, Era era, boolean lenient) {
        this(span, fields, era, lenient, false);
    }

    private RawTxBody(CborSpan span, List<Map.Entry<CborSpan, CborSpan>> fields, Era era, boolean lenient,
                      boolean tolerant) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        this.span = span;
        this.fields = fields;
        this.era = era;
        this.lenient = lenient;
        this.tolerant = tolerant;
    }

    /**
     * @param span a body as encoded
     * @param era  the era to read it in
     * @return the view, read as the models read one
     * @throws CborRuntimeException if it is not a map, or it repeats a key
     */
    public static RawTxBody lenient(CborSpan span, Era era) {
        return new RawTxBody(span, RawItems.record(span, "transaction body"), era, true);
    }

    /**
     * Reads a body as {@link #lenient(CborSpan, Era)} does, except that a repeated key means its last value, here and in
     * an output, and that outputs and withdrawals are read with {@link RawOutput#tolerant(CborSpan)} and
     * {@link RawWithdrawal#tolerant(CborSpan)}. What decoding the body to a tree rejects is rejected.
     *
     * @param span a body as encoded
     * @param era  the era to read it in
     * @return the view
     * @throws CborRuntimeException if it is not a map, or decoding it would reject it
     */
    public static RawTxBody tolerant(CborSpan span, Era era) {
        CborSerializationUtil.checkTags(span);
        return new RawTxBody(span, RawItems.lastFirst(span, "transaction body"), era, true, true);
    }

    /**
     * @return the body as encoded
     */
    public CborSpan span() {
        return span;
    }

    public Era era() {
        return era;
    }

    /**
     * @return the inputs
     * @throws CborRuntimeException if the body has none
     */
    public List<RawInput> inputs() {
        return inputs(required(TxBodyField.INPUTS));
    }

    /**
     * @return the outputs
     * @throws CborRuntimeException if the body has none
     */
    public List<RawOutput> outputs() {
        return items(required(TxBodyField.OUTPUTS), "outputs", this::output);
    }

    private RawOutput output(CborSpan output) {
        if (tolerant)
            return RawOutput.tolerant(output);
        return lenient ? RawOutput.lenient(output) : RawOutput.of(output);
    }

    public Optional<BigInteger> fee() {
        return value(TxBodyField.FEE).map(RawItems::unsigned);
    }

    /**
     * @return the time to live, its 64 bits
     */
    public OptionalLong ttl() {
        return number(TxBodyField.TTL);
    }

    /**
     * @return the certificates, none if the field is absent
     */
    public List<RawCertificate> certificates() {
        return value(TxBodyField.CERTIFICATES).map(field -> items(field, "certificates", this::certificate))
                .orElse(List.of());
    }

    /**
     * @return the certificates as encoded, for a caller that reads them itself; none if the field is absent
     */
    public List<CborSpan> certificateItems() {
        return value(TxBodyField.CERTIFICATES).map(field -> RawItems.array(field, "certificates")).orElse(List.of());
    }

    private RawCertificate certificate(CborSpan certificate) {
        if (!lenient)
            return RawCertificate.of(certificate);
        return RawCertificate.lenient(certificate).orElseThrow(() -> error("Unknown certificate tag", certificate));
    }

    /**
     * @return the withdrawals, one per reward account, in the order of their first entries; a repeated account has its
     * last amount, and the span has every entry. None if the field is absent
     */
    public List<RawWithdrawal> withdrawals() {
        return value(TxBodyField.WITHDRAWALS).map(tolerant ? RawWithdrawal::tolerant : RawWithdrawal::read)
                .orElse(List.of());
    }

    /**
     * @return the update of the protocol parameters before Conway, its parameter updates read in this view's era
     */
    public Optional<RawUpdate> update() {
        return value(TxBodyField.UPDATE_PROPOSAL)
                .map(update -> lenient ? RawUpdate.lenient(update, era) : RawUpdate.of(update, era));
    }

    public Optional<byte[]> auxDataHash() {
        return value(TxBodyField.AUX_DATA_HASH).map(RawItems::bytes);
    }

    /**
     * @return the validity interval's start, its 64 bits
     */
    public OptionalLong validityStart() {
        return number(TxBodyField.VALIDITY_START);
    }

    /**
     * @return the mint, one entry per policy and per asset name, as {@link #withdrawals()} has; none if the field is
     * absent
     */
    public List<RawMultiAsset> mint() {
        return value(TxBodyField.MINT).map(field -> RawMultiAsset.read(field, lenient)).orElse(List.of());
    }

    public Optional<byte[]> scriptDataHash() {
        return value(TxBodyField.SCRIPT_DATA_HASH).map(RawItems::bytes);
    }

    /**
     * @return the collateral inputs, or empty if the field is absent
     */
    public Optional<List<RawInput>> collateral() {
        return value(TxBodyField.COLLATERAL).map(this::inputs);
    }

    /**
     * @return the required signers' key hashes in encoded order, with any repeated hash kept, or empty if the field is
     * absent or its key means something else in this era
     */
    public Optional<List<byte[]>> requiredSigners() {
        return value(TxBodyField.REQUIRED_SIGNERS).map(field -> items(field, "required signers", RawItems::bytes));
    }

    /**
     * @return the network id, as encoded; which values exist is not checked
     */
    public OptionalInt networkId() {
        Optional<CborSpan> field = value(TxBodyField.NETWORK);
        return field.isPresent() ? OptionalInt.of(RawItems.smallUnsigned(field.get())) : OptionalInt.empty();
    }

    public Optional<RawOutput> collateralReturn() {
        return value(TxBodyField.COLLATERAL_RETURN).map(this::output);
    }

    public Optional<BigInteger> totalCollateral() {
        return value(TxBodyField.TOTAL_COLLATERAL).map(RawItems::unsigned);
    }

    /**
     * @return the reference inputs, or empty if the field is absent
     */
    public Optional<List<RawInput>> referenceInputs() {
        return value(TxBodyField.REFERENCE_INPUTS).map(this::inputs);
    }

    public Optional<RawVotingProcedures> votingProcedures() {
        return value(TxBodyField.VOTING_PROCEDURES)
                .map(lenient ? RawVotingProcedures::lenient : RawVotingProcedures::of);
    }

    /**
     * @return the proposal procedures in encoded order, with any repeated one kept, read in this view's era; or empty
     * if the field is absent
     */
    public Optional<List<RawProposal>> proposalProcedures() {
        return value(TxBodyField.PROPOSAL_PROCEDURES).map(field -> items(field, "proposal procedures",
                proposal -> lenient ? RawProposal.lenient(proposal, era) : RawProposal.of(proposal, era)));
    }

    /**
     * @return the current treasury value; read as the models read it, any integer that is not a bignum
     */
    public Optional<BigInteger> currentTreasury() {
        if (tolerant)
            return value(TxBodyField.CURRENT_TREASURY).map(value -> RawItems.integer(value, true));
        return value(TxBodyField.CURRENT_TREASURY).map(lenient ? RawItems::number : RawItems::unsigned);
    }

    public Optional<BigInteger> donation() {
        return value(TxBodyField.DONATION).map(value -> tolerant ? RawItems.integer(value, true)
                : RawItems.unsigned(value));
    }

    private List<RawInput> inputs(CborSpan field) {
        return items(field, "set of inputs", RawInput::of);
    }

    private static <T> List<T> items(CborSpan field, String what, Function<CborSpan, T> read) {
        List<CborSpan> items = RawItems.array(field, what);
        List<T> views = new ArrayList<>(items.size());
        for (CborSpan item : items)
            views.add(read.apply(item));
        return views;
    }

    private OptionalLong number(TxBodyField field) {
        Optional<CborSpan> value = value(field);
        return value.isPresent() ? OptionalLong.of(RawItems.unsignedLong(value.get())) : OptionalLong.empty();
    }

    private CborSpan required(TxBodyField field) {
        return value(field).orElseThrow(() -> error("A transaction body without " + field, span));
    }

    private Optional<CborSpan> value(TxBodyField field) {
        if (TxBodyField.RECORD.replacedIn(field.spec(), era))
            return Optional.empty();
        return RawTx.field(fields, field.spec().key());
    }
}
