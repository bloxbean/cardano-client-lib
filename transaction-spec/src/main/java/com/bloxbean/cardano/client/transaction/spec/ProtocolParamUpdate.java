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
import lombok.*;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.transaction.util.CborSerializationUtil.*;
import static com.bloxbean.cardano.client.transaction.util.RationalNumberUtil.toRational;
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
    private Integer maxBlockSize; //2
    private Integer maxTxSize; //3
    private Integer maxBlockHeaderSize; //4
    private BigInteger keyDeposit; //5
    private BigInteger poolDeposit; //6
    private Integer maxEpoch; //7
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
                map.put(new UnsignedInteger(9),
                        new RationalNumber(bigIntegerToDataItem(poolPledgeInfluence.getNumerator()),
                                bigIntegerToDataItem(poolPledgeInfluence.getDenominator())));
            }

            if (expansionRate != null) {
                map.put(new UnsignedInteger(10),
                        new RationalNumber(bigIntegerToDataItem(expansionRate.getNumerator()),
                                bigIntegerToDataItem(expansionRate.getDenominator())));
            }

            if (treasuryGrowthRate != null) {
                map.put(new UnsignedInteger(11),
                        new RationalNumber(bigIntegerToDataItem(treasuryGrowthRate.getNumerator()),
                                bigIntegerToDataItem(treasuryGrowthRate.getDenominator())));
            }

            if (decentralisationConstant != null) {
                map.put(new UnsignedInteger(12),
                        new RationalNumber(bigIntegerToDataItem(decentralisationConstant.getNumerator()),
                                bigIntegerToDataItem(decentralisationConstant.getDenominator())));
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
        Integer maxBlockSize = itemDI != null? toInt(itemDI) : null;

        itemDI = map.get(new UnsignedInteger(3));
        Integer maxTxSize = itemDI != null? toInt(itemDI) : null;

        itemDI = map.get(new UnsignedInteger(4));
        Integer maxBlockHeaderSize = itemDI != null? toInt(itemDI) : null;

        itemDI = map.get(new UnsignedInteger(5));
        BigInteger keyDeposit = itemDI != null? getBigInteger(itemDI): null;

        itemDI = map.get(new UnsignedInteger(6));
        BigInteger poolDeposit = itemDI != null? getBigInteger(itemDI): null;

        itemDI = map.get(new UnsignedInteger(7));
        Integer maxEpoch = itemDI != null? toInt(itemDI): null;

        itemDI = map.get(new UnsignedInteger(8));
        Integer nOpt = itemDI != null? toInt(itemDI): null;

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
        if (itemDI != null) {
            List<DataItem> protocolVersionDIList =((Array) itemDI).getDataItems();
            int majorVersion = toInt(protocolVersionDIList.get(0));
            int minorVersion = toInt(protocolVersionDIList.get(1));
            protocolVersion = new ProtocolVersion(majorVersion, minorVersion);
        }

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
        Long maxValueSize = itemDI != null? toLong(itemDI): null;

        itemDI = map.get(new UnsignedInteger(23));
        Integer collateralPercent = itemDI != null? toInt(itemDI): null;

        itemDI = map.get(new UnsignedInteger(24));
        Integer maxCollateralPercent = itemDI != null? toInt(itemDI): null;

        ProtocolParamUpdate protocolParamUpdate = ProtocolParamUpdate.builder()
                .minFeeA(minFeeA)
                .minFeeB(minFeeB)
                .maxBlockSize(maxBlockSize)
                .maxTxSize(maxTxSize)
                .maxBlockHeaderSize(maxBlockHeaderSize)
                .keyDeposit(keyDeposit)
                .poolDeposit(poolDeposit)
                .maxEpoch(maxEpoch)
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
                .build();

        return protocolParamUpdate;

    }
}
