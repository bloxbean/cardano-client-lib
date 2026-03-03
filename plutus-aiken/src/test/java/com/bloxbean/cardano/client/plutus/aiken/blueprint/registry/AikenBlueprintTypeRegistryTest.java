package com.bloxbean.cardano.client.plutus.aiken.blueprint.registry;

import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;
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

    private static LookupContext ctxV1() {
        return LookupContext.EMPTY.withHint(AikenBlueprintTypeRegistry.HINT_STDLIB_VERSION, AikenStdlibVersion.V1.name());
    }

    private static LookupContext ctxV2() {
        return LookupContext.EMPTY.withHint(AikenBlueprintTypeRegistry.HINT_STDLIB_VERSION, AikenStdlibVersion.V2.name());
    }

    private static LookupContext ctxV3() {
        return LookupContext.EMPTY.withHint(AikenBlueprintTypeRegistry.HINT_STDLIB_VERSION, AikenStdlibVersion.V3.name());
    }

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

    @Test
    void commonTypesAvailableRegardlessOfVersion() {
        // Tuple should be found with any version hint
        BlueprintSchema tupleSchema = new BlueprintSchema();
        tupleSchema.setTitle("Tuple");
        tupleSchema.setDataType(BlueprintDatatype.list);
        BlueprintSchema leftRef = new BlueprintSchema();
        leftRef.setRef("#/definitions/ByteArray");
        BlueprintSchema rightRef = new BlueprintSchema();
        rightRef.setRef("#/definitions/ByteArray");
        tupleSchema.setItems(java.util.List.of(leftRef, rightRef));

        SchemaSignature signature = new SchemaSignatureBuilder().build(tupleSchema);

        assertThat(registry.lookup(signature, tupleSchema, ctxV1())).isPresent();
        assertThat(registry.lookup(signature, tupleSchema, ctxV2())).isPresent();
        assertThat(registry.lookup(signature, tupleSchema, ctxV3())).isPresent();
    }

    // ── Bytes wrappers (version-independent) ────────────────────────────────

    @Test
    void lookupBytesWrappers() {
        assertLookup(bytesSchema("VerificationKey"), ctxV3(), STD + ".VerificationKey");
        assertLookup(bytesSchema("VerificationKeyHash"), ctxV3(), STD + ".VerificationKeyHash");
        assertLookup(bytesSchema("Script"), ctxV3(), STD + ".Script");
        assertLookup(bytesSchema("ScriptHash"), ctxV3(), STD + ".ScriptHash");
        assertLookup(bytesSchema("Signature"), ctxV3(), STD + ".Signature");
        assertLookup(bytesSchema("DataHash"), ctxV3(), STD + ".DataHash");
        assertLookup(bytesSchema("Hash"), ctxV3(), STD + ".Hash");
        assertLookup(bytesSchema("PolicyId"), ctxV3(), STD + ".PolicyId");
        assertLookup(bytesSchema("AssetName"), ctxV3(), STD + ".AssetName");
    }

    @Test
    void lookupIntervalBoundType() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.intervalBoundTypeSchema();
        assertLookup(schema, ctxV3(), STD + ".IntervalBoundType");
    }

    // ── Aiken stdlib v1 ─────────────────────────────────────────────────────

    @Test
    void lookupCredential_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV1Schema();
        assertLookup(schema, ctxV1(), STD + ".Credential");
    }

    @Test
    void lookupReferencedCredential_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.referencedV1Schema();
        assertLookup(schema, ctxV1(), STD + ".ReferencedCredential");
    }

    @Test
    void lookupAddress_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.addressV1Schema();
        assertLookup(schema, ctxV1(), STD + ".Address");
    }

    @Test
    void lookupOutputReference_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.outputReferenceV1Schema();
        assertLookup(schema, ctxV1(), STD + ".OutputReferenceV1");
    }

    @Test
    void lookupIntervalBound_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.intervalBoundV1Schema();
        assertLookup(schema, ctxV1(), STD + ".IntervalBound");
    }

    @Test
    void lookupValidityRange_stdlibV1() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.validityRangeV1Schema();
        assertLookup(schema, ctxV1(), STD + ".ValidityRange");
    }

    // ── Aiken stdlib v2 ─────────────────────────────────────────────────────

    @Test
    void lookupCredential_stdlibV2() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV2Schema("Credential");
        assertLookup(schema, ctxV2(), STD + ".PaymentCredential");
    }

    @Test
    void lookupPaymentCredential_stdlibV2() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV2Schema("PaymentCredential");
        assertLookup(schema, ctxV2(), STD + ".PaymentCredential");
    }

    @Test
    void lookupStakeCredential_stdlibV2() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.stakeCredentialV2Schema();
        assertLookup(schema, ctxV2(), STD + ".StakeCredential");
    }

    @Test
    void lookupAddress_stdlibV2() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.addressV2Schema();
        assertLookup(schema, ctxV2(), STD + ".Address");
    }

    @Test
    void lookupOutputReference_stdlibV2() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.outputReferenceV2Schema();
        assertLookup(schema, ctxV2(), STD + ".OutputReference");
    }

    // ── Aiken stdlib v3 ─────────────────────────────────────────────────────

    @Test
    void lookupCredential_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV3Schema("Credential");
        assertLookup(schema, ctxV3(), STD + ".PaymentCredential");
    }

    @Test
    void lookupPaymentCredential_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV3Schema("PaymentCredential");
        assertLookup(schema, ctxV3(), STD + ".PaymentCredential");
    }

    @Test
    void lookupAddress_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.addressV3Schema();
        assertLookup(schema, ctxV3(), STD + ".Address");
    }

    @Test
    void lookupIntervalBound_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.intervalBoundV3Schema();
        assertLookup(schema, ctxV3(), STD + ".IntervalBound");
    }

    @Test
    void lookupValidityRange_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.validityRangeV3Schema();
        assertLookup(schema, ctxV3(), STD + ".ValidityRange");
    }

    @Test
    void lookupStakeCredential_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.stakeCredentialV2Schema();
        assertLookup(schema, ctxV3(), STD + ".StakeCredential");
    }

    @Test
    void lookupOutputReference_stdlibV3() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.outputReferenceV2Schema();
        assertLookup(schema, ctxV3(), STD + ".OutputReference");
    }

    // ── Version isolation ───────────────────────────────────────────────────

    @Test
    void v1TypeNotReturnedForV2Context() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV1Schema();
        SchemaSignature signature = new SchemaSignatureBuilder().build(schema);
        Optional<RegisteredType> result = registry.lookup(signature, schema, ctxV2());
        assertThat(result).isEmpty();
    }

    @Test
    void v1TypeNotReturnedForV3Context() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV1Schema();
        SchemaSignature signature = new SchemaSignatureBuilder().build(schema);
        Optional<RegisteredType> result = registry.lookup(signature, schema, ctxV3());
        assertThat(result).isEmpty();
    }

    @Test
    void v2TypeNotReturnedForV1Context() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.addressV2Schema();
        SchemaSignature signature = new SchemaSignatureBuilder().build(schema);
        Optional<RegisteredType> result = registry.lookup(signature, schema, ctxV1());
        assertThat(result).isEmpty();
    }

    @Test
    void v3TypeNotReturnedForV1Context() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV3Schema("Credential");
        SchemaSignature signature = new SchemaSignatureBuilder().build(schema);
        Optional<RegisteredType> result = registry.lookup(signature, schema, ctxV1());
        assertThat(result).isEmpty();
    }

    @Test
    void v3TypeNotReturnedForV2Context() {
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV3Schema("Credential");
        SchemaSignature signature = new SchemaSignatureBuilder().build(schema);
        Optional<RegisteredType> result = registry.lookup(signature, schema, ctxV2());
        assertThat(result).isEmpty();
    }

    @Test
    void defaultContextUsesV3() {
        // LookupContext.EMPTY has no hint → defaults to V3
        BlueprintSchema schema = AikenBlueprintTypeRegistry.credentialV3Schema("Credential");
        assertLookup(schema, LookupContext.EMPTY, STD + ".PaymentCredential");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void assertLookup(BlueprintSchema schema, LookupContext context, String expectedCanonicalName) {
        SchemaSignature signature = new SchemaSignatureBuilder().build(schema);
        Optional<RegisteredType> result = registry.lookup(signature, schema, context);
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
