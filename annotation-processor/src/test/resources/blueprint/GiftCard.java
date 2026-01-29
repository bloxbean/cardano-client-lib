package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * Gift Card contract (CIP-53) integration test marker interface.
 *
 * This blueprint was generated using Aiken v1.1.21+42babe5 and represents
 * a CIP-53 compliant gift card contract that can be used to create redeemable locked assets.
 *
 * @see <a href="https://github.com/aiken-lang/gift_card">aiken-lang/gift_card</a>
 */
@Blueprint(fileInResources = "blueprint/giftcard.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.giftcard")
public interface GiftCard {
}
