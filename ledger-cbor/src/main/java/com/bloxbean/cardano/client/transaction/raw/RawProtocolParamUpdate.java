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
        return value(ProtocolParamField.EXECUTION_COSTS)
                .map(item -> lenient ? RawExUnitPrices.lenient(item, era) : RawExUnitPrices.of(item));
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

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the maximum size of the reference scripts of a block (key 34), its 64 bits
     */
    public OptionalLong maxRefScriptSizePerBlock() {
        return word(since(ProtocolParamField.MAX_REF_SCRIPT_SIZE_PER_BLOCK));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the maximum size of the reference scripts of a transaction (key 35), its 64 bits
     */
    public OptionalLong maxRefScriptSizePerTx() {
        return word(since(ProtocolParamField.MAX_REF_SCRIPT_SIZE_PER_TX));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the reference script cost stride (key 36), its 64 bits
     */
    public OptionalLong refScriptCostStride() {
        return word(since(ProtocolParamField.REF_SCRIPT_COST_STRIDE));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the reference script cost multiplier (key 37)
     */
    public Optional<UnitInterval> refScriptCostMultiplier() {
        return rational(since(ProtocolParamField.REF_SCRIPT_COST_MULTIPLIER));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the maximum pledge leverage (key 38): empty if absent, and an empty value if it is null, no maximum
     */
    public Optional<Optional<UnitInterval>> maxPledgeLeverage() {
        return since(ProtocolParamField.MAX_PLEDGE_LEVERAGE)
                .map(item -> Optional.ofNullable(RawItems.nullable(item, value -> RawItems.rational(value, lenient))));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the minimum pool margin (key 39)
     */
    public Optional<UnitInterval> minPoolMargin() {
        return rational(since(ProtocolParamField.MIN_POOL_MARGIN));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Leios announcement period length in milliseconds (key 40), its 64 bits
     */
    public OptionalLong leiosAnnouncementPeriodLength() {
        return word(since(ProtocolParamField.LEIOS_ANNOUNCEMENT_PERIOD_LENGTH));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Leios vote period length in milliseconds (key 41), its 64 bits
     */
    public OptionalLong leiosVotePeriodLength() {
        return word(since(ProtocolParamField.LEIOS_VOTE_PERIOD_LENGTH));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Leios diffusion period length in milliseconds (key 42), its 64 bits
     */
    public OptionalLong leiosDiffusionPeriodLength() {
        return word(since(ProtocolParamField.LEIOS_DIFFUSION_PERIOD_LENGTH));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Leios committee size (key 43)
     */
    public OptionalInt leiosCommitteeSize() {
        return small(since(ProtocolParamField.LEIOS_COMMITTEE_SIZE));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Leios quorum stake threshold (key 44)
     */
    public Optional<UnitInterval> leiosQuorumStakeThreshold() {
        return rational(since(ProtocolParamField.LEIOS_QUORUM_STAKE_THRESHOLD));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the maximum size of an endorser block's references (key 45), its 64 bits
     */
    public OptionalLong maxEndorserBlockReferencesSize() {
        return word(since(ProtocolParamField.MAX_ENDORSER_BLOCK_REFERENCES_SIZE));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the maximum size of an endorser block's transactions (key 46), its 64 bits
     */
    public OptionalLong maxEndorserBlockTxsSize() {
        return word(since(ProtocolParamField.MAX_ENDORSER_BLOCK_TXS_SIZE));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the maximum execution units of an endorser block (key 47)
     */
    public Optional<RawExUnits> maxEndorserBlockExUnits() {
        return since(ProtocolParamField.MAX_ENDORSER_BLOCK_EX_UNITS).map(RawExUnits::of);
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the maximum size of the reference scripts of an endorser block (key 48), its 64 bits
     */
    public OptionalLong maxRefScriptSizePerEndorserBlock() {
        return word(since(ProtocolParamField.MAX_REF_SCRIPT_SIZE_PER_ENDORSER_BLOCK));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Peras minimum candidate block age in slots (key 49), its 64 bits
     */
    public OptionalLong perasMinCandidateBlockAge() {
        return word(since(ProtocolParamField.PERAS_MIN_CANDIDATE_BLOCK_AGE));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Peras healing factor (key 50)
     */
    public Optional<UnitInterval> perasHealingFactor() {
        return rational(since(ProtocolParamField.PERAS_HEALING_FACTOR));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Peras certificate boost (key 51)
     */
    public OptionalInt perasCertBoost() {
        return small(since(ProtocolParamField.PERAS_CERT_BOOST));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Peras target committee size (key 52)
     */
    public OptionalInt perasTargetCommitteeSize() {
        return small(since(ProtocolParamField.PERAS_TARGET_COMMITTEE_SIZE));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Peras bootstrap round (key 53), its 64 bits: empty if absent, and an empty value if it is null
     */
    public Optional<OptionalLong> perasBootstrapRound() {
        return since(ProtocolParamField.PERAS_BOOTSTRAP_ROUND).map(item -> item.isNull() ? OptionalLong.empty()
                : OptionalLong.of(RawItems.unsignedLong(item)));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the Peras quorum threshold safety margin (key 54)
     */
    public Optional<UnitInterval> perasQuorumThresholdSafetyMargin() {
        return rational(since(ProtocolParamField.PERAS_QUORUM_THRESHOLD_SAFETY_MARGIN));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the fee per multi-asset policy id in reference inputs (key 55)
     */
    public Optional<BigInteger> refInputsCostPerMultiAssetPolicy() {
        return coin(since(ProtocolParamField.REF_INPUTS_COST_PER_MULTI_ASSET_POLICY));
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return the fee per datum byte in reference inputs (key 56)
     */
    public Optional<BigInteger> refInputsCostPerDatumByte() {
        return coin(since(ProtocolParamField.REF_INPUTS_COST_PER_DATUM_BYTE));
    }

    // A coin; when lenient, any integer or bignum, and absent if it is no number.
    private Optional<BigInteger> coin(ProtocolParamField field) {
        return coin(value(field));
    }

    private Optional<BigInteger> coin(Optional<CborSpan> value) {
        return value.map(item -> RawItems.coin(item, lenient));
    }

    // An unsigned integer whose range is not checked, in 64 bits.
    private OptionalLong word(ProtocolParamField field) {
        return word(value(field));
    }

    private OptionalLong word(Optional<CborSpan> value) {
        return value.isPresent() ? OptionalLong.of(RawItems.unsignedLong(value.get())) : OptionalLong.empty();
    }

    private OptionalInt small(ProtocolParamField field) {
        return small(value(field));
    }

    private OptionalInt small(Optional<CborSpan> value) {
        return value.isPresent() ? OptionalInt.of(RawItems.smallUnsigned(value.get())) : OptionalInt.empty();
    }

    private Optional<UnitInterval> rational(ProtocolParamField field) {
        return rational(value(field));
    }

    private Optional<UnitInterval> rational(Optional<CborSpan> value) {
        return value.map(item -> RawItems.rational(item, lenient));
    }

    // A field of an era after the view's is an unknown key there, so it reads as absent, as a field of Dijkstra does
    // before Dijkstra. The other accessors read their field in any era.
    private Optional<CborSpan> since(ProtocolParamField field) {
        return field.spec().startsAfter(era) ? Optional.empty() : value(field);
    }

    private Optional<CborSpan> value(ProtocolParamField field) {
        if (ProtocolParamField.RECORD.replacedIn(field.spec(), era))
            return Optional.empty();
        return RawTx.field(fields, field.key());
    }
}
