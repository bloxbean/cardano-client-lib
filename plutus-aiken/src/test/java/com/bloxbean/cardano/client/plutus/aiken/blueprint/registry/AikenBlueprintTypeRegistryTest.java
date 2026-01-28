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
    void lookupCredentialMapping() {
        BlueprintSchema credential = AikenBlueprintTypeRegistry.credentialSchema();
        SchemaSignature signature = new SchemaSignatureBuilder().build(credential);
        Optional<RegisteredType> result = registry.lookup(signature, credential, LookupContext.EMPTY);

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Credential");
    }

    @Test
    void lookupReferencedCredentialMapping() {
        BlueprintSchema referenced = AikenBlueprintTypeRegistry.referencedCredentialSchema();
        SchemaSignature signature = new SchemaSignatureBuilder().build(referenced);
        Optional<RegisteredType> result = registry.lookup(signature, referenced, LookupContext.EMPTY);

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("com.bloxbean.cardano.client.plutus.aiken.blueprint.std.ReferencedCredential");
    }

    @Test
    void lookupAddressMapping() {
        BlueprintSchema address = AikenBlueprintTypeRegistry.addressSchema();
        SchemaSignature signature = new SchemaSignatureBuilder().build(address);
        Optional<RegisteredType> result = registry.lookup(signature, address, LookupContext.EMPTY);

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address");
    }

    @Test
    void lookupHashWrappers() {
        assertThat(registry.lookup(new SchemaSignatureBuilder().build(bytesSchema("VerificationKey")),
                bytesSchema("VerificationKey"), LookupContext.EMPTY))
                .contains(new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "VerificationKey"));

        assertThat(registry.lookup(new SchemaSignatureBuilder().build(bytesSchema("VerificationKeyHash")),
                bytesSchema("VerificationKeyHash"), LookupContext.EMPTY))
                .contains(new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "VerificationKeyHash"));

        assertThat(registry.lookup(new SchemaSignatureBuilder().build(bytesSchema("Script")),
                bytesSchema("Script"), LookupContext.EMPTY))
                .contains(new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "Script"));

        assertThat(registry.lookup(new SchemaSignatureBuilder().build(bytesSchema("ScriptHash")),
                bytesSchema("ScriptHash"), LookupContext.EMPTY))
                .contains(new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "ScriptHash"));

        assertThat(registry.lookup(new SchemaSignatureBuilder().build(bytesSchema("Signature")),
                bytesSchema("Signature"), LookupContext.EMPTY))
                .contains(new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "Signature"));

        assertThat(registry.lookup(new SchemaSignatureBuilder().build(bytesSchema("DataHash")),
                bytesSchema("DataHash"), LookupContext.EMPTY))
                .contains(new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "DataHash"));

        assertThat(registry.lookup(new SchemaSignatureBuilder().build(bytesSchema("Hash")),
                bytesSchema("Hash"), LookupContext.EMPTY))
                .contains(new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "Hash"));
    }

    private BlueprintSchema bytesSchema(String title) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(title);
        schema.setDataType(BlueprintDatatype.bytes);
        return schema;
    }
}
