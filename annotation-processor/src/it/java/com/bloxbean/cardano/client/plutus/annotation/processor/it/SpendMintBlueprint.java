package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

@Blueprint(fileInResources = "blueprint/spend_mint.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.spend_mint")
public interface SpendMintBlueprint {
}
