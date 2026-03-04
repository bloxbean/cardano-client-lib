package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/parameterized_validators_aiken_v1_0_29_alpha_16fb02e.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.parameterized_validator")
@AikenStdlib(AikenStdlibVersion.V1)
public class ParameterizedValidatorBlueprint {
}
