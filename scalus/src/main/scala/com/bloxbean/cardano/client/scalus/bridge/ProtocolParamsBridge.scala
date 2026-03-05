package com.bloxbean.cardano.client.scalus.bridge

import com.bloxbean.cardano.client.api.model.ProtocolParams as CclProtocolParams
import scalus.bloxbean.Interop
import scalus.cardano.ledger.*

import java.math.BigDecimal

/**
 * Converts CCL ProtocolParams to Scalus ProtocolParams.
 * All Scala types are internal — Java code never sees them.
 */
private[bridge] object ProtocolParamsBridge:

  def toScalusProtocolParams(pp: CclProtocolParams): scalus.cardano.ledger.ProtocolParams =
    val costModels = convertCostModels(pp)

    val rationalPrecision = 6
    val exUnitPrices = ExUnitPrices(
      NonNegativeInterval(toDouble(pp.getPriceMem), rationalPrecision),
      NonNegativeInterval(toDouble(pp.getPriceStep), rationalPrecision)
    )

    val maxBlockExUnits = ExUnits(
      parseLong(pp.getMaxBlockExSteps),
      parseLong(pp.getMaxBlockExMem)
    )

    val maxTxExUnits = ExUnits(
      parseLong(pp.getMaxTxExSteps),
      parseLong(pp.getMaxTxExMem)
    )

    val dRepVotingThresholds = DRepVotingThresholds(
      toUnitInterval(pp.getDvtMotionNoConfidence),
      toUnitInterval(pp.getDvtCommitteeNormal),
      toUnitInterval(pp.getDvtCommitteeNoConfidence),
      toUnitInterval(pp.getDvtUpdateToConstitution),
      toUnitInterval(pp.getDvtHardForkInitiation),
      toUnitInterval(pp.getDvtPPNetworkGroup),
      toUnitInterval(pp.getDvtPPEconomicGroup),
      toUnitInterval(pp.getDvtPPTechnicalGroup),
      toUnitInterval(pp.getDvtPPGovGroup),
      toUnitInterval(pp.getDvtTreasuryWithdrawal)
    )

    val poolVotingThresholds = PoolVotingThresholds(
      toUnitInterval(pp.getPvtMotionNoConfidence),
      toUnitInterval(pp.getPvtCommitteeNormal),
      toUnitInterval(pp.getPvtCommitteeNoConfidence),
      toUnitInterval(pp.getPvtHardForkInitiation),
      toUnitInterval(pp.getPvtPPSecurityGroup)
    )

    val protocolVersion = ProtocolVersion(
      intOrDefault(pp.getProtocolMajorVer, 10),
      intOrDefault(pp.getProtocolMinorVer, 0)
    )

    scalus.cardano.ledger.ProtocolParams(
      /* 1  collateralPercentage    */ toLong(pp.getCollateralPercent),
      /* 2  committeeMaxTermLength   */ intOrDefault(pp.getCommitteeMaxTermLength, 146).toLong,
      /* 3  committeeMinSize         */ intOrDefault(pp.getCommitteeMinSize, 7).toLong,
      /* 4  costModels               */ costModels,
      /* 5  dRepActivity             */ intOrDefault(pp.getDrepActivity, 20).toLong,
      /* 6  dRepDeposit              */ if pp.getDrepDeposit != null then pp.getDrepDeposit.longValue() else 500000000L,
      /* 7  dRepVotingThresholds     */ dRepVotingThresholds,
      /* 8  executionUnitPrices      */ exUnitPrices,
      /* 9  govActionDeposit         */ if pp.getGovActionDeposit != null then pp.getGovActionDeposit.longValue() else 100000000000L,
      /* 10 govActionLifetime        */ intOrDefault(pp.getGovActionLifetime, 6).toLong,
      /* 11 maxBlockBodySize         */ intOrDefault(pp.getMaxBlockSize, 90112).toLong,
      /* 12 maxBlockExecutionUnits   */ maxBlockExUnits,
      /* 13 maxBlockHeaderSize       */ intOrDefault(pp.getMaxBlockHeaderSize, 1100).toLong,
      /* 14 maxCollateralInputs      */ intOrDefault(pp.getMaxCollateralInputs, 3).toLong,
      /* 15 maxTxExecutionUnits      */ maxTxExUnits,
      /* 16 maxTxSize                */ intOrDefault(pp.getMaxTxSize, 16384).toLong,
      /* 17 maxValueSize             */ parseLong(pp.getMaxValSize),
      /* 18 minFeeRefScriptCostPerByte */ if pp.getMinFeeRefScriptCostPerByte != null then pp.getMinFeeRefScriptCostPerByte.longValue() else 15L,
      /* 19 minPoolCost              */ parseLong(pp.getMinPoolCost),
      /* 20 monetaryExpansion        */ toDouble(pp.getRho),
      /* 21 poolPledgeInfluence      */ toDouble(pp.getA0),
      /* 22 poolRetireMaxEpoch       */ intOrDefault(pp.getEMax, 18).toLong,
      /* 23 poolVotingThresholds     */ poolVotingThresholds,
      /* 24 protocolVersion          */ protocolVersion,
      /* 25 stakeAddressDeposit      */ parseLong(pp.getKeyDeposit),
      /* 26 stakePoolDeposit         */ parseLong(pp.getPoolDeposit),
      /* 27 stakePoolTargetNum       */ intOrDefault(pp.getNOpt, 500).toLong,
      /* 28 treasuryCut              */ toDouble(pp.getTau),
      /* 29 txFeeFixed               */ intOrDefault(pp.getMinFeeB, 155381).toLong,
      /* 30 txFeePerByte             */ intOrDefault(pp.getMinFeeA, 44).toLong,
      /* 31 utxoCostPerByte          */ parseLong(pp.getCoinsPerUtxoSize)
    )

  private def convertCostModels(pp: CclProtocolParams): CostModels =
    if pp.getCostModels == null || pp.getCostModels.isEmpty then
      CostModels(scala.collection.immutable.Map.empty)
    else
      Interop.getCostModels(pp)

  private def toUnitInterval(value: BigDecimal): UnitInterval =
    if value == null then UnitInterval.zero
    else UnitInterval.fromDouble(value.doubleValue())

  private def toDouble(value: BigDecimal): Double =
    if value != null then value.doubleValue() else 0.0

  private def toLong(value: BigDecimal): Long =
    if value != null then value.longValue() else 0L

  private def intOrDefault(value: Integer, default: Int): Int =
    if value != null then value.intValue() else default

  private def parseLong(value: String): Long =
    if value == null || value.isEmpty then 0L
    else java.lang.Long.parseLong(value)

  private[bridge] def extractProtocolVersion(pp: CclProtocolParams): ProtocolVersion =
    ProtocolVersion(
      intOrDefault(pp.getProtocolMajorVer, 10),
      intOrDefault(pp.getProtocolMinorVer, 0)
    )

  private[bridge] def toNetwork(networkId: Int): scalus.cardano.address.Network =
    if networkId == 1 then scalus.cardano.address.Network.Mainnet
    else scalus.cardano.address.Network.Testnet
