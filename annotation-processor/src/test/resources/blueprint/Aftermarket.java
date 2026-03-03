package com.bloxbean.cardano.client.plutus.annotation.blueprint.model;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/aftermarket_aiken_v1_0_26_alpha_075668b.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.model.aftermarket")
@AikenStdlib(AikenStdlibVersion.V1)
public interface Aftermarket {
}
