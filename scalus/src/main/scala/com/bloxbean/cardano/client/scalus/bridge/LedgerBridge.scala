package com.bloxbean.cardano.client.scalus.bridge

import com.bloxbean.cardano.client.api.model.{ProtocolParams as CclProtocolParams, Utxo}
import scalus.bloxbean.ScriptSupplier
import scalus.cardano.ledger.{Transaction as ScalusTx, *}
import scalus.cardano.ledger.rules.*

import java.util

/**
 * Java-friendly facade for Scalus ledger validation (CardanoMutator.transit).
 * Accepts only Java/CCL types, returns TransitResult.
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
