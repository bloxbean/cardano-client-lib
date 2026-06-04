package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * UVerify contract (Plutus v3) test marker interface.
 *
 * This blueprint contains validators for connected goods and social hub
 * functionality in the UVerify decentralized verification system.
 * Compiled with Aiken v1.1.21+42babe5 targeting Plutus v3.
 */
@Blueprint(fileInResources = "blueprint/uverify_aiken_v1_1_21_42babe5.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.uverify")
public interface UVerify {
}
