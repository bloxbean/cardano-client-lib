package com.bloxbean.cardano.client.scalus.bridge

import scalus.cardano.ledger.SlotConfig

/**
 * Java-friendly factory for SlotConfig.
 * Returns opaque SlotConfigHandle — no Scalus types leak to Java.
 */
object SlotConfigBridge:

  def preview(): SlotConfigHandle =
    new SlotConfigHandle(SlotConfig.preview)

  def preprod(): SlotConfigHandle =
    new SlotConfigHandle(SlotConfig.preprod)

  def mainnet(): SlotConfigHandle =
    new SlotConfigHandle(SlotConfig.mainnet)

  def custom(zeroTime: Long, zeroSlot: Long, slotLength: Int): SlotConfigHandle =
    new SlotConfigHandle(SlotConfig(zeroTime, zeroSlot, slotLength))
