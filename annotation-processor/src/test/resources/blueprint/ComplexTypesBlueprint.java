package com.bloxbean.cardano.client.plutus.annotation.blueprint.model;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/ComplexTypeBlueprint_aiken_v1_0_21_alpha_4b04517.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.model.complextypes")
@AikenStdlib(AikenStdlibVersion.V1)
public interface ComplexTypesBlueprint {
}
