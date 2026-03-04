package blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/primitive-no-title_aiken_v1_0_26.json", packageName = "com.test.primitive")
@AikenStdlib(AikenStdlibVersion.V1)
public interface PrimitiveNoTitleBlueprint {
}
