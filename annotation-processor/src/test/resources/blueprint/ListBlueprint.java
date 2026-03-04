package com.bloxbean.cardano.client.plutus.annotation.blueprint.model;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/ListBlueprint_aiken_v1_0_21_alpha_4b04517.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.model.listblueprint")
@AikenStdlib(AikenStdlibVersion.V1)
public interface ListBlueprint {
}
