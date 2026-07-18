package com.bloxbean.cardano.client.txflow.compile;

import com.bloxbean.cardano.client.quicktx.intent.CollectFromIntent;
import com.bloxbean.cardano.client.quicktx.intent.FlowOutputRef;
import com.bloxbean.cardano.client.quicktx.intent.ReferenceInputIntent;
import com.bloxbean.cardano.client.quicktx.intent.ScriptCollectFromIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.quicktx.intent.UtxoRef;
import com.bloxbean.cardano.client.quicktx.serialization.TransactionDocument;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.exc.IgnoredPropertyException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compiler-local boundary between a bound portable transaction tree and QuickTx.
 *
 * <p>Standalone QuickTx intentionally accepts unknown fields in several legacy
 * intent DTOs. TxFlow cannot do that because the discarded field would disappear
 * from the compiled definition fingerprint. This materializer therefore uses an
 * isolated strict mapper, validates shapes handled by custom deserializers, and
 * reports every failure against the owning portable document path. It does not
 * change QuickTx's process-wide mapper or legacy deserialization behavior.</p>
 */
final class QuickTxTemplateMaterializer {
    private static final String UNKNOWN_FIELD = "TXFLOW_TRANSACTION_FIELD_UNKNOWN";
    private static final String INVALID_TRANSACTION = "TXFLOW_TRANSACTION_INVALID";
    private static final Set<String> ENVELOPE_FIELDS = Set.of("tx", "context");
    private static final Set<String> CONCRETE_REF_FIELDS = Set.of("tx_hash", "output_index");
    private static final Set<String> FLOW_REF_FIELDS = Set.of("flow_output");
    private static final Set<String> FLOW_POINTER_FIELDS = Set.of("step", "output");
    private static final String VALIDATION_TX_HASH = "0".repeat(64);
    private static final ObjectMapper STRICT_MAPPER = strictMapper();

    /**
     * Strictly materializes one embedded transaction into a fresh one-transaction plan.
     *
     * @param transaction bound portable {@code transaction} object
     * @param path path of that object in the portable flow
     * @return isolated QuickTx plan
     * @throws MaterializationException when the embedded QuickTx definition is lossy or invalid
     */
    TxPlan materialize(JsonNode transaction, String path) throws MaterializationException {
        validateEnvelope(transaction, path);

        JsonNode txNode = transaction.get("tx");
        validateProjectionConflicts(txNode, path + ".tx");
        validateKnownIntentChoices(txNode, path + ".tx");
        validateUtxoReferenceShapes(txNode, path + ".tx");

        TransactionDocument document = new TransactionDocument();
        document.setVersion("1.0");
        document.setContext(strictTreeToValue(transaction.get("context"),
                TransactionDocument.TxContext.class, path + ".context"));
        TransactionDocument.TxContent tx = strictTreeToValue(
                txNode, TransactionDocument.TxContent.class, path + ".tx");
        validateIntents(tx.getInputs(), path + ".tx.inputs");
        validateIntents(tx.getIntents(), path + ".tx.intents");
        validateIntents(tx.getScripts(), path + ".tx.scripts");
        document.setTransaction(List.of(new TransactionDocument.TxEntry(tx)));

        try {
            return TxPlan.from(YamlSerializer.serialize(document));
        } catch (RuntimeException failure) {
            throw invalid(rootMessage(failure, "QuickTx transaction materialization failed"), path);
        }
    }

    private void validateEnvelope(JsonNode transaction, String path)
            throws MaterializationException {
        if (transaction == null || !transaction.isObject()) {
            throw invalid("Embedded QuickTx transaction must be an object", path);
        }
        rejectUnknownFields(transaction, ENVELOPE_FIELDS, path);
        if (!transaction.has("tx") || !transaction.get("tx").isObject()) {
            throw invalid("Embedded QuickTx transaction requires an object-valued tx field",
                    path + ".tx");
        }
        JsonNode context = transaction.get("context");
        if (context != null && !context.isNull() && !context.isObject()) {
            throw invalid("Embedded QuickTx context must be an object", path + ".context");
        }
        if (context != null && context.isObject()) {
            rejectPair(context, "fee_payer", "fee_payer_ref", path + ".context");
            rejectPair(context, "collateral_payer", "collateral_payer_ref",
                    path + ".context");
        }
    }

    private void validateProjectionConflicts(JsonNode tx, String path)
            throws MaterializationException {
        if (tx.hasNonNull("from") && tx.hasNonNull("from_ref")) {
            throw invalid("tx.from and tx.from_ref are mutually exclusive", path);
        }
        boolean hasChangeDatum = tx.hasNonNull("change_datum");
        boolean hasChangeDatumHash = tx.hasNonNull("change_datum_hash");
        if (hasChangeDatum && hasChangeDatumHash) {
            throw invalid("tx.change_datum and tx.change_datum_hash are mutually exclusive", path);
        }
        if ((hasChangeDatum || hasChangeDatumHash) && !tx.hasNonNull("change_address")) {
            throw invalid("tx.change_address is required when a change datum is provided",
                    path + ".change_address");
        }
    }

    private void validateUtxoReferenceShapes(JsonNode tx, String path)
            throws MaterializationException {
        validateIntentArrayReferences(tx.get("inputs"), path + ".inputs");
        validateIntentArrayReferences(tx.get("intents"), path + ".intents");
        validateIntentArrayReferences(tx.get("scripts"), path + ".scripts");
    }

    private void validateKnownIntentChoices(JsonNode tx, String path)
            throws MaterializationException {
        validateIntentChoices(tx.get("inputs"), path + ".inputs");
        validateIntentChoices(tx.get("intents"), path + ".intents");
        validateIntentChoices(tx.get("scripts"), path + ".scripts");
    }

    private void validateIntentChoices(JsonNode intents, String path)
            throws MaterializationException {
        if (intents == null || intents.isNull() || !intents.isArray()) return;
        for (int index = 0; index < intents.size(); index++) {
            JsonNode intent = intents.get(index);
            if (!intent.isObject() || !intent.path("type").isTextual()) continue;
            String intentPath = path + "[" + index + "]";
            switch (intent.path("type").asText()) {
                case "payment":
                    rejectPair(intent, "datum_hex", "datum_hash", intentPath);
                    rejectPair(intent, "datum_hex", "datum", intentPath);
                    rejectPair(intent, "datum_hash", "datum", intentPath);
                    break;
                case "script_collect_from":
                    rejectPair(intent, "redeemer_hex", "redeemer", intentPath);
                    rejectPair(intent, "datum_hex", "datum", intentPath);
                    rejectPair(intent, "utxo_refs", "utxo_filter", intentPath);
                    rejectPair(intent, "utxo_refs", "address", intentPath);
                    break;
                case "script_minting":
                    rejectPair(intent, "redeemer_hex", "redeemer", intentPath);
                    rejectPair(intent, "output_datum_hex", "output_datum", intentPath);
                    requireCompanion(intent, "output_datum_hex", "receiver", intentPath);
                    requireCompanion(intent, "output_datum", "receiver", intentPath);
                    break;
                case "minting":
                    rejectPair(intent, "policy_ref", "script_hex", intentPath);
                    rejectPair(intent, "policy_ref", "script_type", intentPath);
                    break;
                case "native_script":
                    rejectPair(intent, "script_ref", "script_hash", intentPath);
                    rejectPair(intent, "script_ref", "script_hex", intentPath);
                    rejectPair(intent, "script_hash", "script_hex", intentPath);
                    break;
                case "validator":
                    rejectPair(intent, "script_ref", "script_hash", intentPath);
                    rejectPair(intent, "script_ref", "cbor_hex", intentPath);
                    rejectPair(intent, "script_ref", "version", intentPath);
                    rejectPair(intent, "script_hash", "cbor_hex", intentPath);
                    rejectPair(intent, "script_hash", "version", intentPath);
                    break;
                case "voting_delegation":
                    rejectPair(intent, "drep_hex", "drep_type", intentPath);
                    rejectPair(intent, "drep_hex", "drep_hash", intentPath);
                    requireCompanion(intent, "drep_hash", "drep_type", intentPath);
                    rejectHashForHashlessDrep(intent, intentPath);
                    break;
                case "drep_registration":
                case "drep_update":
                case "governance_proposal":
                case "voting":
                    requireCompanion(intent, "anchor_hash", "anchor_url", intentPath);
                    break;
                case "pool_registration":
                    requireBooleanValue(intent, "is_update", false, intentPath);
                    break;
                case "pool_update":
                    requireBooleanValue(intent, "is_update", true, intentPath);
                    break;
                default:
                    break;
            }
        }
    }

    private void rejectPair(JsonNode object, String first, String second, String path)
            throws MaterializationException {
        if (hasValue(object, first) && hasValue(object, second)) {
            throw invalid(first + " and " + second + " are mutually exclusive",
                    path + "." + second);
        }
    }

    private void requireCompanion(JsonNode object, String valueField, String companion,
                                  String path) throws MaterializationException {
        if (hasValue(object, valueField) && !hasValue(object, companion)) {
            throw invalid(companion + " is required when " + valueField + " is provided",
                    path + "." + companion);
        }
    }

    private void requireBooleanValue(JsonNode object, String field, boolean expected, String path)
            throws MaterializationException {
        if (!hasValue(object, field)) return;
        JsonNode value = object.get(field);
        if (!value.isBoolean() || value.booleanValue() != expected) {
            throw invalid(field + " must be " + expected + " for type "
                            + object.path("type").asText(), path + "." + field);
        }
    }

    private void rejectHashForHashlessDrep(JsonNode intent, String path)
            throws MaterializationException {
        if (!hasValue(intent, "drep_hash") || !intent.path("drep_type").isTextual()) return;
        String type = intent.path("drep_type").asText();
        if ("abstain".equals(type) || "no_confidence".equals(type)) {
            throw invalid("drep_hash is not used by drep_type " + type,
                    path + ".drep_hash");
        }
    }

    private boolean hasValue(JsonNode object, String field) {
        return object.has(field) && !object.get(field).isNull();
    }

    private void validateIntentArrayReferences(JsonNode intents, String path)
            throws MaterializationException {
        if (intents == null || intents.isNull() || !intents.isArray()) return;
        for (int index = 0; index < intents.size(); index++) {
            JsonNode intent = intents.get(index);
            if (!intent.isObject() || !intent.path("type").isTextual()) continue;
            String intentPath = path + "[" + index + "]";
            String type = intent.path("type").asText();
            if ("collect_from".equals(type)) {
                if (intent.has("refs") && intent.has("utxo_refs")) {
                    throw invalid("collect_from cannot declare both refs and utxo_refs", intentPath);
                }
                String field = intent.has("utxo_refs") ? "utxo_refs" : "refs";
                validateReferenceArray(intent.get(field), intentPath + "." + field);
            } else if ("reference_input".equals(type)) {
                validateReferenceArray(intent.get("refs"), intentPath + ".refs");
            } else if ("script_collect_from".equals(type)) {
                validateReferenceArray(intent.get("utxo_refs"), intentPath + ".utxo_refs");
            }
        }
    }

    private void validateReferenceArray(JsonNode references, String path)
            throws MaterializationException {
        if (references == null || references.isNull()) return;
        if (!references.isArray()) {
            throw invalid("QuickTx UTXO references must be an array", path);
        }
        for (int index = 0; index < references.size(); index++) {
            validateReference(references.get(index), path + "[" + index + "]");
        }
    }

    private void validateReference(JsonNode reference, String path)
            throws MaterializationException {
        if (!reference.isObject()) {
            throw invalid("QuickTx UTXO reference must be an object", path);
        }
        if (reference.has("flow_output")) {
            rejectUnknownFields(reference, FLOW_REF_FIELDS, path);
            JsonNode pointer = reference.get("flow_output");
            if (!pointer.isObject()) {
                throw invalid("flow_output must be an object", path + ".flow_output");
            }
            rejectUnknownFields(pointer, FLOW_POINTER_FIELDS, path + ".flow_output");
            requireNonBlankText(pointer, "step", path + ".flow_output.step");
            requireNonBlankText(pointer, "output", path + ".flow_output.output");
            return;
        }

        rejectUnknownFields(reference, CONCRETE_REF_FIELDS, path);
        requireNonBlankText(reference, "tx_hash", path + ".tx_hash");
        JsonNode outputIndex = reference.get("output_index");
        if (outputIndex == null || outputIndex.isNull()
                || (!outputIndex.isIntegralNumber() && !outputIndex.isTextual())) {
            throw invalid("output_index must be an integer or string", path + ".output_index");
        }
    }

    private void requireNonBlankText(JsonNode object, String field, String path)
            throws MaterializationException {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid(field + " must be a non-blank string", path);
        }
    }

    private void rejectUnknownFields(JsonNode object, Set<String> allowed, String path)
            throws MaterializationException {
        TreeSet<String> unknown = new TreeSet<>();
        object.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) unknown.add(field);
        });
        if (!unknown.isEmpty()) {
            String field = unknown.first();
            throw unknown(field, path + "." + field);
        }
    }

    private <T> T strictTreeToValue(JsonNode value, Class<T> type, String path)
            throws MaterializationException {
        if (value == null || value.isNull()) return null;
        try {
            return STRICT_MAPPER.treeToValue(value, type);
        } catch (UnrecognizedPropertyException failure) {
            throw unknown(failure.getPropertyName(), appendMappingPath(path, failure));
        } catch (IgnoredPropertyException failure) {
            throw unknown(failure.getPropertyName(), appendMappingPath(path, failure));
        } catch (JsonMappingException failure) {
            String failurePath = appendMappingPath(path, failure);
            if (failure instanceof InvalidTypeIdException && !failurePath.endsWith(".type")) {
                failurePath += ".type";
            }
            throw invalid(failure.getOriginalMessage(), failurePath);
        } catch (JsonProcessingException failure) {
            throw invalid(failure.getOriginalMessage(), path);
        }
    }

    private void validateIntents(List<? extends TxIntent> intents, String path)
            throws MaterializationException {
        if (intents == null) return;
        for (int index = 0; index < intents.size(); index++) {
            TxIntent intent = intents.get(index);
            try {
                if (intent == null) throw new IllegalStateException("Intent cannot be null");
                validateIntentWithDeferredReferences(intent);
            } catch (RuntimeException failure) {
                throw invalid(rootMessage(failure, "QuickTx intent validation failed"),
                        path + "[" + index + "]");
            }
        }
    }

    private void validateIntentWithDeferredReferences(TxIntent intent) {
        if (intent instanceof CollectFromIntent) {
            CollectFromIntent collect = (CollectFromIntent) intent;
            List<UtxoRef> original = collect.getUtxoRefs();
            List<UtxoRef> validation = replaceDeferredReferences(original);
            if (validation == original) {
                intent.validate();
            } else {
                collect.setUtxoRefs(validation);
                try {
                    intent.validate();
                } finally {
                    collect.setUtxoRefs(original);
                }
            }
        } else if (intent instanceof ReferenceInputIntent) {
            ReferenceInputIntent reference = (ReferenceInputIntent) intent;
            List<UtxoRef> original = reference.getRefs();
            List<UtxoRef> validation = replaceDeferredReferences(original);
            if (validation == original) {
                intent.validate();
            } else {
                reference.setRefs(validation);
                try {
                    intent.validate();
                } finally {
                    reference.setRefs(original);
                }
            }
        } else if (intent instanceof ScriptCollectFromIntent) {
            ScriptCollectFromIntent collect = (ScriptCollectFromIntent) intent;
            List<UtxoRef> original = collect.getUtxoRefs();
            List<UtxoRef> validation = replaceDeferredReferences(original);
            if (validation == original) {
                intent.validate();
            } else {
                collect.setUtxoRefs(validation);
                try {
                    intent.validate();
                } finally {
                    collect.setUtxoRefs(original);
                }
            }
        } else {
            intent.validate();
        }
    }

    private List<UtxoRef> replaceDeferredReferences(List<UtxoRef> references) {
        if (references == null || references.stream().noneMatch(FlowOutputRef.class::isInstance)) {
            return references;
        }
        List<UtxoRef> validation = new ArrayList<>(references.size());
        for (UtxoRef reference : references) {
            validation.add(reference instanceof FlowOutputRef
                    ? UtxoRef.builder().txHash(VALIDATION_TX_HASH).outputIndex(0).build()
                    : reference);
        }
        return validation;
    }

    private String appendMappingPath(String basePath, JsonMappingException failure) {
        StringBuilder path = new StringBuilder(basePath);
        for (JsonMappingException.Reference reference : failure.getPath()) {
            if (reference.getFieldName() != null) {
                path.append('.').append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private String rootMessage(Throwable failure, String fallback) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? fallback : current.getMessage();
    }

    private MaterializationException unknown(String field, String path) {
        return new MaterializationException(UNKNOWN_FIELD,
                "Unknown QuickTx transaction field: " + field, path);
    }

    private MaterializationException invalid(String message, String path) {
        return new MaterializationException(INVALID_TRANSACTION, message, path);
    }

    private static ObjectMapper strictMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
        mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
            @Override
            public JsonIgnoreProperties.Value findPropertyIgnoralByName(
                    MapperConfig<?> config, Annotated annotated) {
                JsonIgnoreProperties.Value value =
                        super.findPropertyIgnoralByName(config, annotated);
                return value == null ? JsonIgnoreProperties.Value.empty()
                        : value.withoutIgnoreUnknown();
            }
        });
        mapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public boolean handleUnknownProperty(
                    DeserializationContext context, JsonParser parser,
                    JsonDeserializer<?> deserializer, Object beanOrClass,
                    String propertyName) throws IOException {
                // EXISTING_PROPERTY exposes the already-resolved discriminator to
                // the subtype. Most intents compute getType() and have no setter.
                if ("type".equals(propertyName) && beanOrClass instanceof TxIntent) {
                    parser.skipChildren();
                    return true;
                }
                return false;
            }
        });
        return mapper;
    }

    /** Stable compiler failure returned as a path-specific flow diagnostic. */
    static final class MaterializationException extends Exception {
        private final String code;
        private final String documentPath;

        private MaterializationException(String code, String message, String documentPath) {
            super(message);
            this.code = code;
            this.documentPath = documentPath;
        }

        String code() {
            return code;
        }

        String documentPath() {
            return documentPath;
        }
    }
}
