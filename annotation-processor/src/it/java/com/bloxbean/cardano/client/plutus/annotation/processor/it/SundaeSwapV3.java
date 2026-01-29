package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * SundaeSwap DEX contract (Plutus v3) integration test marker interface.
 *
 * This blueprint was generated using Aiken v1.1.21+42babe5 and represents
 * an experimental port of SundaeSwap to Aiken, including pool management,
 * order handling, and oracle functionality for a decentralized exchange.
 * Uses Plutus v3 for enhanced functionality.
 *
 * @see <a href="https://sundaeswap.finance/">SundaeSwap</a>
 */
@Blueprint(fileInResources = "blueprint/sundaeswap_aiken_v1.1.2142babe5.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswapv3")
public interface SundaeSwapV3 {
}
