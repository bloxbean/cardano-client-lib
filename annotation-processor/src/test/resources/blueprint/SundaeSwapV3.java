package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * SundaeSwap DEX contract (Plutus v3) test marker interface.
 *
 * This blueprint contains validators for a decentralized exchange including
 * pool management, order handling, and oracle functionality.
 * Compiled with Aiken v1.1.21+42babe5 targeting Plutus v3.
 *
 * @see <a href="https://sundaeswap.finance/">SundaeSwap</a>
 */
@Blueprint(fileInResources = "blueprint/sundaeswap_aiken_v1_1_21.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswapv3")
public interface SundaeSwapV3 {
}
