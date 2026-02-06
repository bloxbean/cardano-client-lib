package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * Gift Card contract (CIP-57) test marker interface.
 *
 * <p>This blueprint was generated using Aiken v1.1.21 and represents
 * a gift card contract that can be used to create redeemable locked assets.</p>
 *
 * <p><strong>What This Tests:</strong></p>
 * <ul>
 *   <li>CIP-57 generic naming strategy for blueprint type conversion</li>
 *   <li>Multi-validator contract with shared datum types</li>
 *   <li>Modern Aiken (v1.1.x) blueprint structure with namespaced types</li>
 * </ul>
 *
 * @see <a href="https://github.com/aiken-lang/gift_card">aiken-lang/gift_card</a>
 * @see <a href="https://cips.cardano.org/cip/CIP-57">CIP-57 Plutus Contract Blueprints</a>
 */
@Blueprint(fileInResources = "blueprint/giftcard_aiken_v1_1_21_42babe5.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.giftcard")
public interface GiftCard {
}
