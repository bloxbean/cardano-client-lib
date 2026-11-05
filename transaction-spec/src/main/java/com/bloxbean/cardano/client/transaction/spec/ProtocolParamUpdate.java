package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.ExUnitPrices;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
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
import lombok.*;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toRationalNumber;

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

        } catch (Exception e) {
            throw new CborSerializationException("Serialization error", e);
        }

        return map;
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
     * @return the model, which keeps the coins per UTxO of key 17 in {@code adaPerUtxoByte} whatever the era
     * @throws CborDeserializationException if the cost models, the execution prices or units, or the nonce are not what
     *                                      their models accept
     */
    public static ProtocolParamUpdate from(RawProtocolParamUpdate raw) throws CborDeserializationException {
        Optional<RawExUnits> maxTxExUnits = raw.maxTxExUnits();
        Optional<RawExUnits> maxBlockExUnits = raw.maxBlockExUnits();
        Optional<RawCostModels> costModels = raw.costModels();
        Optional<RawNonce> extraEntropy = raw.extraEntropy();
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
                .costModels(costModels.isPresent() ? CostMdls.from(costModels.get()) : null)
                .executionCosts(raw.executionCosts().map(ExUnitPrices::from).orElse(null))
                .maxTxExUnits(maxTxExUnits.isPresent() ? ExUnits.from(maxTxExUnits.get()) : null)
                .maxBlockExUnits(maxBlockExUnits.isPresent() ? ExUnits.from(maxBlockExUnits.get()) : null)
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
                .build();
    }
}
