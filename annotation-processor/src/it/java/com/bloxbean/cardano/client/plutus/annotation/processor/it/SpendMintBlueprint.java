package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/spend_mint_aiken_v1_0_29_alpha_16fb02e.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.spend_mint")
@AikenStdlib(AikenStdlibVersion.V1)
public interface SpendMintBlueprint {
}
