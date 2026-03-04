package com.bloxbean.cardano.client.scalus.bridge

import com.bloxbean.cardano.client.api.model.Utxo
import scalus.bloxbean.{Interop, ScriptSupplier, NoScriptSupplier}
import scalus.cardano.ledger.{TransactionInput, TransactionOutput}

import java.util

/**
 * Converts CCL Utxo objects to Scalus UTxO map.
 * All Scala types are internal — Java code never sees them.
 */
private[bridge] object UtxoBridge:

  /**
   * Convert a Java Set of CCL UTxOs to a Scalus immutable Map.
   * @param utxos CCL UTxO set
   * @param scriptSupplier Scalus ScriptSupplier, or null for NoScriptSupplier
   */
  def convert(
      utxos: util.Set[Utxo],
      scriptSupplier: ScriptSupplier
  ): scala.collection.immutable.Map[TransactionInput, TransactionOutput] =
    val supplier: ScriptSupplier =
      if scriptSupplier != null then scriptSupplier
      else new NoScriptSupplier()

    val builder = scala.collection.immutable.Map.newBuilder[TransactionInput, TransactionOutput]
    val iter = utxos.iterator()
    while iter.hasNext do
      val utxo = iter.next()
      val entry = Interop.toUtxoEntry(utxo, supplier)
      builder += entry

    builder.result()
