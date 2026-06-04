package com.bloxbean.cardano.client.plutus.aiken.blueprint.registry;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.registry.LookupContext;
import com.bloxbean.cardano.client.plutus.blueprint.registry.RegisteredType;
import com.bloxbean.cardano.client.plutus.blueprint.registry.SchemaSignature;
import com.bloxbean.cardano.client.plutus.blueprint.registry.SchemaSignatureBuilder;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AikenBlueprintTypeRegistryTest {

    private final AikenBlueprintTypeRegistry registry = new AikenBlueprintTypeRegistry();

    private static final String STD = "com.bloxbean.cardano.client.plutus.aiken.blueprint.std";

    // ── Bytes wrappers ──────────────────────────────────────────────────────

    @Test
    void lookupBytesWrappers() {
        assertLookup(bytesSchema("VerificationKey"), STD + ".VerificationKey");
        assertLookup(bytesSchema("VerificationKeyHash"), STD + ".VerificationKeyHash");
        assertLookup(bytesSchema("Script"), STD + ".Script");
        assertLookup(bytesSchema("ScriptHash"), STD + ".ScriptHash");
        assertLookup(bytesSchema("Signature"), STD + ".Signature");
        assertLookup(bytesSchema("DataHash"), STD + ".DataHash");
        assertLookup(bytesSchema("Hash"), STD + ".Hash");
        assertLookup(bytesSchema("PolicyId"), STD + ".PolicyId");
        assertLookup(bytesSchema("AssetName"), STD + ".AssetName");
    }

    @Test
    void lookupIntervalBoundType() {
        assertLookup(AikenBlueprintTypeRegistry.intervalBoundTypeSchema(), STD + ".IntervalBoundType");
    }

    // ── Aiken stdlib v3.x types ─────────────────────────────────────────────

    @Test
    void lookupCredential() {
        assertLookup(AikenBlueprintTypeRegistry.credentialSchema("Credential"), STD + ".PaymentCredential");
    }

    @Test
    void lookupPaymentCredential() {
        assertLookup(AikenBlueprintTypeRegistry.credentialSchema("PaymentCredential"), STD + ".PaymentCredential");
    }

    @Test
    void lookupAddress() {
        assertLookup(AikenBlueprintTypeRegistry.addressSchema(), STD + ".Address");
    }

    @Test
    void lookupStakeCredential() {
        assertLookup(AikenBlueprintTypeRegistry.stakeCredentialSchema(), STD + ".StakeCredential");
    }

    @Test
    void lookupOutputReference() {
        assertLookup(AikenBlueprintTypeRegistry.outputReferenceSchema(), STD + ".OutputReference");
    }

    @Test
    void lookupIntervalBound() {
        assertLookup(AikenBlueprintTypeRegistry.intervalBoundSchema(), STD + ".IntervalBound");
    }

    @Test
    void lookupValidityRange() {
        assertLookup(AikenBlueprintTypeRegistry.validityRangeSchema(), STD + ".ValidityRange");
    }

    @Test
    void unknownSchemaReturnsEmpty() {
        BlueprintSchema schema = bytesSchema("CompletelyMadeUpType");
        SchemaSignature signature = new SchemaSignatureBuilder().build(schema);
        Optional<RegisteredType> result = registry.lookup(signature, schema, LookupContext.EMPTY);
        assertThat(result).isEmpty();
    }

    @Test
    void noAnnotationHintsAdvertised() {
        assertThat(registry.annotationHints()).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void assertLookup(BlueprintSchema schema, String expectedCanonicalName) {
        SchemaSignature signature = new SchemaSignatureBuilder().build(schema);
        Optional<RegisteredType> result = registry.lookup(signature, schema, LookupContext.EMPTY);
        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo(expectedCanonicalName);
    }

    private BlueprintSchema bytesSchema(String title) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(title);
        schema.setDataType(BlueprintDatatype.bytes);
        return schema;
    }
}
