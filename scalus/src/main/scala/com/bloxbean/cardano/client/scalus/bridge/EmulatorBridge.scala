package com.bloxbean.cardano.client.scalus.bridge

import com.bloxbean.cardano.client.api.model.{Amount, ProtocolParams as CclProtocolParams, Utxo}
import scalus.cardano.ledger.{Transaction as ScalusTx, *}
import scalus.cardano.ledger.rules.*
import scalus.cardano.node.Emulator

import java.math.BigInteger
import java.util
import java.util.Optional

/**
 * Java-friendly facade for Scalus Emulator.
 * All public methods accept/return only Java types.
 */
object EmulatorBridge:

  private val GENESIS_TX_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

  /**
   * Create a new emulator instance.
   *
   * @param protocolParams CCL ProtocolParams
   * @param slotConfig     opaque SlotConfigHandle
   * @param initialFunds   map of bech32 address → Amount (lovelace)
   * @param networkId      0 = testnet, 1 = mainnet
   * @return opaque EmulatorHandle
   */
  def create(
      protocolParams: CclProtocolParams,
      slotConfig: SlotConfigHandle,
      initialFunds: util.Map[String, Amount],
      networkId: Int
  ): EmulatorHandle =
    val scalusParams = ProtocolParamsBridge.toScalusProtocolParams(protocolParams)
    val sc = if slotConfig != null then slotConfig.inner else SlotConfig.preview

    val network = ProtocolParamsBridge.toNetwork(networkId)

    val initialUtxos: scala.collection.immutable.Map[TransactionInput, TransactionOutput] =
      if initialFunds != null && !initialFunds.isEmpty then
        buildInitialUtxos(initialFunds)
      else
        scala.collection.immutable.Map.empty

    val env = UtxoEnv(0L, scalusParams, CertState.empty, network)
    val context = Context(Coin.zero, env, sc, EvaluatorMode.Validate)

    val emulator = new Emulator(
      initialUtxos,
      context,
      Emulator.defaultValidators,
      Emulator.defaultMutators
    )

    new EmulatorHandle(emulator)

  /**
   * Submit a serialized transaction to the emulator.
   *
   * @return SubmitResult with success/failure, txHash, errorMessage
   */
  def submit(handle: EmulatorHandle, txCbor: Array[Byte], protocolParams: CclProtocolParams): SubmitResult =
    try
      given ProtocolVersion = ProtocolParamsBridge.extractProtocolVersion(protocolParams)
      val scalusTx = ScalusTx.fromCbor(txCbor)
      val result = handle.inner.submitSync(scalusTx)

      result match
        case Right(txHashBytes) =>
          new SubmitResult(true, txHashBytes.toHex, null)
        case Left(error) =>
          new SubmitResult(false, null, error.toString)
    catch
      case e: Exception =>
        new SubmitResult(false, null, "Submit error: " + e.getMessage)

  /**
   * Get UTxOs for a given address from the emulator.
   */
  def getUtxos(handle: EmulatorHandle, address: String): util.List[Utxo] =
    val result = new util.ArrayList[Utxo]()
    val utxoMap = handle.inner.utxos
    val scalaIter = utxoMap.iterator
    while scalaIter.hasNext do
      val kv = scalaIter.next()
      val txInput = kv._1
      val txOutput = kv._2
      val outputAddr = txOutput.address.encode.getOrElse("")
      if outputAddr == address then
        result.add(convertToUtxo(txInput, txOutput))
    result

  /**
   * Get a specific transaction output from the emulator.
   */
  def getTxOutput(handle: EmulatorHandle, txHash: String, outputIndex: Int): Optional[Utxo] =
    val lookupInput = TransactionInput(TransactionHash.fromHex(txHash), outputIndex)
    val utxoMap = handle.inner.utxos
    val outputOpt = utxoMap.get(lookupInput)
    if outputOpt.isDefined then
      Optional.of(convertToUtxo(lookupInput, outputOpt.get))
    else
      Optional.empty()

  /**
   * Set the emulator's current slot.
   */
  def setSlot(handle: EmulatorHandle, slot: Long): Unit =
    handle.inner.setSlot(slot)

  /**
   * Create a snapshot of the emulator for rollback testing.
   */
  def snapshot(handle: EmulatorHandle): EmulatorHandle =
    new EmulatorHandle(handle.inner.snapshot())

  // ---- Private helpers ----

  private def buildInitialUtxos(
      funds: util.Map[String, Amount]
  ): scala.collection.immutable.Map[TransactionInput, TransactionOutput] =
    val builder = scala.collection.immutable.Map.newBuilder[TransactionInput, TransactionOutput]
    var index = 0

    val iter = funds.entrySet().iterator()
    while iter.hasNext do
      val entry = iter.next()
      val address = entry.getKey
      val amount = entry.getValue

      val input = TransactionInput(TransactionHash.fromHex(GENESIS_TX_HASH), index)
      val scalusAddr = scalus.cardano.address.Address.fromBech32(address)
      val value = Value.lovelace(amount.getQuantity.longValue())
      val output = TransactionOutput(scalusAddr, value)

      builder += (input -> output)
      index += 1

    builder.result()

  private def convertToUtxo(input: TransactionInput, output: TransactionOutput): Utxo =
    val txHash = input.transactionId.toHex
    val outputIndex = input.index
    val address = output.address.encode.getOrElse("unknown")

    Utxo.builder()
      .txHash(txHash)
      .outputIndex(outputIndex)
      .address(address)
      .amount(util.List.of(Amount.lovelace(BigInteger.valueOf(output.value.coin.value))))
      .build()
