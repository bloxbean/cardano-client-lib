package com.bloxbean.cardano.client.plutus.aiken.blueprint.registry;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.registry.AnnotationHintDescriptor;
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
 * Registry seeded with the CIP-57 schemas emitted by Aiken stdlib v3.x.
 * Verified against stdlib 3.0 and 3.1 (no new shared types in 3.1).
 */
public class AikenBlueprintTypeRegistry implements BlueprintTypeRegistry {

    private static final String STD_PKG = "com.bloxbean.cardano.client.plutus.aiken.blueprint.std";

    // Type names that appear in multiple places (registered type, schema title,
    // and inner constructor title). Extracted to constants so a rename or typo
    // can't drift between sites.
    private static final String TYPE_VERIFICATION_KEY = "VerificationKey";
    private static final String TYPE_SCRIPT = "Script";
    private static final String TYPE_ADDRESS = "Address";
    private static final String TYPE_OUTPUT_REFERENCE = "OutputReference";
    private static final String TYPE_INTERVAL_BOUND = "IntervalBound";
    private static final String TYPE_VALIDITY_RANGE = "ValidityRange";

    private final Map<SchemaSignature, RegisteredType> mappings;
    private final SchemaSignatureBuilder signatureBuilder;

    public AikenBlueprintTypeRegistry() {
        this(new SchemaSignatureBuilder());
    }

    AikenBlueprintTypeRegistry(SchemaSignatureBuilder signatureBuilder) {
        this.signatureBuilder = signatureBuilder;
        this.mappings = new HashMap<>();
        registerBytesWrappers();
        registerStdlibTypes();
    }

    @Override
    public List<AnnotationHintDescriptor> annotationHints() {
        return List.of();
    }

    @Override
    public Optional<RegisteredType> lookup(SchemaSignature signature, BlueprintSchema schema, LookupContext context) {
        return Optional.ofNullable(mappings.get(signature));
    }

    // ── Bytes wrappers (independent of any stdlib structural shape) ─────────

    private void registerBytesWrappers() {
        register(bytesSchema(TYPE_VERIFICATION_KEY), new RegisteredType(STD_PKG, TYPE_VERIFICATION_KEY));
        register(bytesSchema(TYPE_SCRIPT), new RegisteredType(STD_PKG, TYPE_SCRIPT));
        register(bytesSchema("Signature"), new RegisteredType(STD_PKG, "Signature"));
        register(bytesSchema("VerificationKeyHash"), new RegisteredType(STD_PKG, "VerificationKeyHash"));
        register(bytesSchema("ScriptHash"), new RegisteredType(STD_PKG, "ScriptHash"));
        register(bytesSchema("DataHash"), new RegisteredType(STD_PKG, "DataHash"));
        register(bytesSchema("Hash"), new RegisteredType(STD_PKG, "Hash"));
        register(bytesSchema("PolicyId"), new RegisteredType(STD_PKG, "PolicyId"));
        register(bytesSchema("AssetName"), new RegisteredType(STD_PKG, "AssetName"));
        register(intervalBoundTypeSchema(), new RegisteredType(STD_PKG, "IntervalBoundType"));
    }

    // ── Aiken stdlib v3.x ───────────────────────────────────────────────────

    private void registerStdlibTypes() {
        RegisteredType paymentCredentialType = new RegisteredType(STD_PKG, "PaymentCredential");
        register(credentialSchema("Credential"), paymentCredentialType);
        register(credentialSchema("PaymentCredential"), paymentCredentialType);
        register(stakeCredentialSchema(), new RegisteredType(STD_PKG, "StakeCredential"));
        register(addressSchema(), new RegisteredType(STD_PKG, TYPE_ADDRESS));
        register(outputReferenceSchema(), new RegisteredType(STD_PKG, TYPE_OUTPUT_REFERENCE));
        register(intervalBoundSchema(), new RegisteredType(STD_PKG, TYPE_INTERVAL_BOUND));
        register(validityRangeSchema(), new RegisteredType(STD_PKG, TYPE_VALIDITY_RANGE));
    }

    private void register(BlueprintSchema schema, RegisteredType type) {
        Objects.requireNonNull(schema, "schema cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        SchemaSignature signature = signatureBuilder.build(schema);
        mappings.put(signature, type);
    }

    // ── Schema builders ─────────────────────────────────────────────────────

    /**
     * stdlib v3 Credential / PaymentCredential: VerificationKey / Script with
     * namespaced hash refs.
     *
     * @param title "Credential" or "PaymentCredential" (structurally identical)
     */
    static BlueprintSchema credentialSchema(String title) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(title);
        schema.setDescription("A general structure for representing an on-chain `Credential`.\n\n Credentials are always one of two kinds: a direct public/private key\n pair, or a script (native or Plutus).");
        schema.setAnyOf(List.of(
                constructor(TYPE_VERIFICATION_KEY, 0, List.of(defRef("aiken/crypto/VerificationKeyHash"))),
                constructor(TYPE_SCRIPT, 1, List.of(defRef("aiken/crypto/ScriptHash")))
        ));
        return schema;
    }

    /** stdlib v3 StakeCredential: Inline ref cardano/address/Credential, Pointer with three Ints. */
    static BlueprintSchema stakeCredentialSchema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("StakeCredential");
        schema.setDescription("Represent a type of object that can be represented either inline (by hash)\n or via a reference (i.e. a pointer to an on-chain location).\n\n This is mainly use for capturing pointers to a stake credential\n registration certificate in the case of so-called pointer addresses.");
        schema.setAnyOf(List.of(
                constructor("Inline", 0, List.of(defRef("cardano/address/Credential"))),
                constructor("Pointer", 1, List.of(
                        titled(defRef("Int"), "slot_number"),
                        titled(defRef("Int"), "transaction_index"),
                        titled(defRef("Int"), "certificate_index")
                ))
        ));
        return schema;
    }

    /** stdlib v3 Address: cardano/address/PaymentCredential + Option&lt;cardano/address/StakeCredential&gt;. */
    static BlueprintSchema addressSchema() {
        BlueprintSchema addressConstructor = constructor(TYPE_ADDRESS, 0, List.of(
                titled(defRef("cardano/address/PaymentCredential"), "payment_credential"),
                titled(defRef("Option<cardano/address/StakeCredential>"), "stake_credential")
        ));

        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(TYPE_ADDRESS);
        schema.setDescription("A Cardano `Address` typically holding one or two credential references.\n\n Note that legacy bootstrap addresses (a.k.a. 'Byron addresses') are\n completely excluded from Plutus contexts. Thus, from an on-chain\n perspective only exists addresses of type 00, 01, ..., 07 as detailed\n in [CIP-0019 :: Shelley Addresses](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0019/#shelley-addresses).");
        schema.setAnyOf(List.of(addressConstructor));
        return schema;
    }

    /** stdlib v3 OutputReference: flat ByteArray + Int. */
    static BlueprintSchema outputReferenceSchema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(TYPE_OUTPUT_REFERENCE);
        schema.setDescription("An `OutputReference` is a unique reference to an output on-chain. The `output_index`\n corresponds to the position in the output list of the transaction (identified by its id)\n that produced that output");
        schema.setAnyOf(List.of(
                constructor(TYPE_OUTPUT_REFERENCE, 0, List.of(
                        titled(defRef("ByteArray"), "transaction_id"),
                        titled(defRef("Int"), "output_index")
                ))
        ));
        return schema;
    }

    /** IntervalBoundType: NegativeInfinity, Finite(Int), PositiveInfinity. */
    static BlueprintSchema intervalBoundTypeSchema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("IntervalBoundType");
        schema.setAnyOf(List.of(
                constructor("NegativeInfinity", 0, List.of()),
                constructor("Finite", 1, List.of(defRef("Int"))),
                constructor("PositiveInfinity", 2, List.of())
        ));
        return schema;
    }

    /** stdlib v3 IntervalBound: refs IntervalBoundType&lt;Int&gt; + Bool. */
    static BlueprintSchema intervalBoundSchema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(TYPE_INTERVAL_BOUND);
        schema.setAnyOf(List.of(
                constructor(TYPE_INTERVAL_BOUND, 0, List.of(
                        titled(defRef("aiken/interval/IntervalBoundType<Int>"), "bound_type"),
                        titled(defRef("Bool"), "is_inclusive")
                ))
        ));
        return schema;
    }

    /** stdlib v3 ValidityRange: refs IntervalBound&lt;Int&gt;. */
    static BlueprintSchema validityRangeSchema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(TYPE_VALIDITY_RANGE);
        schema.setAnyOf(List.of(
                constructor(TYPE_VALIDITY_RANGE, 0, List.of(
                        titled(defRef("aiken/interval/IntervalBound<Int>"), "lower_bound"),
                        titled(defRef("aiken/interval/IntervalBound<Int>"), "upper_bound")
                ))
        ));
        return schema;
    }

    // ── Primitive schema helpers ────────────────────────────────────────────

    private static BlueprintSchema constructor(String title, int index, List<BlueprintSchema> fields) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(title);
        schema.setDataType(BlueprintDatatype.constructor);
        schema.setIndex(index);
        schema.setFields(fields);
        return schema;
    }

    /** Creates a {@code $ref} to {@code #/definitions/<key>}, escaping {@code /} as {@code ~1} per JSON Pointer. */
    private static BlueprintSchema defRef(String key) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setRef("#/definitions/" + key.replace("/", "~1"));
        return schema;
    }

    private static BlueprintSchema titled(BlueprintSchema schema, String title) {
        schema.setTitle(title);
        return schema;
    }

    private static BlueprintSchema bytesSchema(String title) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(title);
        schema.setDataType(BlueprintDatatype.bytes);
        return schema;
    }
}
