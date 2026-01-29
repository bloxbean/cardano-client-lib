package com.bloxbean.cardano.client.plutus.blueprint;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusContractBlueprint;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.blueprint.model.Validator;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
        assertThat(blueprint.getValidators().get(0).getDatum().getTitle()).isEqualTo("Datum");
        assertThat(blueprint.getValidators().get(0).getDatum().getPurpose()).isEqualTo("spend");
        assertThat(blueprint.getValidators().get(0).getDatum().getSchema().getAnyOf().get(0).getTitle()).isEqualTo("Datum");
        assertThat(blueprint.getValidators().get(0).getDatum().getSchema().getAnyOf().get(0).getDataType()).isEqualTo(BlueprintDatatype.constructor);

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

    @Nested
    @DisplayName("Circular Reference Handling")
    class CircularReferenceHandling {

        @Test
        @DisplayName("should handle self-referencing types without StackOverflowError")
        void testSelfReferencingType() {
            // A linked list node that references itself
            String json = "{" +
                    "\"preamble\": {" +
                    "\"title\": \"test\"," +
                    "\"version\": \"1.0.0\"," +
                    "\"plutusVersion\": \"v2\"" +
                    "}," +
                    "\"validators\": [{" +
                    "\"title\": \"test.validator\"," +
                    "\"datum\": {" +
                    "\"schema\": {" +
                    "\"$ref\": \"#/definitions/LinkedListNode\"" +
                    "}" +
                    "}," +
                    "\"compiledCode\": \"abc123\"," +
                    "\"hash\": \"def456\"" +
                    "}]," +
                    "\"definitions\": {" +
                    "\"LinkedListNode\": {" +
                    "\"title\": \"LinkedListNode\"," +
                    "\"dataType\": \"constructor\"," +
                    "\"index\": 0," +
                    "\"fields\": [{" +
                    "\"title\": \"value\"," +
                    "\"dataType\": \"integer\"" +
                    "},{" +
                    "\"title\": \"next\"," +
                    "\"$ref\": \"#/definitions/Option$LinkedListNode\"" +
                    "}]" +
                    "}," +
                    "\"Option$LinkedListNode\": {" +
                    "\"title\": \"Option\"," +
                    "\"anyOf\": [{" +
                    "\"title\": \"Some\"," +
                    "\"dataType\": \"constructor\"," +
                    "\"index\": 0," +
                    "\"fields\": [{" +
                    "\"$ref\": \"#/definitions/LinkedListNode\"" +
                    "}]" +
                    "},{" +
                    "\"title\": \"None\"," +
                    "\"dataType\": \"constructor\"," +
                    "\"index\": 1," +
                    "\"fields\": []" +
                    "}]" +
                    "}" +
                    "}" +
                    "}";

            InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

            assertDoesNotThrow(() -> {
                PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(is);
                assertNotNull(blueprint);

                Validator validator = blueprint.getValidators().get(0);
                BlueprintSchema datumSchema = validator.getDatum().getSchema();

                assertNotNull(datumSchema);
                assertEquals("LinkedListNode", datumSchema.getTitle());
                assertNotNull(datumSchema.getFields());
                assertEquals(2, datumSchema.getFields().size());
            }, "Should not throw StackOverflowError on self-referencing types");
        }

        @Test
        @DisplayName("should handle mutually recursive types without StackOverflowError")
        void testMutuallyRecursiveTypes() {
            // Type A references Type B, Type B references Type A
            String json = "{" +
                    "\"preamble\": {" +
                    "\"title\": \"test\"," +
                    "\"version\": \"1.0.0\"," +
                    "\"plutusVersion\": \"v2\"" +
                    "}," +
                    "\"validators\": [{" +
                    "\"title\": \"test.validator\"," +
                    "\"datum\": {" +
                    "\"schema\": {" +
                    "\"$ref\": \"#/definitions/TypeA\"" +
                    "}" +
                    "}," +
                    "\"compiledCode\": \"abc123\"," +
                    "\"hash\": \"def456\"" +
                    "}]," +
                    "\"definitions\": {" +
                    "\"TypeA\": {" +
                    "\"title\": \"TypeA\"," +
                    "\"dataType\": \"constructor\"," +
                    "\"index\": 0," +
                    "\"fields\": [{" +
                    "\"title\": \"fieldB\"," +
                    "\"$ref\": \"#/definitions/TypeB\"" +
                    "}]" +
                    "}," +
                    "\"TypeB\": {" +
                    "\"title\": \"TypeB\"," +
                    "\"dataType\": \"constructor\"," +
                    "\"index\": 0," +
                    "\"fields\": [{" +
                    "\"title\": \"fieldA\"," +
                    "\"$ref\": \"#/definitions/TypeA\"" +
                    "}]" +
                    "}" +
                    "}" +
                    "}";

            InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

            assertDoesNotThrow(() -> {
                PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(is);
                assertNotNull(blueprint);

                Map<String, BlueprintSchema> definitions = blueprint.getDefinitions();
                assertTrue(definitions.containsKey("TypeA"));
                assertTrue(definitions.containsKey("TypeB"));
            }, "Should not throw StackOverflowError on mutually recursive types");
        }

        @Test
        @DisplayName("should handle complex circular references in anyOf")
        void testCircularReferencesInAnyOf() {
            // Similar to SundaeSwap pattern with anyOf containing circular refs
            String json = "{" +
                    "\"preamble\": {" +
                    "\"title\": \"test\"," +
                    "\"version\": \"1.0.0\"," +
                    "\"plutusVersion\": \"v2\"" +
                    "}," +
                    "\"validators\": [{" +
                    "\"title\": \"test.validator\"," +
                    "\"datum\": {" +
                    "\"schema\": {" +
                    "\"$ref\": \"#/definitions/Action\"" +
                    "}" +
                    "}," +
                    "\"compiledCode\": \"abc123\"," +
                    "\"hash\": \"def456\"" +
                    "}]," +
                    "\"definitions\": {" +
                    "\"Action\": {" +
                    "\"title\": \"Action\"," +
                    "\"anyOf\": [{" +
                    "\"title\": \"Create\"," +
                    "\"dataType\": \"constructor\"," +
                    "\"index\": 0," +
                    "\"fields\": [{" +
                    "\"$ref\": \"#/definitions/State\"" +
                    "}]" +
                    "},{" +
                    "\"title\": \"Update\"," +
                    "\"dataType\": \"constructor\"," +
                    "\"index\": 1," +
                    "\"fields\": [{" +
                    "\"$ref\": \"#/definitions/State\"" +
                    "}]" +
                    "}]" +
                    "}," +
                    "\"State\": {" +
                    "\"title\": \"State\"," +
                    "\"dataType\": \"constructor\"," +
                    "\"index\": 0," +
                    "\"fields\": [{" +
                    "\"title\": \"lastAction\"," +
                    "\"$ref\": \"#/definitions/Action\"" +
                    "}]" +
                    "}" +
                    "}" +
                    "}";

            InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

            assertDoesNotThrow(() -> {
                PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(is);
                assertNotNull(blueprint);

                Validator validator = blueprint.getValidators().get(0);
                assertNotNull(validator.getDatum().getSchema());
            }, "Should handle circular references in anyOf structures");
        }
    }
}
