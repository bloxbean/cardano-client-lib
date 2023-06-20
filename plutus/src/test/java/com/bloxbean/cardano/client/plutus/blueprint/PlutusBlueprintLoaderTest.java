package com.bloxbean.cardano.client.plutus.blueprint;

import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusContractBlueprint;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
class PlutusBlueprintLoaderTest {

    @Test
    void loadBlueprint_fromInputStream() {
        InputStream in = this.getClass().getResourceAsStream("/blueprint/helloworld-plutus.json");
        PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(in);

        assertThat(blueprint).isNotNull();
        assertThat(blueprint.getPreamble().getTitle()).isEqualTo("aiken-lang/hello_world");
        assertThat(blueprint.getPreamble().getDescription()).isEqualTo("Aiken contracts for project 'aiken-lang/hello_world'");
        assertThat(blueprint.getPreamble().getVersion()).isEqualTo("1.0.0");
        assertThat(blueprint.getPreamble().getPlutusVersion()).isEqualTo(PlutusVersion.v2);
        assertThat(blueprint.getPreamble().getLicense()).isEqualTo("Apache 2.0");

        assertThat(blueprint.getValidators()).hasSize(2);
        assertThat(blueprint.getValidators().get(0).getTitle()).isEqualTo("hello_world");
        assertThat(blueprint.getValidators().get(0).getCompiledCode()).isEqualTo("58ad0100003232322225333004323253330063372e646e64004dd7198009801002240009210d48656c6c6f2c20576f726c64210013233300100137586600460066600460060089000240206eb8cc008c00c019200022253335573e004294054ccc024cdc79bae300a00200114a226660060066016004002294088c8ccc0040052000003222333300a3370e008004016466600800866e0000d2002300d001001235573c6ea8004526165734ae855d11");
        assertThat(blueprint.getValidators().get(0).getHash()).isEqualTo("5e1e8fa84f2b557ddc362329413caa3fd89a1be26bfd24be05ce0a02");

        assertThat(blueprint.getValidators().get(1).getTitle()).isEqualTo("validator-2");
        assertThat(blueprint.getValidators().get(1).getCompiledCode()).isEqualTo("581801000032223253330043370e00290020a4c2c6eb40095cd1");
        assertThat(blueprint.getValidators().get(1).getHash()).isEqualTo("6e1e8fa84f2b557ddc362329413caa3fd89a1be26bfd24be05ce0a02");
    }

    @Test
    void loadBlueprint_fromFile() {
        File file = new File(this.getClass().getResource("/blueprint/helloworld-plutus.json").getFile());
        PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(file);

        assertThat(blueprint).isNotNull();
        assertThat(blueprint.getPreamble().getTitle()).isEqualTo("aiken-lang/hello_world");
        assertThat(blueprint.getPreamble().getDescription()).isEqualTo("Aiken contracts for project 'aiken-lang/hello_world'");
        assertThat(blueprint.getPreamble().getVersion()).isEqualTo("1.0.0");
        assertThat(blueprint.getPreamble().getPlutusVersion()).isEqualTo(PlutusVersion.v2);
        assertThat(blueprint.getPreamble().getLicense()).isEqualTo("Apache 2.0");

        assertThat(blueprint.getValidators()).hasSize(2);
        assertThat(blueprint.getValidators().get(0).getTitle()).isEqualTo("hello_world");
        assertThat(blueprint.getValidators().get(0).getCompiledCode()).isEqualTo("58ad0100003232322225333004323253330063372e646e64004dd7198009801002240009210d48656c6c6f2c20576f726c64210013233300100137586600460066600460060089000240206eb8cc008c00c019200022253335573e004294054ccc024cdc79bae300a00200114a226660060066016004002294088c8ccc0040052000003222333300a3370e008004016466600800866e0000d2002300d001001235573c6ea8004526165734ae855d11");
        assertThat(blueprint.getValidators().get(0).getHash()).isEqualTo("5e1e8fa84f2b557ddc362329413caa3fd89a1be26bfd24be05ce0a02");

        assertThat(blueprint.getValidators().get(1).getTitle()).isEqualTo("validator-2");
        assertThat(blueprint.getValidators().get(1).getCompiledCode()).isEqualTo("581801000032223253330043370e00290020a4c2c6eb40095cd1");
        assertThat(blueprint.getValidators().get(1).getHash()).isEqualTo("6e1e8fa84f2b557ddc362329413caa3fd89a1be26bfd24be05ce0a02");
    }

    @Test
    void getPlutusScript() {
        InputStream in = this.getClass().getResourceAsStream("/blueprint/helloworld-plutus.json");
        PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(in);

        PlutusScript plutusScript1 = blueprint.getPlutusScript("hello_world");
        PlutusScript plutusScript2 = blueprint.getPlutusScript("validator-2");

        assertThat(plutusScript1).isNotNull();
        assertThat(plutusScript2).isNotNull();
        assertThat(plutusScript1.getCborHex()).contains("58ad0100003232322225333004323253330063372e646e64004dd7198009801002240009210d48656c6c6f2c20576f726c64210013233300100137586600460066600460060089000240206eb8cc008c00c019200022253335573e004294054ccc024cdc79bae300a00200114a226660060066016004002294088c8ccc0040052000003222333300a3370e008004016466600800866e0000d2002300d001001235573c6ea8004526165734ae855d11");
        assertThat(plutusScript2.getCborHex()).contains("581801000032223253330043370e00290020a4c2c6eb40095cd1");
        assertThat(plutusScript2.getCborHex().length()).isGreaterThan("581801000032223253330043370e00290020a4c2c6eb40095cd1".length());
    }
}
