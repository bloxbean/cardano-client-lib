package com.bloxbean.cardano.client.scalus.bridge

import com.bloxbean.cardano.client.api.model.{ProtocolParams as CclProtocolParams, Utxo}
import scalus.bloxbean.ScriptSupplier
import scalus.cardano.ledger.{Transaction as ScalusTx, *}
import scalus.cardano.ledger.rules.*

import java.util
import scala.jdk.CollectionConverters.*

/**
 * Java-friendly facade for Scalus ledger validation and script evaluation.
 * Accepts only Java/CCL types — no Scala types leak to Java callers.
 */
object LedgerBridge:

  /**
   * Validate a transaction against full Cardano ledger rules (without script supplier).
   */
  def validate(
      txCbor: Array[Byte],
      protocolParams: CclProtocolParams,
      inputUtxos: util.Set[Utxo],
      currentSlot: Long,
      slotConfig: SlotConfigHandle,
      networkId: Int
  ): TransitResult =
    validate(txCbor, protocolParams, inputUtxos, currentSlot, slotConfig, networkId, null)

  /**
   * Validate a transaction against full Cardano ledger rules.
   *
   * @param txCbor           serialized transaction CBOR bytes
   * @param protocolParams   CCL ProtocolParams
   * @param inputUtxos       set of resolved input UTxOs (CCL Utxo)
   * @param currentSlot      current slot (e.g. from validity start interval)
   * @param slotConfig       opaque SlotConfigHandle from SlotConfigBridge
   * @param networkId        0 = testnet, 1 = mainnet
   * @param scriptSupplier   nullable ScriptSupplier for reference script resolution
   * @return TransitResult with success/failure and error details
   */
  def validate(
      txCbor: Array[Byte],
      protocolParams: CclProtocolParams,
      inputUtxos: util.Set[Utxo],
      currentSlot: Long,
      slotConfig: SlotConfigHandle,
      networkId: Int,
      scriptSupplier: ScriptSupplier
  ): TransitResult =
    try
      val scalusParams = ProtocolParamsBridge.toScalusProtocolParams(protocolParams)

      given ProtocolVersion = ProtocolParamsBridge.extractProtocolVersion(protocolParams)
      val scalusTx = ScalusTx.fromCbor(txCbor)

      val scalusUtxos = UtxoBridge.convert(inputUtxos, scriptSupplier)

      val network = ProtocolParamsBridge.toNetwork(networkId)

      val sc = if slotConfig != null then slotConfig.inner else SlotConfig.preview

      val env = UtxoEnv(currentSlot, scalusParams, CertState.empty, network)

      val context = Context(
        Coin.zero,
        env,
        sc,
        EvaluatorMode.Validate
      )

      val state = State(
        scalusUtxos,
        CertState.empty,
        Coin.zero,   // deposited
        Coin.zero,   // fees
        new Array[io.bullet.borer.Dom.Element](0), // govState (empty)
        scala.collection.immutable.Map.empty,       // stakeDistribution
        Coin.zero    // donation
      )

      val result = CardanoMutator.transit(context, state, scalusTx)

      result match
        case Right(_) =>
          new TransitResult(true, null, null)
        case Left(error) =>
          new TransitResult(false, error.getMessage, error.getClass.getSimpleName)

    catch
      case e: Exception =>
        new TransitResult(false, "Validation error: " + e.getMessage, e.getClass.getSimpleName)

  /**
   * Evaluate Plutus scripts in a transaction and compute execution units (ExUnits).
   * Uses EvaluatorMode.EvaluateAndComputeCost to actually run scripts and measure costs.
   *
   * @param txCbor         serialized transaction CBOR bytes
   * @param protocolParams CCL ProtocolParams
   * @param inputUtxos     set of resolved input UTxOs (CCL Utxo)
   * @param currentSlot    current slot
   * @param slotConfig     opaque SlotConfigHandle from SlotConfigBridge
   * @param networkId      0 = testnet, 1 = mainnet
   * @return Java List of EvaluationEntry with computed ExUnits per redeemer
   * @throws Exception on evaluation failure
   */
  def evaluate(
      txCbor: Array[Byte],
      protocolParams: CclProtocolParams,
      inputUtxos: util.Set[Utxo],
      currentSlot: Long,
      slotConfig: SlotConfigHandle,
      networkId: Int
  ): util.List[EvaluationEntry] =
    val scalusParams = ProtocolParamsBridge.toScalusProtocolParams(protocolParams)
    given ProtocolVersion = ProtocolParamsBridge.extractProtocolVersion(protocolParams)

    val scalusTx = ScalusTx.fromCbor(txCbor)
    val scalusUtxos = UtxoBridge.convert(inputUtxos, null)
    val sc = if slotConfig != null then slotConfig.inner else SlotConfig.preview

    val maxExUnits = scalusParams.maxTxExecutionUnits
    val majorVersion = MajorProtocolVersion(ProtocolParamsBridge.extractProtocolVersion(protocolParams).major)
    val costModels = scalus.bloxbean.Interop.getCostModels(protocolParams)
    val evaluator = PlutusScriptEvaluator(sc, maxExUnits, majorVersion, costModels, EvaluatorMode.EvaluateAndComputeCost)

    val redeemers: scala.collection.immutable.Seq[Redeemer] = evaluator.evalPlutusScripts(scalusTx, scalusUtxos)

    val entries = redeemers.map { r =>
      val tagStr = r.tag.toString.toLowerCase
      new EvaluationEntry(tagStr, r.index, r.exUnits.memory, r.exUnits.steps)
    }
    entries.asJava
