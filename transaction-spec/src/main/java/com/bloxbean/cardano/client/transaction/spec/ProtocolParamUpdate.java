package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.ExUnitPrices;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.spec.Rational;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.Nonce;
import com.bloxbean.cardano.client.transaction.spec.governance.DRepVotingThresholds;
import com.bloxbean.cardano.client.transaction.spec.governance.PoolVotingThresholds;
import com.bloxbean.cardano.client.transaction.raw.RawCostModels;
import com.bloxbean.cardano.client.transaction.raw.RawExUnits;
import com.bloxbean.cardano.client.transaction.raw.RawNonce;
import com.bloxbean.cardano.client.transaction.raw.RawProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toRationalNumber;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.requireDijkstra;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class ProtocolParamUpdate {
    private BigInteger minFeeA; //0
    private BigInteger minFeeB; //1
    private Long maxBlockSize; //2
    private Long maxTxSize; //3
    private Integer maxBlockHeaderSize; //4
    private BigInteger keyDeposit; //5
    private BigInteger poolDeposit; //6
    private Long maxEpoch; //7
    // The getter, getNOpt, names the JSON property nopt; naming the field so lets the property be read back.
    @JsonProperty("nopt")
    private Integer nOpt; //8
    private Rational poolPledgeInfluence; //rational //9
    private UnitInterval expansionRate; //unit interval //10
    private UnitInterval treasuryGrowthRate; //11

    @Deprecated
    private UnitInterval decentralisationConstant; //12

    @Deprecated
    //Removed in Babbage era
    private Nonce extraEntropy; //13

    private ProtocolVersion protocolVersion; //14

    @Deprecated
    private BigInteger minUtxo; //TODO //15

    private BigInteger minPoolCost; //16
    private BigInteger adaPerUtxoByte; //17

    //Alonzo changes
    private CostMdls costModels; //18
    private ExUnitPrices executionCosts; //19
    private ExUnits maxTxExUnits; //20
    private ExUnits maxBlockExUnits; //21
    private Long maxValSize; //22
    private Integer collateralPercent; //23
    private Integer maxCollateralInputs; //24

    //Conway changes
    private PoolVotingThresholds poolVotingThresholds; //25
    private DRepVotingThresholds drepVotingThresholds; //26
    private Integer committeeMinSize; //27
    private Long committeeMaxTermLength; //28
    private Long govActionLifetime; //29
    private BigInteger govActionDeposit; //30
    private BigInteger drepDeposit; //31
    private Long drepActivity; //32
    private Rational minFeeRefScriptCostPerByte; //33

    //Dijkstra changes
    /**
     * The maximum size of the reference scripts of a block (key 34), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long maxRefScriptSizePerBlock; //34

    /**
     * The maximum size of the reference scripts of a transaction (key 35), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long maxRefScriptSizePerTx; //35

    /**
     * The reference script cost stride, positive (key 36), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long refScriptCostStride; //36

    /**
     * The reference script cost multiplier, positive (key 37), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Rational refScriptCostMultiplier; //37

    /**
     * The maximum pledge leverage (key 38), written only in Dijkstra: null when absent, empty for nil (no maximum), as
     * the ledger's {@code StrictMaybe NonNegativeInterval} inside an update.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = NilOrValueJsonSerializer.class)
    @JsonDeserialize(using = NilOrValueJsonDeserializer.class)
    private Optional<Rational> maxPledgeLeverage; //38

    /**
     * The minimum pool margin (key 39), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UnitInterval minPoolMargin; //39

    /**
     * The Leios announcement period length in milliseconds (key 40), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long leiosAnnouncementPeriodLength; //40

    /**
     * The Leios vote period length in milliseconds (key 41), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long leiosVotePeriodLength; //41

    /**
     * The Leios diffusion period length in milliseconds (key 42), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long leiosDiffusionPeriodLength; //42

    /**
     * The Leios committee size (key 43), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer leiosCommitteeSize; //43

    /**
     * The Leios quorum stake threshold (key 44), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UnitInterval leiosQuorumStakeThreshold; //44

    /**
     * The maximum size of an endorser block's references (key 45), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long maxEndorserBlockReferencesSize; //45

    /**
     * The maximum size of an endorser block's transactions (key 46), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long maxEndorserBlockTxsSize; //46

    /**
     * The maximum execution units of an endorser block (key 47), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ExUnits maxEndorserBlockExUnits; //47

    /**
     * The maximum size of the reference scripts of an endorser block (key 48), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long maxRefScriptSizePerEndorserBlock; //48

    /**
     * The Peras minimum candidate block age in slots (key 49), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long perasMinCandidateBlockAge; //49

    /**
     * The Peras healing factor, positive (key 50), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Rational perasHealingFactor; //50

    /**
     * The Peras certificate boost (key 51), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer perasCertBoost; //51

    /**
     * The Peras target committee size (key 52), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer perasTargetCommitteeSize; //52

    /**
     * The Peras bootstrap round (key 53), written only in Dijkstra: null when absent, empty for nil, as the ledger's
     * {@code StrictMaybe Word32} inside an update.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = NilOrValueJsonSerializer.class)
    @JsonDeserialize(using = NilOrValueJsonDeserializer.class)
    private Optional<Long> perasBootstrapRound; //53

    /**
     * The Peras quorum threshold safety margin (key 54), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UnitInterval perasQuorumThresholdSafetyMargin; //54

    /**
     * The fee per multi-asset policy id in reference inputs (key 55), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigInteger refInputsCostPerMultiAssetPolicy; //55

    /**
     * The fee per datum byte in reference inputs (key 56), written only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigInteger refInputsCostPerDatumByte; //56

    /**
     * The constructor of the fields before Dijkstra, kept for source and binary compatibility; use the builder for the
     * Dijkstra keys.
     */
    @SuppressWarnings("java:S107") // the old signature, kept for callers
    public ProtocolParamUpdate(BigInteger minFeeA, BigInteger minFeeB, Long maxBlockSize, Long maxTxSize,
                               Integer maxBlockHeaderSize, BigInteger keyDeposit, BigInteger poolDeposit,
                               Long maxEpoch, Integer nOpt, Rational poolPledgeInfluence, UnitInterval expansionRate,
                               UnitInterval treasuryGrowthRate, UnitInterval decentralisationConstant,
                               Nonce extraEntropy, ProtocolVersion protocolVersion, BigInteger minUtxo,
                               BigInteger minPoolCost, BigInteger adaPerUtxoByte, CostMdls costModels,
                               ExUnitPrices executionCosts, ExUnits maxTxExUnits, ExUnits maxBlockExUnits,
                               Long maxValSize, Integer collateralPercent, Integer maxCollateralInputs,
                               PoolVotingThresholds poolVotingThresholds, DRepVotingThresholds drepVotingThresholds,
                               Integer committeeMinSize, Long committeeMaxTermLength, Long govActionLifetime,
                               BigInteger govActionDeposit, BigInteger drepDeposit, Long drepActivity,
                               Rational minFeeRefScriptCostPerByte) {
        this(minFeeA, minFeeB, maxBlockSize, maxTxSize, maxBlockHeaderSize, keyDeposit, poolDeposit, maxEpoch, nOpt,
                poolPledgeInfluence, expansionRate, treasuryGrowthRate, decentralisationConstant, extraEntropy,
                protocolVersion, minUtxo, minPoolCost, adaPerUtxoByte, costModels, executionCosts, maxTxExUnits,
                maxBlockExUnits, maxValSize, collateralPercent, maxCollateralInputs, poolVotingThresholds,
                drepVotingThresholds, committeeMinSize, committeeMaxTermLength, govActionLifetime, govActionDeposit,
                drepDeposit, drepActivity, minFeeRefScriptCostPerByte, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * The constructor before the Conway keys and the widening of three fields.
     *
     * @deprecated use {@code builder()}
     */
    @Deprecated
    @SuppressWarnings("java:S107") // the old signature, kept for callers
    public ProtocolParamUpdate(BigInteger minFeeA, BigInteger minFeeB, Integer maxBlockSize, Integer maxTxSize,
                               Integer maxBlockHeaderSize, BigInteger keyDeposit, BigInteger poolDeposit,
                               Integer maxEpoch, Integer nOpt, Rational poolPledgeInfluence,
                               UnitInterval expansionRate, UnitInterval treasuryGrowthRate,
                               UnitInterval decentralisationConstant, Nonce extraEntropy,
                               ProtocolVersion protocolVersion, BigInteger minUtxo, BigInteger minPoolCost,
                               BigInteger adaPerUtxoByte, CostMdls costModels, ExUnitPrices executionCosts,
                               ExUnits maxTxExUnits, ExUnits maxBlockExUnits, Long maxValSize,
                               Integer collateralPercent, Integer maxCollateralInputs) {
        this(minFeeA, minFeeB, toLongOrNull(maxBlockSize), toLongOrNull(maxTxSize), maxBlockHeaderSize, keyDeposit,
                poolDeposit, toLongOrNull(maxEpoch), nOpt, poolPledgeInfluence, expansionRate, treasuryGrowthRate,
                decentralisationConstant, extraEntropy, protocolVersion, minUtxo, minPoolCost, adaPerUtxoByte,
                costModels, executionCosts, maxTxExUnits, maxBlockExUnits, maxValSize, collateralPercent,
                maxCollateralInputs, null, null, null, null, null, null, null, null, null);
    }

    private static Long toLongOrNull(Integer value) {
        return value == null ? null : value.longValue();
    }

    /**
     * Existing callers keep their {@code Integer} methods, including with a {@code null} argument. The {@code ...Long}
     * methods take the whole range of the widened fields.
     */
    public static class ProtocolParamUpdateBuilder {
        public ProtocolParamUpdateBuilder maxBlockSize(Integer maxBlockSize) {
            this.maxBlockSize = toLongOrNull(maxBlockSize);
            return this;
        }

        public ProtocolParamUpdateBuilder maxBlockSizeLong(Long maxBlockSize) {
            this.maxBlockSize = maxBlockSize;
            return this;
        }

        public ProtocolParamUpdateBuilder maxTxSize(Integer maxTxSize) {
            this.maxTxSize = toLongOrNull(maxTxSize);
            return this;
        }

        public ProtocolParamUpdateBuilder maxTxSizeLong(Long maxTxSize) {
            this.maxTxSize = maxTxSize;
            return this;
        }

        public ProtocolParamUpdateBuilder maxEpoch(Integer maxEpoch) {
            this.maxEpoch = toLongOrNull(maxEpoch);
            return this;
        }

        public ProtocolParamUpdateBuilder maxEpochLong(Long maxEpoch) {
            this.maxEpoch = maxEpoch;
            return this;
        }
    }

    public DataItem serialize() throws CborSerializationException {
        return serialize(EraSerializationConfig.INSTANCE.getEra());
    }

    /**
     * Writes the update in an era: the Dijkstra keys (34 to 56) only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param era the era to write it in
     * @return the update
     * @throws CborSerializationException if a field cannot be encoded, or a Dijkstra key is set and the era is before
     *                                    Dijkstra
     */
    public DataItem serialize(Era era) throws CborSerializationException {
        Map map = new Map();

        try {
            if (minFeeA != null) {
                map.put(new UnsignedInteger(0), new UnsignedInteger(minFeeA));
            }

            if (minFeeB != null) {
                map.put(new UnsignedInteger(1), new UnsignedInteger(minFeeB));
            }

            if (maxBlockSize != null) {
                map.put(new UnsignedInteger(2), new UnsignedInteger(maxBlockSize));
            }

            if (maxTxSize != null) {
                map.put(new UnsignedInteger(3), new UnsignedInteger(maxTxSize));
            }

            if (maxBlockHeaderSize != null) {
                map.put(new UnsignedInteger(4), new UnsignedInteger(maxBlockHeaderSize));
            }

            if (keyDeposit != null) {
                map.put(new UnsignedInteger(5), new UnsignedInteger(keyDeposit));
            }

            if (poolDeposit != null) {
                map.put(new UnsignedInteger(6), new UnsignedInteger(poolDeposit));
            }

            if (maxEpoch != null) {
                map.put(new UnsignedInteger(7), new UnsignedInteger(maxEpoch));
            }

            if (nOpt != null) {
                map.put(new UnsignedInteger(8), new UnsignedInteger(nOpt));
            }

            if (poolPledgeInfluence != null) {
                map.put(new UnsignedInteger(9), toRationalNumber(poolPledgeInfluence));
            }

            if (expansionRate != null) {
                map.put(new UnsignedInteger(10), toRationalNumber(expansionRate));
            }

            if (treasuryGrowthRate != null) {
                map.put(new UnsignedInteger(11), toRationalNumber(treasuryGrowthRate));
            }

            if (decentralisationConstant != null) {
                map.put(new UnsignedInteger(12), toRationalNumber(decentralisationConstant));
            }

            //Entropy removed in Babbage era
            if (extraEntropy != null) {
                map.put(new UnsignedInteger(13), extraEntropy.serialize());
            }

            if (protocolVersion != null) {
                Array protocolVersionArr = new Array();
                protocolVersionArr.add(new UnsignedInteger(protocolVersion.getMajor()));
                protocolVersionArr.add(new UnsignedInteger(protocolVersion.getMinor()));

                map.put(new UnsignedInteger(14), protocolVersionArr);
            }

            //Removed in Babbage era
            if (minUtxo != null) {
                map.put(new UnsignedInteger(15), new UnsignedInteger(minUtxo));
            }

            if (minPoolCost != null) {
                map.put(new UnsignedInteger(16), new UnsignedInteger(minPoolCost));
            }

            if (adaPerUtxoByte != null) {
                map.put(new UnsignedInteger(17), new UnsignedInteger(adaPerUtxoByte));
            }

            if (costModels != null) {
                map.put(new UnsignedInteger(18), costModels.serialize());
            }

            if (executionCosts != null) {
                map.put(new UnsignedInteger(19), executionCosts.serialize());
            }

            if (maxTxExUnits != null) {
                map.put(new UnsignedInteger(20), maxTxExUnits.serialize());
            }

            if (maxBlockExUnits != null) {
                map.put(new UnsignedInteger(21), maxBlockExUnits.serialize());
            }

            if (maxValSize != null) {
                map.put(new UnsignedInteger(22), new UnsignedInteger(maxValSize));
            }

            if (collateralPercent != null) {
                map.put(new UnsignedInteger(23), new UnsignedInteger(collateralPercent));
            }

            if (maxCollateralInputs != null) {
                map.put(new UnsignedInteger(24), new UnsignedInteger(maxCollateralInputs));
            }

            if (poolVotingThresholds != null) {
                map.put(new UnsignedInteger(25), poolVotingThresholds.serialize());
            }

            if (drepVotingThresholds != null) {
                map.put(new UnsignedInteger(26), drepVotingThresholds.serialize());
            }

            if (committeeMinSize != null) {
                map.put(new UnsignedInteger(27), new UnsignedInteger(committeeMinSize));
            }

            if (committeeMaxTermLength != null) {
                map.put(new UnsignedInteger(28), new UnsignedInteger(committeeMaxTermLength));
            }

            if (govActionLifetime != null) {
                map.put(new UnsignedInteger(29), new UnsignedInteger(govActionLifetime));
            }

            if (govActionDeposit != null) {
                map.put(new UnsignedInteger(30), new UnsignedInteger(govActionDeposit));
            }

            if (drepDeposit != null) {
                map.put(new UnsignedInteger(31), new UnsignedInteger(drepDeposit));
            }

            if (drepActivity != null) {
                map.put(new UnsignedInteger(32), new UnsignedInteger(drepActivity));
            }

            if (minFeeRefScriptCostPerByte != null) {
                map.put(new UnsignedInteger(33), toRationalNumber(minFeeRefScriptCostPerByte));
            }

            putDijkstra(map, era, 34, "maxRefScriptSizePerBlock", maxRefScriptSizePerBlock);
            putDijkstra(map, era, 35, "maxRefScriptSizePerTx", maxRefScriptSizePerTx);
            putDijkstra(map, era, 36, "refScriptCostStride", refScriptCostStride);
            putDijkstra(map, era, 37, "refScriptCostMultiplier", refScriptCostMultiplier);
            putDijkstra(map, era, 38, "maxPledgeLeverage", maxPledgeLeverage);
            putDijkstra(map, era, 39, "minPoolMargin", minPoolMargin);
            putDijkstra(map, era, 40, "leiosAnnouncementPeriodLength", leiosAnnouncementPeriodLength);
            putDijkstra(map, era, 41, "leiosVotePeriodLength", leiosVotePeriodLength);
            putDijkstra(map, era, 42, "leiosDiffusionPeriodLength", leiosDiffusionPeriodLength);
            putDijkstra(map, era, 43, "leiosCommitteeSize", leiosCommitteeSize);
            putDijkstra(map, era, 44, "leiosQuorumStakeThreshold", leiosQuorumStakeThreshold);
            putDijkstra(map, era, 45, "maxEndorserBlockReferencesSize", maxEndorserBlockReferencesSize);
            putDijkstra(map, era, 46, "maxEndorserBlockTxsSize", maxEndorserBlockTxsSize);
            putDijkstra(map, era, 47, "maxEndorserBlockExUnits", maxEndorserBlockExUnits);
            putDijkstra(map, era, 48, "maxRefScriptSizePerEndorserBlock", maxRefScriptSizePerEndorserBlock);
            putDijkstra(map, era, 49, "perasMinCandidateBlockAge", perasMinCandidateBlockAge);
            putDijkstra(map, era, 50, "perasHealingFactor", perasHealingFactor);
            putDijkstra(map, era, 51, "perasCertBoost", perasCertBoost);
            putDijkstra(map, era, 52, "perasTargetCommitteeSize", perasTargetCommitteeSize);
            putDijkstra(map, era, 53, "perasBootstrapRound", perasBootstrapRound);
            putDijkstra(map, era, 54, "perasQuorumThresholdSafetyMargin", perasQuorumThresholdSafetyMargin);
            putDijkstra(map, era, 55, "refInputsCostPerMultiAssetPolicy", refInputsCostPerMultiAssetPolicy);
            putDijkstra(map, era, 56, "refInputsCostPerDatumByte", refInputsCostPerDatumByte);
        } catch (Exception e) {
            throw new CborSerializationException("Serialization error", e);
        }

        return map;
    }

    // A Dijkstra key: written when it is set, and only in Dijkstra.
    private static void putDijkstra(Map map, Era era, int key, String field, Object value)
            throws CborSerializationException {
        if (value == null)
            return;
        requireDijkstra(era, "ProtocolParamUpdate." + field);
        map.put(new UnsignedInteger(key), item(value));
    }

    // A Dijkstra key's value: an empty Optional is nil, a rational has tag 30.
    private static DataItem item(Object value) throws CborSerializationException {
        if (value instanceof Optional<?> optional)
            return optional.isEmpty() ? SimpleValue.NULL : item(optional.get());
        if (value instanceof UnitInterval interval)
            return toRationalNumber(interval);
        if (value instanceof ExUnits exUnits)
            return exUnits.serialize();
        if (value instanceof BigInteger number)
            return new UnsignedInteger(number);
        return new UnsignedInteger(value instanceof Integer number ? number.longValue() : (Long) value);
    }

    // Haskell reads these as Word32 or EpochInterval. A Long holds that whole range without using its sign.
    private static Long toWord32(OptionalLong value) {
        if (value.isEmpty())
            return null;
        long word = value.getAsLong();
        if (word < 0 || word > 0xffffffffL)
            throw new CborRuntimeException("Expected an unsigned 32-bit integer, found : "
                    + Long.toUnsignedString(word));
        return word;
    }

    private static Integer orNull(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    private static Rational toRational(Optional<UnitInterval> value) {
        return value.map(interval -> new Rational(interval.getNumerator(), interval.getDenominator())).orElse(null);
    }

    public static ProtocolParamUpdate deserialize(DataItem din) throws CborDeserializationException {
        return from(RawProtocolParamUpdate.lenient(CborSerializationUtil.span(din), RawModels.READ_IN));
    }

    /**
     * @param raw a protocol parameter update as received
     * @return the model, which keeps the coins per UTxO of key 17 in {@code adaPerUtxoByte} whatever the era, and the
     * cost models read in the view's era (language 3 is Plutus V4 only in Dijkstra)
     * @throws CborDeserializationException if the cost models, the execution prices or units, or the nonce are not what
     *                                      their models accept
     */
    public static ProtocolParamUpdate from(RawProtocolParamUpdate raw) throws CborDeserializationException {
        Optional<RawExUnits> maxTxExUnits = raw.maxTxExUnits();
        Optional<RawExUnits> maxBlockExUnits = raw.maxBlockExUnits();
        Optional<RawCostModels> costModels = raw.costModels();
        Optional<RawNonce> extraEntropy = raw.extraEntropy();
        Optional<RawExUnits> maxEndorserBlockExUnits = raw.maxEndorserBlockExUnits();
        return ProtocolParamUpdate.builder()
                .minFeeA(raw.minFeeA().orElse(null))
                .minFeeB(raw.minFeeB().orElse(null))
                .maxBlockSizeLong(toWord32(raw.maxBlockBodySize()))
                .maxTxSizeLong(toWord32(raw.maxTxSize()))
                .maxBlockHeaderSize(orNull(raw.maxBlockHeaderSize()))
                .keyDeposit(raw.keyDeposit().orElse(null))
                .poolDeposit(raw.poolDeposit().orElse(null))
                .maxEpochLong(toWord32(raw.maxEpoch()))
                .nOpt(orNull(raw.nOpt()))
                .poolPledgeInfluence(toRational(raw.poolPledgeInfluence()))
                .expansionRate(raw.expansionRate().orElse(null))
                .treasuryGrowthRate(raw.treasuryGrowthRate().orElse(null))
                .decentralisationConstant(raw.decentralization().orElse(null))
                .extraEntropy(extraEntropy.isPresent() ? Nonce.from(extraEntropy.get()) : null)
                .protocolVersion(raw.protocolVersion().map(ProtocolVersion::from).orElse(null))
                .minUtxo(raw.minUtxoValue().orElse(null))
                .minPoolCost(raw.minPoolCost().orElse(null))
                .adaPerUtxoByte(raw.coinsPerUtxoByte().orElse(null))
                .costModels(costModels.isPresent() ? CostMdls.from(costModels.get(), raw.era()) : null)
                .executionCosts(raw.executionCosts().map(ExUnitPrices::from).orElse(null))
                .maxTxExUnits(maxTxExUnits.isPresent() ? ExUnits.from(maxTxExUnits.get(), raw.era()) : null)
                .maxBlockExUnits(maxBlockExUnits.isPresent() ? ExUnits.from(maxBlockExUnits.get(), raw.era()) : null)
                .maxValSize(toWord32(raw.maxValueSize()))
                .collateralPercent(orNull(raw.collateralPercentage()))
                .maxCollateralInputs(orNull(raw.maxCollateralInputs()))
                .poolVotingThresholds(raw.poolVotingThresholds().map(PoolVotingThresholds::from).orElse(null))
                .drepVotingThresholds(raw.drepVotingThresholds().map(DRepVotingThresholds::from).orElse(null))
                .committeeMinSize(orNull(raw.minCommitteeSize()))
                .committeeMaxTermLength(toWord32(raw.committeeTermLimit()))
                .govActionLifetime(toWord32(raw.govActionLifetime()))
                .govActionDeposit(raw.govActionDeposit().orElse(null))
                .drepDeposit(raw.drepDeposit().orElse(null))
                .drepActivity(toWord32(raw.drepInactivity()))
                .minFeeRefScriptCostPerByte(toRational(raw.minFeeRefScriptCostPerByte()))
                .maxRefScriptSizePerBlock(toWord32(raw.maxRefScriptSizePerBlock()))
                .maxRefScriptSizePerTx(toWord32(raw.maxRefScriptSizePerTx()))
                .refScriptCostStride(toWord32(raw.refScriptCostStride()))
                .refScriptCostMultiplier(toRational(raw.refScriptCostMultiplier()))
                .maxPledgeLeverage(raw.maxPledgeLeverage().map(value -> Optional.ofNullable(toRational(value)))
                        .orElse(null))
                .minPoolMargin(raw.minPoolMargin().orElse(null))
                .leiosAnnouncementPeriodLength(toWord32(raw.leiosAnnouncementPeriodLength()))
                .leiosVotePeriodLength(toWord32(raw.leiosVotePeriodLength()))
                .leiosDiffusionPeriodLength(toWord32(raw.leiosDiffusionPeriodLength()))
                .leiosCommitteeSize(orNull(raw.leiosCommitteeSize()))
                .leiosQuorumStakeThreshold(raw.leiosQuorumStakeThreshold().orElse(null))
                .maxEndorserBlockReferencesSize(toWord32(raw.maxEndorserBlockReferencesSize()))
                .maxEndorserBlockTxsSize(toWord32(raw.maxEndorserBlockTxsSize()))
                .maxEndorserBlockExUnits(maxEndorserBlockExUnits.isPresent()
                        ? ExUnits.from(maxEndorserBlockExUnits.get(), raw.era()) : null)
                .maxRefScriptSizePerEndorserBlock(toWord32(raw.maxRefScriptSizePerEndorserBlock()))
                .perasMinCandidateBlockAge(toWord32(raw.perasMinCandidateBlockAge()))
                .perasHealingFactor(toRational(raw.perasHealingFactor()))
                .perasCertBoost(orNull(raw.perasCertBoost()))
                .perasTargetCommitteeSize(orNull(raw.perasTargetCommitteeSize()))
                .perasBootstrapRound(raw.perasBootstrapRound().map(value -> Optional.ofNullable(toWord32(value)))
                        .orElse(null))
                .perasQuorumThresholdSafetyMargin(raw.perasQuorumThresholdSafetyMargin().orElse(null))
                .refInputsCostPerMultiAssetPolicy(raw.refInputsCostPerMultiAssetPolicy().orElse(null))
                .refInputsCostPerDatumByte(raw.refInputsCostPerDatumByte().orElse(null))
                .build();
    }
}
