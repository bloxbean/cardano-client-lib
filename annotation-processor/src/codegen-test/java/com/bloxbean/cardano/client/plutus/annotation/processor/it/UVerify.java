package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * UVerify contract (Plutus v3) integration test marker interface.
 *
 * This blueprint was generated using Aiken v1.1.21+42babe5 and represents
 * the UVerify validator system including connected goods and social hub
 * functionality for decentralized verification and social features.
 * Uses Plutus v3 for enhanced functionality.
 *
 * <p><b>Validators:</b></p>
 * <ul>
 *   <li>connected_goods.mint - Minting policy for connected goods NFTs</li>
 *   <li>connected_goods.spend - Spending validator for connected goods UTXOs</li>
 *   <li>social_hub.mint - Minting policy for social hub tokens</li>
 *   <li>social_hub.spend - Spending validator for social hub UTXOs</li>
 *   <li>library.spend - Generic library spending validator</li>
 * </ul>
 */
@Blueprint(fileInResources = "blueprint/uverify_aiken_v1_1_21_42babe5.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.uverify")
public interface UVerify {
}
