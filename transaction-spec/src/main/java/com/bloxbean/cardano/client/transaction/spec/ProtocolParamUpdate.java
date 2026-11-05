package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.ExUnitPrices;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.spec.Rational;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.Nonce;
import com.bloxbean.cardano.client.transaction.spec.governance.DRepVotingThresholds;
import com.bloxbean.cardano.client.transaction.spec.governance.PoolVotingThresholds;
import lombok.*;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.*;
import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toRational;
import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toRationalNumber;
import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toUnitInterval;

@Getter
@AllArgsConstructor
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
        Map map = new Map();

        try {
            if (minFeeA != null) {
                map.put(new UnsignedInteger(0), new UnsignedInteger(minFeeA));
            }

            if (minFeeB != null) {
                map.put(new UnsignedInteger(1), new UnsignedInteger(minFeeB));
            }

            if (maxBlockSize != null) {
                map.put(new UnsignedInteger(2), unsignedLongItem(maxBlockSize));
            }

            if (maxTxSize != null) {
                map.put(new UnsignedInteger(3), unsignedLongItem(maxTxSize));
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
                map.put(new UnsignedInteger(7), unsignedLongItem(maxEpoch));
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
                map.put(new UnsignedInteger(22), unsignedLongItem(maxValSize));
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
                map.put(new UnsignedInteger(28), unsignedLongItem(committeeMaxTermLength));
            }

            if (govActionLifetime != null) {
                map.put(new UnsignedInteger(29), unsignedLongItem(govActionLifetime));
            }

            if (govActionDeposit != null) {
                map.put(new UnsignedInteger(30), new UnsignedInteger(govActionDeposit));
            }

            if (drepDeposit != null) {
                map.put(new UnsignedInteger(31), new UnsignedInteger(drepDeposit));
            }

            if (drepActivity != null) {
                map.put(new UnsignedInteger(32), unsignedLongItem(drepActivity));
            }

            if (minFeeRefScriptCostPerByte != null) {
                map.put(new UnsignedInteger(33), toRationalNumber(minFeeRefScriptCostPerByte));
            }

        } catch (Exception e) {
            throw new CborSerializationException("Serialization error", e);
        }

        return map;
    }

    public static ProtocolParamUpdate deserialize(DataItem din) throws CborDeserializationException {
        Map map = (Map) din;

        DataItem itemDI = map.get(new UnsignedInteger(0));
        BigInteger minFeeA = itemDI != null? getBigInteger(itemDI) : null;

        itemDI = map.get(new UnsignedInteger(1));
        BigInteger minFeeB = itemDI != null? getBigInteger(itemDI) : null;

        itemDI = map.get(new UnsignedInteger(2));
        Long maxBlockSize = itemDI != null? toUnsignedLong(itemDI) : null;

        itemDI = map.get(new UnsignedInteger(3));
        Long maxTxSize = itemDI != null? toUnsignedLong(itemDI) : null;

        itemDI = map.get(new UnsignedInteger(4));
        Integer maxBlockHeaderSize = itemDI != null? toUnsignedInt(itemDI) : null;

        itemDI = map.get(new UnsignedInteger(5));
        BigInteger keyDeposit = itemDI != null? getBigInteger(itemDI): null;

        itemDI = map.get(new UnsignedInteger(6));
        BigInteger poolDeposit = itemDI != null? getBigInteger(itemDI): null;

        itemDI = map.get(new UnsignedInteger(7));
        Long maxEpoch = itemDI != null? toUnsignedLong(itemDI): null;

        itemDI = map.get(new UnsignedInteger(8));
        Integer nOpt = itemDI != null? toUnsignedInt(itemDI): null;

        itemDI = map.get(new UnsignedInteger(9));
        Rational poolPledgeInfluence = itemDI != null? toRational((RationalNumber) itemDI): null;

        itemDI = map.get(new UnsignedInteger(10));
        UnitInterval expansionRate = itemDI != null? toUnitInterval((RationalNumber) itemDI): null;

        itemDI = map.get(new UnsignedInteger(11));
        UnitInterval treasuryGrowthRate = itemDI != null? toUnitInterval((RationalNumber) itemDI): null;

        itemDI = map.get(new UnsignedInteger(12));
        UnitInterval decentralizationParam = itemDI != null? toUnitInterval((RationalNumber) itemDI): null;

//      $nonce /= [ 0 // 1, bytes .size 32 ]
        itemDI = map.get(new UnsignedInteger(13)); //Removed
        Nonce extraEntropy = itemDI != null? Nonce.deserialize(itemDI): null;

        ProtocolVersion protocolVersion = null;
        itemDI = map.get(new UnsignedInteger(14));
        if (itemDI != null)
            protocolVersion = ProtocolVersion.deserialize((Array) itemDI);

        itemDI = map.get(new UnsignedInteger(15)); //Removed
        BigInteger minUtxo = itemDI != null? getBigInteger(itemDI): null;

        itemDI = map.get(new UnsignedInteger(16));
        BigInteger minPoolCost = itemDI != null? getBigInteger(itemDI): null;

        itemDI = map.get(new UnsignedInteger(17));
        BigInteger adaPerUtxoBytes = itemDI != null? getBigInteger(itemDI): null;

        //CostModels
        itemDI = map.get(new UnsignedInteger(18));
        CostMdls costMdls = itemDI != null? CostMdls.deserialize(itemDI): null;

        itemDI = map.get(new UnsignedInteger(19));
        ExUnitPrices executionCosts = itemDI != null? ExUnitPrices.deserialize(itemDI) : null;

        itemDI = map.get(new UnsignedInteger(20));
        ExUnits maxTxExUnits = itemDI != null? ExUnits.deserialize((Array)itemDI) : null;

        itemDI = map.get(new UnsignedInteger(21));
        ExUnits maxBlockExUnits = itemDI != null? ExUnits.deserialize((Array)itemDI) : null;


        itemDI = map.get(new UnsignedInteger(22));
        Long maxValueSize = itemDI != null? toUnsignedLong(itemDI): null;

        itemDI = map.get(new UnsignedInteger(23));
        Integer collateralPercent = itemDI != null? toUnsignedInt(itemDI): null;

        itemDI = map.get(new UnsignedInteger(24));
        Integer maxCollateralPercent = itemDI != null? toUnsignedInt(itemDI): null;

        itemDI = map.get(new UnsignedInteger(25));
        PoolVotingThresholds poolVotingThresholds = itemDI != null
                ? PoolVotingThresholds.deserialize((Array) itemDI) : null;

        itemDI = map.get(new UnsignedInteger(26));
        DRepVotingThresholds drepVotingThresholds = itemDI != null
                ? DRepVotingThresholds.deserialize((Array) itemDI) : null;

        itemDI = map.get(new UnsignedInteger(27));
        Integer committeeMinSize = itemDI != null? toUnsignedInt(itemDI): null;

        itemDI = map.get(new UnsignedInteger(28));
        Long committeeMaxTermLength = itemDI != null? toUnsignedLong(itemDI): null;

        itemDI = map.get(new UnsignedInteger(29));
        Long govActionLifetime = itemDI != null? toUnsignedLong(itemDI): null;

        itemDI = map.get(new UnsignedInteger(30));
        BigInteger govActionDeposit = itemDI != null? getBigInteger(itemDI): null;

        itemDI = map.get(new UnsignedInteger(31));
        BigInteger drepDeposit = itemDI != null? getBigInteger(itemDI): null;

        itemDI = map.get(new UnsignedInteger(32));
        Long drepActivity = itemDI != null? toUnsignedLong(itemDI): null;

        itemDI = map.get(new UnsignedInteger(33));
        Rational minFeeRefScriptCostPerByte = itemDI != null? toRational((RationalNumber) itemDI): null;

        ProtocolParamUpdate protocolParamUpdate = ProtocolParamUpdate.builder()
                .minFeeA(minFeeA)
                .minFeeB(minFeeB)
                .maxBlockSizeLong(maxBlockSize)
                .maxTxSizeLong(maxTxSize)
                .maxBlockHeaderSize(maxBlockHeaderSize)
                .keyDeposit(keyDeposit)
                .poolDeposit(poolDeposit)
                .maxEpochLong(maxEpoch)
                .nOpt(nOpt)
                .poolPledgeInfluence(poolPledgeInfluence)
                .expansionRate(expansionRate)
                .treasuryGrowthRate(treasuryGrowthRate)
                .decentralisationConstant(decentralizationParam)
                .extraEntropy(extraEntropy)
                .protocolVersion(protocolVersion)
                .minUtxo(minUtxo)
                .minPoolCost(minPoolCost)
                .adaPerUtxoByte(adaPerUtxoBytes)
                .costModels(costMdls)
                .executionCosts(executionCosts)
                .maxTxExUnits(maxTxExUnits)
                .maxBlockExUnits(maxBlockExUnits)
                .maxValSize(maxValueSize)
                .collateralPercent(collateralPercent)
                .maxCollateralInputs(maxCollateralPercent)
                .poolVotingThresholds(poolVotingThresholds)
                .drepVotingThresholds(drepVotingThresholds)
                .committeeMinSize(committeeMinSize)
                .committeeMaxTermLength(committeeMaxTermLength)
                .govActionLifetime(govActionLifetime)
                .govActionDeposit(govActionDeposit)
                .drepDeposit(drepDeposit)
                .drepActivity(drepActivity)
                .minFeeRefScriptCostPerByte(minFeeRefScriptCostPerByte)
                .build();

        return protocolParamUpdate;

    }
}
