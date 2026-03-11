package com.bloxbean.cardano.client.plutus.annotation.processor.it;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/basic_pair_aiken_v1_1_3_3d77b5c.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.basicpair")
@AikenStdlib(AikenStdlibVersion.V3)
public class BasicPair {
}
