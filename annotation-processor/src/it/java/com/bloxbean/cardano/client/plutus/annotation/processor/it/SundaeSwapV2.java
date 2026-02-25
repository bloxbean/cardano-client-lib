package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * SundaeSwap DEX contract (Plutus v2) integration test marker interface.
 *
 * This blueprint was generated using Aiken v1.0.26-alpha+075668b and represents
 * an experimental port of SundaeSwap to Aiken, including pool management,
 * order handling, and oracle functionality for a decentralized exchange.
 *
 * @see <a href="https://sundaeswap.finance/">SundaeSwap</a>
 */
@Blueprint(fileInResources = "blueprint/sundaeswap_aiken_v1_0_26_alpha_075668b.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswap")
public interface SundaeSwapV2 {
}
