package com.bloxbean.cardano.client.plutus.aiken.blueprint.registry;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistry;
import com.bloxbean.cardano.client.plutus.blueprint.registry.LookupContext;
import com.bloxbean.cardano.client.plutus.blueprint.registry.RegisteredType;
import com.bloxbean.cardano.client.plutus.blueprint.registry.SchemaSignature;
import com.bloxbean.cardano.client.plutus.blueprint.registry.SchemaSignatureBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default registry seeded with standard CIP-57 schemas. Currently acts as a placeholder and can be
 * expanded incrementally as signatures are curated.
 */
public class AikenBlueprintTypeRegistry implements BlueprintTypeRegistry {

    private final Map<SchemaSignature, RegisteredType> mappings;
    private final SchemaSignatureBuilder signatureBuilder;

    public AikenBlueprintTypeRegistry() {
        this(new SchemaSignatureBuilder());
    }

    AikenBlueprintTypeRegistry(SchemaSignatureBuilder signatureBuilder) {
        this.signatureBuilder = signatureBuilder;
        this.mappings = new HashMap<>();
        registerTuplePair();
        registerCredential();
        registerReferencedCredential();
        registerAddress();
        registerHashWrappers();
    }

    @Override
    public Optional<RegisteredType> lookup(SchemaSignature signature, BlueprintSchema schema, LookupContext context) {
        return Optional.ofNullable(mappings.get(signature));
    }

    private void registerTuplePair() {
        BlueprintSchema tupleSchema = new BlueprintSchema();
        tupleSchema.setTitle("Tuple");
        tupleSchema.setDataType(BlueprintDatatype.list);

        tupleSchema.setItems(List.of(ref("#/definitions/ByteArray"), ref("#/definitions/ByteArray")));

        SchemaSignature signature = signatureBuilder.build(tupleSchema);
        mappings.put(signature, new RegisteredType("com.bloxbean.cardano.client.plutus.blueprint.type", "Pair"));
    }

    private void registerCredential() {
        registerSchema(credentialSchema(), new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "Credential"));
    }

    private void registerReferencedCredential() {
        registerSchema(referencedCredentialSchema(), new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "ReferencedCredential"));
    }

    private void registerAddress() {
        registerSchema(addressSchema(), new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "Address"));
    }

    protected void registerSchema(BlueprintSchema schema, RegisteredType type) {
        Objects.requireNonNull(schema, "schema cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        SchemaSignature signature = signatureBuilder.build(schema);
        mappings.put(signature, type);
    }

    static BlueprintSchema credentialSchema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Credential");
        schema.setDescription("A general structure for representing an on-chain `Credential`.\n\n Credentials are always one of two kinds: a direct public/private key\n pair, or a script (native or Plutus).");
        schema.setAnyOf(List.of(
                constructor("VerificationKeyCredential", 0, List.of(ref("#/definitions/ByteArray"))),
                constructor("ScriptCredential", 1, List.of(ref("#/definitions/ByteArray")))
        ));
        return schema;
    }

    static BlueprintSchema referencedCredentialSchema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Referenced");
        schema.setDescription("Represent a type of object that can be represented either inline (by hash)\n or via a reference (i.e. a pointer to an on-chain location).\n\n This is mainly use for capturing pointers to a stake credential\n registration certificate in the case of so-called pointer addresses.");
        schema.setAnyOf(List.of(
                constructor("Inline", 0, List.of(ref("#/definitions/aiken~1transaction~1credential~1Credential"))),
                constructor("Pointer", 1, List.of(
                        titled(ref("#/definitions/Int"), "slot_number"),
                        titled(ref("#/definitions/Int"), "transaction_index"),
                        titled(ref("#/definitions/Int"), "certificate_index")
                ))
        ));
        return schema;
    }

    static BlueprintSchema addressSchema() {
        BlueprintSchema addressConstructor = constructor("Address", 0, List.of(
                titled(ref("#/definitions/aiken~1transaction~1credential~1Credential"), "payment_credential"),
                titled(ref("#/definitions/Option$aiken~1transaction~1credential~1Referenced$aiken~1transaction~1credential~1Credential"), "stake_credential")
        ));

        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Address");
        schema.setDescription("A Cardano `Address` typically holding one or two credential references.\n\n Note that legacy bootstrap addresses (a.k.a. 'Byron addresses') are\n completely excluded from Plutus contexts. Thus, from an on-chain\n perspective only exists addresses of type 00, 01, ..., 07 as detailed\n in [CIP-0019 :: Shelley Addresses](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0019/#shelley-addresses).");
        schema.setAnyOf(List.of(addressConstructor));
        return schema;
    }

    private static BlueprintSchema constructor(String title, int index, List<BlueprintSchema> fields) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(title);
        schema.setDataType(BlueprintDatatype.constructor);
        schema.setIndex(index);
        schema.setFields(fields);
        return schema;
    }

    private static BlueprintSchema ref(String ref) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setRef(ref);
        return schema;
    }

    private static BlueprintSchema titled(BlueprintSchema schema, String title) {
        schema.setTitle(title);
        return schema;
    }

    private void registerHashWrappers() {
        registerSchema(bytesSchema("VerificationKey"), new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "VerificationKey"));
        registerSchema(bytesSchema("Script"), new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "Script"));
        registerSchema(bytesSchema("Signature"), new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "Signature"));
        registerSchema(bytesSchema("VerificationKeyHash"), new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "VerificationKeyHash"));
        registerSchema(bytesSchema("ScriptHash"), new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "ScriptHash"));
        registerSchema(bytesSchema("DataHash"), new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "DataHash"));
        registerSchema(bytesSchema("Hash"), new RegisteredType("com.bloxbean.cardano.client.plutus.aiken.blueprint.std", "Hash"));
    }

    private static BlueprintSchema bytesSchema(String title) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(title);
        schema.setDataType(BlueprintDatatype.bytes);
        return schema;
    }
}
