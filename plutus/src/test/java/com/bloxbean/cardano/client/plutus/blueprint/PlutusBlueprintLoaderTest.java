package com.bloxbean.cardano.client.plutus.blueprint;

import com.bloxbean.cardano.client.plutus.blueprint.model.*;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for PlutusBlueprintLoader.
 *
 * Tests are organized into nested classes by functionality:
 * - BasicLoading: Tests for loading blueprints from different sources
 * - ScriptExtraction: Tests for extracting Plutus scripts from blueprints
 * - CircularReferenceHandling: Tests for handling circular type references
 */
@DisplayName("PlutusBlueprintLoader")
class PlutusBlueprintLoaderTest {

    @Nested
    @DisplayName("Basic Scripts Loading")
    class BasicLoading {

        @Test
        @DisplayName("should load blueprint from InputStream")
        void loadBlueprint_fromInputStream() {
            InputStream in = PlutusBlueprintLoaderTest.class.getResourceAsStream("/blueprint/helloworld-plutus.json");
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
        @DisplayName("should load blueprint from File")
        void loadBlueprint_fromFile() {
            File file = new File(PlutusBlueprintLoaderTest.class.getResource("/blueprint/helloworld-plutus.json").getFile());
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
    }

    @Nested
    @DisplayName("Script Extraction")
    class ScriptExtraction {

        @Test
        @DisplayName("should extract PlutusScript from blueprint")
        void getPlutusScript() {
            InputStream in = PlutusBlueprintLoaderTest.class.getResourceAsStream("/blueprint/helloworld-plutus.json");
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

    @Nested
    @DisplayName("Real World Blueprints")
    class RealWorldBlueprints {

        @Test
        @DisplayName("should load giftcard blueprint with correct structure")
        void loadGiftCardBlueprint() {
            InputStream in = PlutusBlueprintLoaderTest.class.getResourceAsStream("/blueprint/giftcard.json");
            assertNotNull(in, "Gift card blueprint file should exist");

            PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(in);

            // Verify preamble
            assertThat(blueprint).isNotNull();
            assertThat(blueprint.getPreamble().getTitle()).isEqualTo("aiken-lang/gift_card");
            assertThat(blueprint.getPreamble().getDescription()).isEqualTo("Create a gift card that can be used to redeem locked assets");
            assertThat(blueprint.getPreamble().getVersion()).isEqualTo("0.0.0");
            assertThat(blueprint.getPreamble().getPlutusVersion()).isEqualTo(PlutusVersion.v3);
            assertThat(blueprint.getPreamble().getCompiler()).isNotNull();
            assertThat(blueprint.getPreamble().getCompiler().getName()).isEqualTo("Aiken");
            assertThat(blueprint.getPreamble().getCompiler().getVersion()).isEqualTo("v1.1.21+42babe5");
            assertThat(blueprint.getPreamble().getLicense()).isEqualTo("Apache-2.0");

            // Verify validators
            assertThat(blueprint.getValidators()).hasSize(6);
            assertThat(blueprint.getValidators().get(0).getTitle()).isEqualTo("multi.redeem.spend");
            assertThat(blueprint.getValidators().get(1).getTitle()).isEqualTo("multi.redeem.mint");
            assertThat(blueprint.getValidators().get(2).getTitle()).isEqualTo("multi.redeem.else");
            assertThat(blueprint.getValidators().get(3).getTitle()).isEqualTo("oneshot.gift_card.spend");
            assertThat(blueprint.getValidators().get(4).getTitle()).isEqualTo("oneshot.gift_card.mint");
            assertThat(blueprint.getValidators().get(5).getTitle()).isEqualTo("oneshot.gift_card.else");

            // Verify first validator structure (multi.redeem.spend)
            Validator validator = blueprint.getValidators().get(0);
            assertThat(validator.getDatum()).isNotNull();
            assertThat(validator.getDatum().getTitle()).isEqualTo("datum");
            assertThat(validator.getRedeemer()).isNotNull();
            assertThat(validator.getRedeemer().getTitle()).isEqualTo("_r");
            assertThat(validator.getParameters()).hasSize(1);
            assertThat(validator.getParameters().get(0).getTitle()).isEqualTo("creator");
            assertThat(validator.getCompiledCode()).isNotNull().isNotEmpty();
            assertThat(validator.getHash()).isEqualTo("2f904329815ffc78edc99e90ca907d86fdd0c8fa886b50bdd42f36fa");

            // Verify definitions exist
            assertThat(blueprint.getDefinitions()).isNotEmpty();
            assertThat(blueprint.getDefinitions()).hasSize(7);
            assertThat(blueprint.getDefinitions()).containsKey("ByteArray");
            assertThat(blueprint.getDefinitions()).containsKey("Data");
        }

        @Test
        @DisplayName("should load aftermarket blueprint with correct structure")
        void loadAftermarketBlueprint() {
            InputStream in = PlutusBlueprintLoaderTest.class.getResourceAsStream("/blueprint/aftermarket_aiken_v1_0_26_alpha_075668b.json");
            assertNotNull(in, "Aftermarket blueprint file should exist");

            PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(in);

            // Verify preamble
            assertThat(blueprint).isNotNull();
            assertThat(blueprint.getPreamble().getTitle()).isEqualTo("fallen-icarus/cardano-aftermarket");
            assertThat(blueprint.getPreamble().getDescription()).isEqualTo("Aiken contracts for project 'fallen-icarus/cardano-aftermarket'");
            assertThat(blueprint.getPreamble().getVersion()).isEqualTo("0.0.0");
            assertThat(blueprint.getPreamble().getPlutusVersion()).isEqualTo(PlutusVersion.v2);
            assertThat(blueprint.getPreamble().getCompiler()).isNotNull();
            assertThat(blueprint.getPreamble().getCompiler().getName()).isEqualTo("Aiken");
            assertThat(blueprint.getPreamble().getCompiler().getVersion()).isEqualTo("v1.0.26-alpha+075668b");
            assertThat(blueprint.getPreamble().getLicense()).isEqualTo("Apache-2.0");

            // Verify validators
            assertThat(blueprint.getValidators()).hasSize(3);
            assertThat(blueprint.getValidators().get(0).getTitle()).isEqualTo("cardano_aftermarket.aftermarket_observer_script");
            assertThat(blueprint.getValidators().get(1).getTitle()).isEqualTo("cardano_aftermarket.aftermarket_script");
            assertThat(blueprint.getValidators().get(2).getTitle()).isEqualTo("cardano_aftermarket.beacon_script");

            // Verify observer script structure
            Validator observerScript = blueprint.getValidators().get(0);
            assertThat(observerScript.getRedeemer()).isNotNull();
            assertThat(observerScript.getParameters()).hasSize(2);
            assertThat(observerScript.getParameters().get(0).getTitle()).isEqualTo("proxy_hash");
            assertThat(observerScript.getParameters().get(1).getTitle()).isEqualTo("aftermarket_script_hash");
            assertThat(observerScript.getCompiledCode()).isNotNull().isNotEmpty();
            assertThat(observerScript.getHash()).isEqualTo("5cb4eee94619efbf5050c3a21075bbf010f4754b5428b66338bc6bee");

            // Verify aftermarket script structure
            Validator aftermarketScript = blueprint.getValidators().get(1);
            assertThat(aftermarketScript.getDatum()).isNotNull();
            assertThat(aftermarketScript.getDatum().getTitle()).isEqualTo("datum");
            assertThat(aftermarketScript.getRedeemer()).isNotNull();
            assertThat(aftermarketScript.getRedeemer().getTitle()).isEqualTo("redeemer");
            assertThat(aftermarketScript.getCompiledCode()).isNotNull().isNotEmpty();
            assertThat(aftermarketScript.getHash()).isEqualTo("e07ee8979776692ce3477b0c0d53b4c650ef6ccad75c2596da22847c");

            // Verify beacon script structure
            Validator beaconScript = blueprint.getValidators().get(2);
            assertThat(beaconScript.getParameters()).hasSize(3);
            assertThat(beaconScript.getParameters().get(0).getTitle()).isEqualTo("proxy_hash");
            assertThat(beaconScript.getParameters().get(1).getTitle()).isEqualTo("aftermarket_script_hash");
            assertThat(beaconScript.getParameters().get(2).getTitle()).isEqualTo("aftermarket_observer_hash");
            assertThat(beaconScript.getHash()).isEqualTo("d38db9eb9a008ccb5bede06df22b75342b35beedc87d1098bf608410");

            // Verify definitions exist
            assertThat(blueprint.getDefinitions()).isNotEmpty();
            assertThat(blueprint.getDefinitions()).containsKey("ByteArray");
            assertThat(blueprint.getDefinitions()).containsKey("Int");
        }

        @Test
        @DisplayName("should load CIP-113 token blueprint with correct structure")
        void loadCip113TokenBlueprint() {
            InputStream in = PlutusBlueprintLoaderTest.class.getResourceAsStream("/blueprint/cip113Token_aiken_v1_1_17.json");
            assertNotNull(in, "CIP-113 token blueprint file should exist");

            PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(in);

            // Verify preamble
            assertThat(blueprint).isNotNull();
            assertThat(blueprint.getPreamble().getTitle()).isEqualTo("ft/bafin");
            assertThat(blueprint.getPreamble().getDescription()).isEqualTo("Aiken contracts for project 'ft/bafin'");
            assertThat(blueprint.getPreamble().getVersion()).isEqualTo("0.0.0");
            assertThat(blueprint.getPreamble().getPlutusVersion()).isEqualTo(PlutusVersion.v3);
            assertThat(blueprint.getPreamble().getCompiler()).isNotNull();
            assertThat(blueprint.getPreamble().getCompiler().getName()).isEqualTo("Aiken");
            assertThat(blueprint.getPreamble().getCompiler().getVersion()).isEqualTo("v1.1.17+c3a7fba");
            assertThat(blueprint.getPreamble().getLicense()).isEqualTo("Apache-2.0");

            // Verify validators count
            assertThat(blueprint.getValidators()).hasSize(22);

            // Verify config mint validator structure
            Validator configMintValidator = blueprint.getValidators().get(0);
            assertThat(configMintValidator.getTitle()).isEqualTo("config.config_mint_validator.mint");
            assertThat(configMintValidator.getRedeemer()).isNotNull();
            assertThat(configMintValidator.getParameters()).hasSize(3);
            assertThat(configMintValidator.getParameters().get(0).getTitle()).isEqualTo("tx0");
            assertThat(configMintValidator.getParameters().get(1).getTitle()).isEqualTo("index0");
            assertThat(configMintValidator.getParameters().get(2).getTitle()).isEqualTo("config_spend_script_hash");
            assertThat(configMintValidator.getCompiledCode()).isNotNull().isNotEmpty();
            assertThat(configMintValidator.getHash()).isEqualTo("216f950d9048ae74e53e7d8f101b210624f28055387d672b1c4678cc");

            // Verify config spend validator structure
            Validator configSpendValidator = blueprint.getValidators().get(2);
            assertThat(configSpendValidator.getTitle()).isEqualTo("config.config_spend_validator.spend");
            assertThat(configSpendValidator.getDatum()).isNotNull();
            assertThat(configSpendValidator.getDatum().getTitle()).isEqualTo("datum_opt");
            assertThat(configSpendValidator.getRedeemer()).isNotNull();
            assertThat(configSpendValidator.getParameters()).hasSize(1);
            assertThat(configSpendValidator.getParameters().get(0).getTitle()).isEqualTo("owner_credential_hash");
            assertThat(configSpendValidator.getHash()).isEqualTo("d77252a19755d8e4309e35a003f4de10e32a30efa70fbd08cda7ce8a");

            // Verify definitions exist
            assertThat(blueprint.getDefinitions()).isNotEmpty();
            assertThat(blueprint.getDefinitions()).containsKey("ByteArray");
            assertThat(blueprint.getDefinitions()).containsKey("Int");
        }
    }

    @Nested
    @DisplayName("Circular Reference Handling")
    class CircularReferenceHandling {

        @Test
        @DisplayName("should handle self-referencing types without StackOverflowError")
        void testSelfReferencingType() {
            InputStream in = PlutusBlueprintLoaderTest.class.getResourceAsStream("/blueprint/circular-self-reference.json");
            assertNotNull(in, "Test blueprint file should exist");

            assertDoesNotThrow(() -> {
                PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(in);
                assertNotNull(blueprint);

                Validator validator = blueprint.getValidators().get(0);
                BlueprintSchema datumSchema = validator.getDatum().getSchema();

                assertNotNull(datumSchema);
                assertEquals("LinkedListNode", datumSchema.getTitle());
                assertNotNull(datumSchema.getFields());
                assertEquals(2, datumSchema.getFields().size());

                // Verify the circular structure exists
                BlueprintSchema nextField = datumSchema.getFields().get(1);
                assertEquals("next", nextField.getTitle());
            }, "Should not throw StackOverflowError on self-referencing types");
        }

        @Test
        @DisplayName("should handle mutually recursive types without StackOverflowError")
        void testMutuallyRecursiveTypes() {
            InputStream in = PlutusBlueprintLoaderTest.class.getResourceAsStream("/blueprint/circular-mutual-reference.json");
            assertNotNull(in, "Test blueprint file should exist");

            assertDoesNotThrow(() -> {
                PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(in);
                assertNotNull(blueprint);

                Map<String, BlueprintSchema> definitions = blueprint.getDefinitions();
                assertTrue(definitions.containsKey("TypeA"));
                assertTrue(definitions.containsKey("TypeB"));

                // Verify TypeA has a field referencing TypeB
                BlueprintSchema typeA = definitions.get("TypeA");
                assertEquals("TypeA", typeA.getTitle());
                assertNotNull(typeA.getFields());
                assertEquals(1, typeA.getFields().size());

                // Verify TypeB has a field referencing TypeA
                BlueprintSchema typeB = definitions.get("TypeB");
                assertEquals("TypeB", typeB.getTitle());
                assertNotNull(typeB.getFields());
                assertEquals(1, typeB.getFields().size());
            }, "Should not throw StackOverflowError on mutually recursive types");
        }

        @Test
        @DisplayName("should handle complex circular references in anyOf")
        void testCircularReferencesInAnyOf() {
            InputStream in = PlutusBlueprintLoaderTest.class.getResourceAsStream("/blueprint/circular-anyof-reference.json");
            assertNotNull(in, "Test blueprint file should exist");

            assertDoesNotThrow(() -> {
                PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(in);
                assertNotNull(blueprint);

                Validator validator = blueprint.getValidators().get(0);
                BlueprintSchema datumSchema = validator.getDatum().getSchema();

                assertNotNull(datumSchema);
                assertEquals("Action", datumSchema.getTitle());
                assertNotNull(datumSchema.getAnyOf());
                assertEquals(2, datumSchema.getAnyOf().size());

                // Verify anyOf variants reference State
                BlueprintSchema createVariant = datumSchema.getAnyOf().get(0);
                assertEquals("Create", createVariant.getTitle());
                assertNotNull(createVariant.getFields());

                BlueprintSchema updateVariant = datumSchema.getAnyOf().get(1);
                assertEquals("Update", updateVariant.getTitle());
                assertNotNull(updateVariant.getFields());

                // Verify State type exists and references Action
                Map<String, BlueprintSchema> definitions = blueprint.getDefinitions();
                assertTrue(definitions.containsKey("State"));
                BlueprintSchema state = definitions.get("State");
                assertEquals("State", state.getTitle());
            }, "Should handle circular references in anyOf structures");
        }

        @Test
        @DisplayName("should handle nested list circular reference pattern")
        void testNestedListCircularReference() {
            // Pattern: Script -> Composite -> List<Script>
            // Common in multisig schemes, state machines, and tree structures
            InputStream in = PlutusBlueprintLoaderTest.class.getResourceAsStream("/blueprint/circular-nested-list.json");
            assertNotNull(in, "Nested list circular reference test blueprint should exist");

            assertDoesNotThrow(() -> {
                PlutusContractBlueprint blueprint = PlutusBlueprintLoader.loadBlueprint(in);
                assertNotNull(blueprint);

                // Verify the circular Script type loaded successfully
                Map<String, BlueprintSchema> definitions = blueprint.getDefinitions();
                assertTrue(definitions.containsKey("multisig/Script"));

                BlueprintSchema script = definitions.get("multisig/Script");
                assertEquals("Script", script.getTitle());
                assertNotNull(script.getAnyOf());
                assertEquals(4, script.getAnyOf().size()); // Single, AllOf, AnyOf, AtLeast

                // Verify AllOf constructor contains self-reference via List
                BlueprintSchema allOfConstructor = script.getAnyOf().get(1);
                assertEquals("AllOf", allOfConstructor.getTitle());
                assertNotNull(allOfConstructor.getFields());
                assertEquals(1, allOfConstructor.getFields().size());

                BlueprintSchema scriptsField = allOfConstructor.getFields().get(0);
                assertEquals("scripts", scriptsField.getTitle());
                assertNotNull(scriptsField.getRef());
                assertTrue(scriptsField.getRef().contains("List<multisig~1Script>"));
            }, "Should handle nested list circular reference without StackOverflowError");
        }
    }

}
