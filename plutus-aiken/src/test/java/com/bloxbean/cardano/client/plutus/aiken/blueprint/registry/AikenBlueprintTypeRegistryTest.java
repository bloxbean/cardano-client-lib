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

    // ── Tuple (version-independent) ─────────────────────────────────────────

    @Test
    void lookupTuplePairMapping() {
        BlueprintSchema tupleSchema = new BlueprintSchema();
        tupleSchema.setTitle("Tuple");
        tupleSchema.setDataType(BlueprintDatatype.list);

        BlueprintSchema leftRef = new BlueprintSchema();
        leftRef.setRef("#/definitions/ByteArray");
        BlueprintSchema rightRef = new BlueprintSchema();
        rightRef.setRef("#/definitions/ByteArray");
        tupleSchema.setItems(java.util.List.of(leftRef, rightRef));

        SchemaSignature signature = new SchemaSignatureBuilder().build(tupleSchema);
        Optional<RegisteredType> result = registry.lookup(signature, tupleSchema, LookupContext.EMPTY);

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("com.bloxbean.cardano.client.plutus.blueprint.type.Pair");
    }

    // ── Bytes wrappers (version-independent) ────────────────────────────────

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
        BlueprintSchema schema = AikenBlueprintTypeRegistry.intervalBoundTypeSchema();
        assertLookup(schema, STD + ".IntervalBoundType");
    }

    // ── Aiken stdlib v1 ─────────────────────────────────────────────────────

    @Test
    void lookupCredential_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV1Schema();
        assertLookup(schema, STD + ".Credential");
    }

    @Test
    void lookupReferencedCredential_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.referencedV1Schema();
        assertLookup(schema, STD + ".ReferencedCredential");
    }

    @Test
    void lookupAddress_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.addressV1Schema();
        assertLookup(schema, STD + ".Address");
    }

    @Test
    void lookupOutputReference_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.outputReferenceV1Schema();
        assertLookup(schema, STD + ".OutputReferenceV1");
    }

    @Test
    void lookupIntervalBound_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.intervalBoundV1Schema();
        assertLookup(schema, STD + ".IntervalBound");
    }

    @Test
    void lookupValidityRange_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.validityRangeV1Schema();
        assertLookup(schema, STD + ".ValidityRange");
    }

    // ── Aiken stdlib v2 ─────────────────────────────────────────────────────

    @Test
    void lookupCredential_stdlibV2() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV2Schema("Credential");
        assertLookup(schema, STD + ".PaymentCredential");
    }

    @Test
    void lookupPaymentCredential_stdlibV2() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV2Schema("PaymentCredential");
        assertLookup(schema, STD + ".PaymentCredential");
    }

    @Test
    void lookupStakeCredential_stdlibV2() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.stakeCredentialV2Schema();
        assertLookup(schema, STD + ".StakeCredential");
    }

    @Test
    void lookupAddress_stdlibV2() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.addressV2Schema();
        assertLookup(schema, STD + ".Address");
    }

    @Test
    void lookupOutputReference_stdlibV2() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.outputReferenceV2Schema();
        assertLookup(schema, STD + ".OutputReference");
    }

    // ── Aiken stdlib v3 ─────────────────────────────────────────────────────

    @Test
    void lookupCredential_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV3Schema("Credential");
        assertLookup(schema, STD + ".PaymentCredential");
    }

    @Test
    void lookupPaymentCredential_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV3Schema("PaymentCredential");
        assertLookup(schema, STD + ".PaymentCredential");
    }

    @Test
    void lookupAddress_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.addressV3Schema();
        assertLookup(schema, STD + ".Address");
    }

    @Test
    void lookupIntervalBound_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.intervalBoundV3Schema();
        assertLookup(schema, STD + ".IntervalBound");
    }

    @Test
    void lookupValidityRange_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.validityRangeV3Schema();
        assertLookup(schema, STD + ".ValidityRange");
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
