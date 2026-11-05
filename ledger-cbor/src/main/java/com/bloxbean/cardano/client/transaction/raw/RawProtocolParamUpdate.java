package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.UnitInterval;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * A protocol parameter update read in an era (ADR 0003 D3): {@link ProtocolParamField} says which key a field has, and
 * a key that means something else in the era reads as absent, as key 17 does. A field outside its era is otherwise read
 * like any other: that is the policy's to reject (D5). Accessors read a field when they are called, and throw
 * {@link CborRuntimeException} if it is not what the table says. Unknown keys are passed over, and a repeated key means
 * its last value. Tags on an item are looked through, and ranges are not checked.
 * <p>
 * {@link #lenient(CborSpan, Era)} reads what CCL's model has always read (D7): a number that is no number reads as
 * absent where the model reads any integer, the numbers of a rational may be bignums, a nonce may have items after its
 * last, the execution prices are definite and may have items after their last, and the cost models are read with
 * {@link RawCostModels#lenient(CborSpan)}.
 */
public final class RawProtocolParamUpdate {
    private final CborSpan span;
    private final List<Map.Entry<CborSpan, CborSpan>> fields;
    private final Era era;
    private final boolean lenient;

    private RawProtocolParamUpdate(CborSpan span, Era era, boolean lenient) {
        if (era == null)
            throw new IllegalArgumentException("era is null");
        this.span = span;
        this.fields = RawItems.lastFirst(span, ProtocolParamField.RECORD.name());
        this.era = era;
        this.lenient = lenient;
    }

    /**
     * @param span a parameter update as encoded
     * @param era  the era to read it in
     * @return the view
     * @throws CborRuntimeException     if it is not a map
     * @throws IllegalArgumentException if the era is null
     */
    public static RawProtocolParamUpdate of(CborSpan span, Era era) {
        return new RawProtocolParamUpdate(span, era, false);
    }

    /**
     * @param span a parameter update as encoded
     * @param era  the era to read it in
     * @return the view, read as CCL's model reads one
     * @throws CborRuntimeException     if it is not a map
     * @throws IllegalArgumentException if the era is null
     */
    public static RawProtocolParamUpdate lenient(CborSpan span, Era era) {
        return new RawProtocolParamUpdate(span, era, true);
    }

    /**
     * @return the update as encoded
     */
    public CborSpan span() {
        return span;
    }

    public Era era() {
        return era;
    }

    public Optional<BigInteger> minFeeA() {
        return coin(ProtocolParamField.MIN_FEE_A);
    }

    public Optional<BigInteger> minFeeB() {
        return coin(ProtocolParamField.MIN_FEE_B);
    }

    /**
     * @return the maximum block body size, its 64 bits
     */
    public OptionalLong maxBlockBodySize() {
        return word(ProtocolParamField.MAX_BLOCK_BODY_SIZE);
    }

    /**
     * @return the maximum transaction size, its 64 bits
     */
    public OptionalLong maxTxSize() {
        return word(ProtocolParamField.MAX_TX_SIZE);
    }

    public OptionalInt maxBlockHeaderSize() {
        return small(ProtocolParamField.MAX_BLOCK_HEADER_SIZE);
    }

    public Optional<BigInteger> keyDeposit() {
        return coin(ProtocolParamField.KEY_DEPOSIT);
    }

    public Optional<BigInteger> poolDeposit() {
        return coin(ProtocolParamField.POOL_DEPOSIT);
    }

    /**
     * @return the maximum epoch interval of a pool retirement, its 64 bits
     */
    public OptionalLong maxEpoch() {
        return word(ProtocolParamField.MAX_EPOCH);
    }

    public OptionalInt nOpt() {
        return small(ProtocolParamField.N_OPT);
    }

    public Optional<UnitInterval> poolPledgeInfluence() {
        return rational(ProtocolParamField.POOL_PLEDGE_INFLUENCE);
    }

    public Optional<UnitInterval> expansionRate() {
        return rational(ProtocolParamField.EXPANSION_RATE);
    }

    public Optional<UnitInterval> treasuryGrowthRate() {
        return rational(ProtocolParamField.TREASURY_GROWTH_RATE);
    }

    public Optional<UnitInterval> decentralization() {
        return rational(ProtocolParamField.DECENTRALIZATION);
    }

    public Optional<RawNonce> extraEntropy() {
        return value(ProtocolParamField.EXTRA_ENTROPY).map(lenient ? RawNonce::lenient : RawNonce::of);
    }

    public Optional<RawProtocolVersion> protocolVersion() {
        return value(ProtocolParamField.PROTOCOL_VERSION)
                .map(lenient ? RawProtocolVersion::lenient : RawProtocolVersion::of);
    }

    public Optional<BigInteger> minUtxoValue() {
        return coin(ProtocolParamField.MIN_UTXO_VALUE);
    }

    public Optional<BigInteger> minPoolCost() {
        return coin(ProtocolParamField.MIN_POOL_COST);
    }

    /**
     * @return the coins per UTxO word of key 17, or empty if the key means coins per byte in this era
     */
    public Optional<BigInteger> coinsPerUtxoWord() {
        return coin(ProtocolParamField.COINS_PER_UTXO_WORD);
    }

    /**
     * @return the coins per UTxO byte of key 17. Before Babbage the key is outside this field's era, and is read as a
     * field outside its era is: as it is (ADR 0003 D5)
     */
    public Optional<BigInteger> coinsPerUtxoByte() {
        return coin(ProtocolParamField.COINS_PER_UTXO_BYTE);
    }

    public Optional<RawCostModels> costModels() {
        return value(ProtocolParamField.COST_MODELS).map(lenient ? RawCostModels::lenient : RawCostModels::of);
    }

    public Optional<RawExUnitPrices> executionCosts() {
        return value(ProtocolParamField.EXECUTION_COSTS).map(lenient ? RawExUnitPrices::lenient : RawExUnitPrices::of);
    }

    public Optional<RawExUnits> maxTxExUnits() {
        return value(ProtocolParamField.MAX_TX_EX_UNITS).map(RawExUnits::of);
    }

    public Optional<RawExUnits> maxBlockExUnits() {
        return value(ProtocolParamField.MAX_BLOCK_EX_UNITS).map(RawExUnits::of);
    }

    /**
     * @return the maximum value size, its 64 bits
     */
    public OptionalLong maxValueSize() {
        return word(ProtocolParamField.MAX_VALUE_SIZE);
    }

    public OptionalInt collateralPercentage() {
        return small(ProtocolParamField.COLLATERAL_PERCENTAGE);
    }

    public OptionalInt maxCollateralInputs() {
        return small(ProtocolParamField.MAX_COLLATERAL_INPUTS);
    }

    public Optional<RawVotingThresholds> poolVotingThresholds() {
        return value(ProtocolParamField.POOL_VOTING_THRESHOLDS)
                .map(item -> RawVotingThresholds.pool(item, lenient));
    }

    public Optional<RawVotingThresholds> drepVotingThresholds() {
        return value(ProtocolParamField.DREP_VOTING_THRESHOLDS)
                .map(item -> RawVotingThresholds.drep(item, lenient));
    }

    public OptionalInt minCommitteeSize() {
        return small(ProtocolParamField.MIN_COMMITTEE_SIZE);
    }

    /**
     * @return the committee term limit, its 64 bits
     */
    public OptionalLong committeeTermLimit() {
        return word(ProtocolParamField.COMMITTEE_TERM_LIMIT);
    }

    /**
     * @return the governance action lifetime, its 64 bits
     */
    public OptionalLong govActionLifetime() {
        return word(ProtocolParamField.GOV_ACTION_LIFETIME);
    }

    public Optional<BigInteger> govActionDeposit() {
        return coin(ProtocolParamField.GOV_ACTION_DEPOSIT);
    }

    public Optional<BigInteger> drepDeposit() {
        return coin(ProtocolParamField.DREP_DEPOSIT);
    }

    /**
     * @return the DRep inactivity period, its 64 bits
     */
    public OptionalLong drepInactivity() {
        return word(ProtocolParamField.DREP_INACTIVITY);
    }

    public Optional<UnitInterval> minFeeRefScriptCostPerByte() {
        return rational(ProtocolParamField.MIN_FEE_REF_SCRIPT_COST_PER_BYTE);
    }

    // A coin; when lenient, any integer or bignum, and absent if it is no number.
    private Optional<BigInteger> coin(ProtocolParamField field) {
        return value(field).map(item -> lenient ? RawItems.integer(item, true) : RawItems.unsigned(item));
    }

    // An unsigned integer whose range is not checked, in 64 bits.
    private OptionalLong word(ProtocolParamField field) {
        Optional<CborSpan> value = value(field);
        return value.isPresent() ? OptionalLong.of(RawItems.unsignedLong(value.get())) : OptionalLong.empty();
    }

    private OptionalInt small(ProtocolParamField field) {
        Optional<CborSpan> value = value(field);
        return value.isPresent() ? OptionalInt.of(RawItems.smallUnsigned(value.get())) : OptionalInt.empty();
    }

    private Optional<UnitInterval> rational(ProtocolParamField field) {
        return value(field).map(item -> RawItems.rational(item, lenient));
    }

    private Optional<CborSpan> value(ProtocolParamField field) {
        if (ProtocolParamField.RECORD.replacedIn(field.spec(), era))
            return Optional.empty();
        return RawTx.field(fields, field.key());
    }
}
