package com.bloxbean.cardano.client.plutus.aiken.blueprint.registry;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.registry.AnnotationHintDescriptor;
import com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistry;
import com.bloxbean.cardano.client.plutus.blueprint.registry.LookupContext;
import com.bloxbean.cardano.client.plutus.blueprint.registry.RegisteredType;
import com.bloxbean.cardano.client.plutus.blueprint.registry.SchemaSignature;
import com.bloxbean.cardano.client.plutus.blueprint.registry.SchemaSignatureBuilder;

import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default registry seeded with standard CIP-57 schemas. Registers all known
 * schema patterns grouped by Aiken stdlib version.
 *
 * <p>When the lookup context contains a {@value HINT_STDLIB_VERSION} hint,
 * only types registered for that version (plus common types) are returned.
 * When no hint is present, defaults to {@code V3}.</p>
 */
public class AikenBlueprintTypeRegistry implements BlueprintTypeRegistry {

    /** Hint key used to pass the Aiken stdlib version through {@link LookupContext}. */
    public static final String HINT_STDLIB_VERSION = "aiken.stdlib.version";

    private static final String STD_PKG = "com.bloxbean.cardano.client.plutus.aiken.blueprint.std";

    private final Map<SchemaSignature, RegisteredType> commonMappings;
    private final Map<String, Map<SchemaSignature, RegisteredType>> versionedMappings;
    private final SchemaSignatureBuilder signatureBuilder;

    public AikenBlueprintTypeRegistry() {
        this(new SchemaSignatureBuilder());
    }

    AikenBlueprintTypeRegistry(SchemaSignatureBuilder signatureBuilder) {
        this.signatureBuilder = signatureBuilder;
        this.commonMappings = new HashMap<>();
        this.versionedMappings = new HashMap<>();
        for (AikenStdlibVersion v : AikenStdlibVersion.values()) {
            versionedMappings.put(v.name(), new HashMap<>());
        }
        registerBytesWrappers();
        registerStdlibV1Types();
        registerStdlibV2Types();
        registerStdlibV3Types();
    }

    @Override
    public List<AnnotationHintDescriptor> annotationHints() {
        return List.of(new AnnotationHintDescriptor(
                "com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib",
                "value", HINT_STDLIB_VERSION, AikenStdlibVersion.LATEST.name()
        ));
    }

    @Override
    public Optional<RegisteredType> lookup(SchemaSignature signature, BlueprintSchema schema, LookupContext context) {
        // Common types first (version-independent)
        RegisteredType common = commonMappings.get(signature);
        if (common != null) {
            return Optional.of(common);
        }

        // Version-specific lookup
        String version = context.hint(HINT_STDLIB_VERSION).orElse(AikenStdlibVersion.LATEST.name());
        Map<SchemaSignature, RegisteredType> vMap = versionedMappings.get(version);
        if (vMap != null) {
            RegisteredType versioned = vMap.get(signature);
            if (versioned != null) {
                return Optional.of(versioned);
            }
        }

        return Optional.empty();
    }

    // ── Version-independent types ───────────────────────────────────────────

    private void registerBytesWrappers() {
        registerCommonSchema(bytesSchema("VerificationKey"), new RegisteredType(STD_PKG, "VerificationKey"));
        registerCommonSchema(bytesSchema("Script"), new RegisteredType(STD_PKG, "Script"));
        registerCommonSchema(bytesSchema("Signature"), new RegisteredType(STD_PKG, "Signature"));
        registerCommonSchema(bytesSchema("VerificationKeyHash"), new RegisteredType(STD_PKG, "VerificationKeyHash"));
        registerCommonSchema(bytesSchema("ScriptHash"), new RegisteredType(STD_PKG, "ScriptHash"));
        registerCommonSchema(bytesSchema("DataHash"), new RegisteredType(STD_PKG, "DataHash"));
        registerCommonSchema(bytesSchema("Hash"), new RegisteredType(STD_PKG, "Hash"));
        registerCommonSchema(bytesSchema("PolicyId"), new RegisteredType(STD_PKG, "PolicyId"));
        registerCommonSchema(bytesSchema("AssetName"), new RegisteredType(STD_PKG, "AssetName"));
        registerCommonSchema(intervalBoundTypeSchema(), new RegisteredType(STD_PKG, "IntervalBoundType"));
    }

    // ── Aiken stdlib v1 (>= 1.9.0, < 2.0.0) ───────────────────────────────

    private void registerStdlibV1Types() {
        String v1 = AikenStdlibVersion.V1.name();
        registerVersionedSchema(v1, credentialV1Schema(), new RegisteredType(STD_PKG, "Credential"));
        registerVersionedSchema(v1, referencedV1Schema(), new RegisteredType(STD_PKG, "ReferencedCredential"));
        registerVersionedSchema(v1, addressV1Schema(), new RegisteredType(STD_PKG, "Address"));
        registerVersionedSchema(v1, outputReferenceV1Schema(), new RegisteredType(STD_PKG, "OutputReferenceV1"));
        registerVersionedSchema(v1, intervalBoundV1Schema(), new RegisteredType(STD_PKG, "IntervalBound"));
        registerVersionedSchema(v1, validityRangeV1Schema(), new RegisteredType(STD_PKG, "ValidityRange"));
    }

    // ── Aiken stdlib v2 (>= 2.0.0, < 3.0.0) ───────────────────────────────

    private void registerStdlibV2Types() {
        String v2 = AikenStdlibVersion.V2.name();
        RegisteredType paymentCredentialType = new RegisteredType(STD_PKG, "PaymentCredential");

        registerVersionedSchema(v2, credentialV2Schema("Credential"), paymentCredentialType);
        registerVersionedSchema(v2, credentialV2Schema("PaymentCredential"), paymentCredentialType);
        registerVersionedSchema(v2, stakeCredentialV2Schema(), new RegisteredType(STD_PKG, "StakeCredential"));
        registerVersionedSchema(v2, addressV2Schema(), new RegisteredType(STD_PKG, "Address"));
        registerVersionedSchema(v2, outputReferenceV2Schema(), new RegisteredType(STD_PKG, "OutputReference"));
    }

    // ── Aiken stdlib v3 (>= 3.0.0) ─────────────────────────────────────────

    private void registerStdlibV3Types() {
        String v3 = AikenStdlibVersion.V3.name();
        RegisteredType paymentCredentialType = new RegisteredType(STD_PKG, "PaymentCredential");

        registerVersionedSchema(v3, credentialV3Schema("Credential"), paymentCredentialType);
        registerVersionedSchema(v3, credentialV3Schema("PaymentCredential"), paymentCredentialType);
        registerVersionedSchema(v3, addressV3Schema(), new RegisteredType(STD_PKG, "Address"));
        // StakeCredential and OutputReference schemas are structurally identical to v2;
        // register them under V3 as well so V3 lookups find them
        registerVersionedSchema(v3, stakeCredentialV2Schema(), new RegisteredType(STD_PKG, "StakeCredential"));
        registerVersionedSchema(v3, outputReferenceV2Schema(), new RegisteredType(STD_PKG, "OutputReference"));
        registerVersionedSchema(v3, intervalBoundV3Schema(), new RegisteredType(STD_PKG, "IntervalBound"));
        registerVersionedSchema(v3, validityRangeV3Schema(), new RegisteredType(STD_PKG, "ValidityRange"));
    }

    // ── Registration helpers ─────────────────────────────────────────────────

    protected void registerSchema(BlueprintSchema schema, RegisteredType type) {
        registerCommonSchema(schema, type);
    }

    private void registerCommonSchema(BlueprintSchema schema, RegisteredType type) {
        Objects.requireNonNull(schema, "schema cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        SchemaSignature signature = signatureBuilder.build(schema);
        commonMappings.put(signature, type);
    }

    private void registerVersionedSchema(String version, BlueprintSchema schema, RegisteredType type) {
        Objects.requireNonNull(schema, "schema cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        SchemaSignature signature = signatureBuilder.build(schema);
        versionedMappings.get(version).put(signature, type);
    }

    // ── Schema builders: Credential ─────────────────────────────────────────

    /** stdlib v1 Credential: VerificationKeyCredential/ScriptCredential with ByteArray refs. */
    static BlueprintSchema credentialV1Schema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Credential");
        schema.setDescription("A general structure for representing an on-chain `Credential`.\n\n Credentials are always one of two kinds: a direct public/private key\n pair, or a script (native or Plutus).");
        schema.setAnyOf(List.of(
                constructor("VerificationKeyCredential", 0, List.of(defRef("ByteArray"))),
                constructor("ScriptCredential", 1, List.of(defRef("ByteArray")))
        ));
        return schema;
    }

    /**
     * stdlib v2 Credential/PaymentCredential: VerificationKey/Script with bare hash refs.
     *
     * @param title "Credential" or "PaymentCredential"
     */
    static BlueprintSchema credentialV2Schema(String title) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(title);
        schema.setDescription("A general structure for representing an on-chain `Credential`.\n\n Credentials are always one of two kinds: a direct public/private key\n pair, or a script (native or Plutus).");
        schema.setAnyOf(List.of(
                constructor("VerificationKey", 0, List.of(defRef("VerificationKeyHash"))),
                constructor("Script", 1, List.of(defRef("ScriptHash")))
        ));
        return schema;
    }

    /**
     * stdlib v3 Credential/PaymentCredential: VerificationKey/Script with namespaced hash refs.
     *
     * @param title "Credential" or "PaymentCredential"
     */
    static BlueprintSchema credentialV3Schema(String title) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle(title);
        schema.setDescription("A general structure for representing an on-chain `Credential`.\n\n Credentials are always one of two kinds: a direct public/private key\n pair, or a script (native or Plutus).");
        schema.setAnyOf(List.of(
                constructor("VerificationKey", 0, List.of(defRef("aiken/crypto/VerificationKeyHash"))),
                constructor("Script", 1, List.of(defRef("aiken/crypto/ScriptHash")))
        ));
        return schema;
    }

    // ── Schema builders: Referenced / StakeCredential ────────────────────────

    /** stdlib v1 Referenced: Inline ref aiken/transaction/credential/Credential. */
    static BlueprintSchema referencedV1Schema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Referenced");
        schema.setDescription("Represent a type of object that can be represented either inline (by hash)\n or via a reference (i.e. a pointer to an on-chain location).\n\n This is mainly use for capturing pointers to a stake credential\n registration certificate in the case of so-called pointer addresses.");
        schema.setAnyOf(List.of(
                constructor("Inline", 0, List.of(defRef("aiken/transaction/credential/Credential"))),
                constructor("Pointer", 1, List.of(
                        titled(defRef("Int"), "slot_number"),
                        titled(defRef("Int"), "transaction_index"),
                        titled(defRef("Int"), "certificate_index")
                ))
        ));
        return schema;
    }

    /** stdlib v2/v3 StakeCredential: Inline ref cardano/address/Credential. */
    static BlueprintSchema stakeCredentialV2Schema() {
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

    // ── Schema builders: Address ─────────────────────────────────────────────

    /** stdlib v1 Address: aiken/transaction/credential/Credential + Option$Referenced. */
    static BlueprintSchema addressV1Schema() {
        BlueprintSchema addressConstructor = constructor("Address", 0, List.of(
                titled(defRef("aiken/transaction/credential/Credential"), "payment_credential"),
                titled(defRef("Option$aiken/transaction/credential/Referenced$aiken/transaction/credential/Credential"), "stake_credential")
        ));

        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Address");
        schema.setDescription("A Cardano `Address` typically holding one or two credential references.\n\n Note that legacy bootstrap addresses (a.k.a. 'Byron addresses') are\n completely excluded from Plutus contexts. Thus, from an on-chain\n perspective only exists addresses of type 00, 01, ..., 07 as detailed\n in [CIP-0019 :: Shelley Addresses](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0019/#shelley-addresses).");
        schema.setAnyOf(List.of(addressConstructor));
        return schema;
    }

    /** stdlib v2 Address: PaymentCredential + Option$StakeCredential. */
    static BlueprintSchema addressV2Schema() {
        BlueprintSchema addressConstructor = constructor("Address", 0, List.of(
                titled(defRef("PaymentCredential"), "payment_credential"),
                titled(defRef("Option$StakeCredential"), "stake_credential")
        ));

        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Address");
        schema.setDescription("A Cardano `Address` typically holding one or two credential references.\n\n Note that legacy bootstrap addresses (a.k.a. 'Byron addresses') are\n completely excluded from Plutus contexts. Thus, from an on-chain\n perspective only exists addresses of type 00, 01, ..., 07 as detailed\n in [CIP-0019 :: Shelley Addresses](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0019/#shelley-addresses).");
        schema.setAnyOf(List.of(addressConstructor));
        return schema;
    }

    /** stdlib v3 Address: cardano/address/PaymentCredential + Option&lt;cardano/address/StakeCredential&gt;. */
    static BlueprintSchema addressV3Schema() {
        BlueprintSchema addressConstructor = constructor("Address", 0, List.of(
                titled(defRef("cardano/address/PaymentCredential"), "payment_credential"),
                titled(defRef("Option<cardano/address/StakeCredential>"), "stake_credential")
        ));

        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Address");
        schema.setDescription("A Cardano `Address` typically holding one or two credential references.\n\n Note that legacy bootstrap addresses (a.k.a. 'Byron addresses') are\n completely excluded from Plutus contexts. Thus, from an on-chain\n perspective only exists addresses of type 00, 01, ..., 07 as detailed\n in [CIP-0019 :: Shelley Addresses](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0019/#shelley-addresses).");
        schema.setAnyOf(List.of(addressConstructor));
        return schema;
    }

    // ── Schema builders: OutputReference ─────────────────────────────────────

    /** stdlib v1 OutputReference: nested TransactionId wrapper + Int. */
    static BlueprintSchema outputReferenceV1Schema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("OutputReference");
        schema.setDescription("An `OutputReference` is a unique reference to an output on-chain. The `output_index`\n corresponds to the position in the output list of the transaction (identified by its id)\n that produced that output");
        schema.setAnyOf(List.of(
                constructor("OutputReference", 0, List.of(
                        titled(defRef("aiken/transaction/TransactionId"), "transaction_id"),
                        titled(defRef("Int"), "output_index")
                ))
        ));
        return schema;
    }

    /** stdlib v2/v3 OutputReference: flat ByteArray + Int. */
    static BlueprintSchema outputReferenceV2Schema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("OutputReference");
        schema.setDescription("An `OutputReference` is a unique reference to an output on-chain. The `output_index`\n corresponds to the position in the output list of the transaction (identified by its id)\n that produced that output");
        schema.setAnyOf(List.of(
                constructor("OutputReference", 0, List.of(
                        titled(defRef("ByteArray"), "transaction_id"),
                        titled(defRef("Int"), "output_index")
                ))
        ));
        return schema;
    }

    // ── Schema builders: IntervalBoundType / IntervalBound / ValidityRange ──

    /** IntervalBoundType: 3 variants — NegativeInfinity, Finite(Int), PositiveInfinity. Identical across all versions. */
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

    /** stdlib v1 IntervalBound: refs IntervalBoundType$Int + Bool. */
    static BlueprintSchema intervalBoundV1Schema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("IntervalBound");
        schema.setAnyOf(List.of(
                constructor("IntervalBound", 0, List.of(
                        titled(defRef("aiken/interval/IntervalBoundType$Int"), "bound_type"),
                        titled(defRef("Bool"), "is_inclusive")
                ))
        ));
        return schema;
    }

    /** stdlib v3 IntervalBound: refs IntervalBoundType&lt;Int&gt; + Bool. */
    static BlueprintSchema intervalBoundV3Schema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("IntervalBound");
        schema.setAnyOf(List.of(
                constructor("IntervalBound", 0, List.of(
                        titled(defRef("aiken/interval/IntervalBoundType<Int>"), "bound_type"),
                        titled(defRef("Bool"), "is_inclusive")
                ))
        ));
        return schema;
    }

    /** stdlib v1 ValidityRange (title: "Interval"): refs IntervalBound$Int. */
    static BlueprintSchema validityRangeV1Schema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Interval");
        schema.setAnyOf(List.of(
                constructor("Interval", 0, List.of(
                        titled(defRef("aiken/interval/IntervalBound$Int"), "lower_bound"),
                        titled(defRef("aiken/interval/IntervalBound$Int"), "upper_bound")
                ))
        ));
        return schema;
    }

    /** stdlib v3 ValidityRange: refs IntervalBound&lt;Int&gt;. */
    static BlueprintSchema validityRangeV3Schema() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("ValidityRange");
        schema.setAnyOf(List.of(
                constructor("ValidityRange", 0, List.of(
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
