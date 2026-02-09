package blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

@Blueprint(fileInResources = "blueprint/missing-title-test_aiken_v1_0_26.json", packageName = "com.test.missingtitle")
public interface MissingTitleBlueprint {
}
