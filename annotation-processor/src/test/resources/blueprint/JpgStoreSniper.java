package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

/**
 * JpgStore Sniper contract (Plutus V3) test marker interface.
 *
 * <p>Deliberately omits {@code @AikenStdlib} to test implicit V3 default.</p>
 */
@Blueprint(fileInResources = "blueprint/jpgstore_sniper_aiken_v1_1_21_42babe5.json",
           packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.jpgstoresniper")
public interface JpgStoreSniper {
}
