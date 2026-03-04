package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/AnyPlutusDataBlueprint_aiken_v1_0_21_alpha_4b04517.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.anyplutusdata")
@AikenStdlib(AikenStdlibVersion.V1)
public interface AnyPlutusDataBlueprint {
}
