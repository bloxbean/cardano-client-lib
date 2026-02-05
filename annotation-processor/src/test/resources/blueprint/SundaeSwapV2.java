package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * SundaeSwap DEX contract (Plutus v2) test marker interface.
 *
 * This blueprint contains validators for a decentralized exchange including
 * pool management, order handling, and oracle functionality.
 *
 * @see <a href="https://sundaeswap.finance/">SundaeSwap</a>
 */
@Blueprint(fileInResources = "blueprint/sundaeswap_aiken_v1_0_26_alpha_075668b.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswap")
public interface SundaeSwapV2 {
}
