package blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/generic-cardano-builtins_aiken_v1_1_21_42babe5.json", packageName = "com.bloxbean.cardano.client.plutus.annotation.blueprint.genericcardanobuiltins")
@AikenStdlib(AikenStdlibVersion.V3)
public class GenericCardanoBuiltins {
}
