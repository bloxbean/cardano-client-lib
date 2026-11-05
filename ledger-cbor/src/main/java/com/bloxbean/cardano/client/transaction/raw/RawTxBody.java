package com.bloxbean.cardano.client.transaction.raw;

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
 * Update and the governance fields stay spans (M5). A view from {@link #lenient(CborSpan, Era)} reads what the models
 * of {@code transaction-spec} have always accepted, which D7 keeps until a separate decision changes it. See
 * {@link RawOutput}, {@link RawValue} and {@link RawCertificate} for what that means for them.
 */
public final class RawTxBody {
    private final CborSpan span;
    private final List<Map.Entry<CborSpan, CborSpan>> fields;
    private final Era era;
    private final boolean lenient;

    RawTxBody(CborSpan span, List<Map.Entry<CborSpan, CborSpan>> fields, Era era, boolean lenient) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        this.span = span;
        this.fields = fields;
        this.era = era;
        this.lenient = lenient;
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
        return value(TxBodyField.WITHDRAWALS).map(RawWithdrawal::read).orElse(List.of());
    }

    /**
     * @return the update, as encoded
     */
    public Optional<CborSpan> update() {
        return value(TxBodyField.UPDATE_PROPOSAL);
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

    /**
     * @return the voting procedures, as encoded
     */
    public Optional<CborSpan> votingProcedures() {
        return value(TxBodyField.VOTING_PROCEDURES);
    }

    /**
     * @return the proposal procedures, as encoded
     */
    public Optional<CborSpan> proposalProcedures() {
        return value(TxBodyField.PROPOSAL_PROCEDURES);
    }

    /**
     * @return the current treasury value, as encoded
     */
    public Optional<CborSpan> currentTreasury() {
        return value(TxBodyField.CURRENT_TREASURY);
    }

    /**
     * @return the donation, as encoded
     */
    public Optional<CborSpan> donation() {
        return value(TxBodyField.DONATION);
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
