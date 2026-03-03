package blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/mixed-titles-with-generics_aiken_v1_0_26.json", packageName = "com.test.mixedtitles")
@AikenStdlib(AikenStdlibVersion.V1)
public interface MixedTitlesWithGenerics {
}
