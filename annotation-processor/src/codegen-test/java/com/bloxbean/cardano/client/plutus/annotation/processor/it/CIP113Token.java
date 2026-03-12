package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/cip113Token_aiken_v1_1_17_c3a7fba.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113")
@AikenStdlib(AikenStdlibVersion.V3)
public interface CIP113Token {
}
