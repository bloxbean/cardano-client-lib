package blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/missing-title-test_aiken_v1_0_26.json", packageName = "com.test.missingtitle")
@AikenStdlib(AikenStdlibVersion.V1)
public interface MissingTitleBlueprint {
}
