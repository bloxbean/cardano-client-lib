package com.bloxbean.cardano.client.scalus.bridge

import scalus.cardano.ledger.SlotConfig
import scalus.cardano.node.Emulator

/**
 * Result of ledger validation (transit).
 * Java-facing — no Scala types exposed.
 */
class TransitResult(
    val isSuccess: Boolean,
    val errorMessage: String,
    val errorClassName: String
)

/**
 * Result of emulator transaction submission.
 */
class SubmitResult(
    val isSuccess: Boolean,
    val txHash: String,
    val errorMessage: String
)

/**
 * Opaque handle wrapping Scalus SlotConfig.
 * Java code treats this as an opaque token.
 */
class SlotConfigHandle private[bridge] (private[bridge] val inner: SlotConfig)

/**
 * Opaque handle wrapping Scalus Emulator.
 * Java code treats this as an opaque token.
 */
class EmulatorHandle private[bridge] (private[bridge] val inner: Emulator)
