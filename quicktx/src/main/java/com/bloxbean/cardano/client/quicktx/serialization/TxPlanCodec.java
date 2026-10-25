package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Instance-scoped TxPlan codec for typed, qualified extension intents.
 * Core intent serialization remains backward compatible through {@link TxPlan#toYaml()}.
 *
 * <p><b>Experimental:</b> part of the QuickTx extension SPI and may change in a minor release.</p>
 */
public final class TxPlanCodec {
    /** Extensions bound to one explicit document namespace. */
    private final Map<String, QuickTxExtension> extensions;
    /** Extensions bound by id: any namespace whose metadata names that id resolves to them. */
    private final Map<String, QuickTxExtension> extensionsById;
    private final ObjectMapper mapper;
    private final boolean strictIntentFields;

    private TxPlanCodec(Map<String, QuickTxExtension> extensions,
                        Map<String, QuickTxExtension> extensionsById,
                        boolean strictIntentFields) {
        this.extensions = Map.copyOf(extensions);
        this.extensionsById = Map.copyOf(extensionsById);
        this.strictIntentFields = strictIntentFields;
        this.mapper = YamlSerializer.getYamlMapper().copy();
        // Quote strings that look like numbers so a written plan reads back with the same value
        // types, e.g. deployment network "0" stays a String. Only this codec's mapper copy changes.
        ((YAMLFactory) mapper.getFactory()).enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS);

        SimpleModule plutusDataModule = new SimpleModule();
        plutusDataModule.addDeserializer(PlutusData.class,
                new com.fasterxml.jackson.databind.JsonDeserializer<>() {
                    @Override
                    public PlutusData deserialize(JsonParser parser,
                                                  com.fasterxml.jackson.databind.DeserializationContext context)
                            throws java.io.IOException {
                        try {
                            return PlutusDataYamlUtil.fromYamlNode(parser.readValueAsTree(), Map.of());
                        } catch (Exception e) {
                            throw com.fasterxml.jackson.databind.JsonMappingException.from(
                                    parser, "Invalid structured Plutus data", e);
                        }
                    }
                });
        mapper.registerModule(plutusDataModule);

        Set<QuickTxExtension> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.addAll(this.extensions.values());
        distinct.addAll(this.extensionsById.values());
        distinct.forEach(extension -> {
            validateIntentTypes(extension);
            extension.intentTypes().forEach((operation, type) -> mapper.registerSubtypes(
                    new NamedType(type, ExtensionIntent.canonicalType(extension.id(), operation))));
        });
        if (strictIntentFields) StrictReading.apply(mapper);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String toYaml(TxPlan plan) {
        try {
            Map<String, ExtensionMetadata> declared = plan.getExtensions();
            validateBindings(declared);
            validatePlanIntents(plan, declared);
            ObjectNode root = mapper.valueToTree(plan.toDocument());
            transformForWrite(root, declared);
            return mapper.writeValueAsString(root);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize extension-aware TxPlan", e);
        }
    }

    public TxPlan fromYaml(String yaml) {
        return fromYaml(yaml, Map.of());
    }

    /**
     * Deserialize an extension-aware plan with values supplied by the execution environment.
     * Runtime values override document defaults and are retained in the reconstructed plan.
     *
     * <p>Variables are resolved structurally on the parsed tree, never by rewriting the YAML
     * text: an exact {@code ${name}} keeps the variable's native type, an embedded placeholder is
     * interpolated as text, and a replacement value is never re-read as a template.</p>
     *
     * @param yaml plan YAML containing {@code ${variable}} references
     * @param runtimeVariables values supplied by the caller for this execution
     * @return the reconstructed, variable-resolved plan
     */
    public TxPlan fromYaml(String yaml, Map<String, Object> runtimeVariables) {
        if (runtimeVariables == null)
            throw new IllegalArgumentException("runtimeVariables is required");
        return read(yaml, runtimeVariables, true);
    }

    /**
     * Deserialize an already-bound plan literally: no {@code ${...}} placeholder is expanded.
     *
     * <p>Use this for a document whose values were bound before it was written, for example a
     * snapshot of an in-memory plan. Expanding it again would reinterpret literal text that only
     * looks like a placeholder. Any {@code variables} block is kept on the plan as inert data.</p>
     *
     * @param yaml extension-aware plan YAML whose values are final
     * @return the reconstructed plan
     */
    public TxPlan readLiteral(String yaml) {
        return read(yaml, Map.of(), false);
    }

    /**
     * Deep-copy a plan through this codec without re-binding any value.
     * Equivalent to {@code readLiteral(toYaml(plan))}: the copy shares no intent instance with
     * the source and keeps the source's variables as inert data.
     *
     * @param plan plan to copy
     * @return an independent, literally equal plan
     */
    public TxPlan copy(TxPlan plan) {
        if (plan == null) throw new IllegalArgumentException("plan is required");
        return readLiteral(toYaml(plan));
    }

    private TxPlan read(String yaml, Map<String, Object> runtimeVariables, boolean resolve) {
        try {
            if (strictIntentFields) StrictReading.checkYamlText(yaml);
            JsonNode unresolved = mapper.readTree(yaml);
            if (!(unresolved instanceof ObjectNode))
                throw new IllegalArgumentException("TxPlan YAML root must be a mapping");

            JsonNode variablesNode = unresolved.get("variables");
            if (variablesNode != null && !variablesNode.isObject())
                throw new IllegalArgumentException("variables must be a mapping");

            if (strictIntentFields) StrictReading.checkVariables(variablesNode);
            Map<String, Object> variables = new LinkedHashMap<>();
            if (variablesNode != null)
                variables.putAll(mapper.convertValue(variablesNode,
                        new TypeReference<Map<String, Object>>() { }));
            variables.putAll(runtimeVariables);

            ObjectNode root = mapper.createObjectNode();
            unresolved.fields().forEachRemaining(entry -> {
                if (!"variables".equals(entry.getKey()))
                    root.set(entry.getKey(), resolve
                            ? VariableResolver.resolveTree(entry.getValue(), variables, entry.getKey())
                            : entry.getValue());
            });

            Map<String, ExtensionMetadata> declared = readMetadata(root);
            validateBindings(declared);
            transformForRead(root, declared);
            // The tree is final (resolved, or literal for readLiteral), so the core reconstruction gets a document without
            // variables: it decodes Plutus data exactly as for a literal plan and has nothing to
            // substitute, so a replacement value is never read as a template a second time.
            if (strictIntentFields) StrictReading.checkDocument(root);
            TransactionDocument document = mapper.treeToValue(root, TransactionDocument.class);
            TxPlan plan = reconstruct(document);
            plan.setVariables(variables);
            validatePlanIntents(plan, declared);
            return plan;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize extension-aware TxPlan: "
                    + YamlSerializer.rootMessage(e), e);
        }
    }

    /**
     * After structural resolution every remaining {@code ${...}} came from a replacement value.
     * The core reconstruction keeps it literal, and when a field cannot hold it the error says
     * so instead of reporting a missing variable.
     */
    private static TxPlan reconstruct(TransactionDocument document) {
        try {
            return TxPlan.from(document);
        } catch (RuntimeException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t.getMessage() != null && t.getMessage().contains("Variable not found"))
                    throw new IllegalArgumentException("A resolved value still contains a ${...}"
                            + " placeholder; it is kept literal and this field cannot hold it: "
                            + e.getMessage(), e);
            }
            throw e;
        }
    }

    private Map<String, ExtensionMetadata> readMetadata(ObjectNode root) {
        JsonNode node = root.get("extensions");
        Map<String, ExtensionMetadata> result = new LinkedHashMap<>();
        if (node == null || node.isNull()) return result;
        if (!node.isObject()) throw new IllegalArgumentException("extensions must be a mapping");
        node.fields().forEachRemaining(entry -> {
            String alias = entry.getKey();
            if (!alias.matches("[a-z][a-z0-9_-]{0,31}"))
                throw new IllegalArgumentException("Invalid extension namespace: " + alias);
            rejectReserved(alias);
            ExtensionMetadata metadata = mapper.convertValue(entry.getValue(), ExtensionMetadata.class);
            if (result.putIfAbsent(alias, metadata) != null)
                throw new IllegalArgumentException("Duplicate extension namespace: " + alias);
        });
        return result;
    }

    private void validateBindings(Map<String, ExtensionMetadata> declared) {
        for (Map.Entry<String, ExtensionMetadata> entry : declared.entrySet()) {
            ExtensionMetadata metadata = entry.getValue();
            QuickTxExtension runtime = runtime(entry.getKey(), metadata);
            if (runtime == null)
                throw new IllegalArgumentException("No codec extension registered for namespace '"
                        + entry.getKey() + "'");
            if (!runtime.id().equals(metadata.getExtension()))
                throw new IllegalArgumentException("Namespace '" + entry.getKey() + "' declares extension '"
                        + metadata.getExtension() + "' but runtime is '" + runtime.id() + "'");
            if (!runtime.schemaVersion().equals(metadata.getSchemaVersion()))
                throw new IllegalArgumentException("Unsupported schema_version '"
                        + metadata.getSchemaVersion() + "' for extension " + runtime.id());
            runtime.validateMetadata(metadata);
        }
    }

    private void transformForRead(ObjectNode root, Map<String, ExtensionMetadata> declared) {
        forEachIntent(root, intent -> {
            JsonNode typeNode = intent.get("type");
            if (typeNode == null || !typeNode.isTextual()) return;
            String type = typeNode.asText();
            int separator = type.indexOf(':');
            if (separator < 0) return;
            String alias = type.substring(0, separator);
            String operation = type.substring(separator + 1);
            ExtensionMetadata metadata = declared.get(alias);
            if (metadata == null)
                throw new IllegalArgumentException("Intent uses undeclared extension namespace '" + alias + "'");
            QuickTxExtension runtime = runtime(alias, metadata);
            requireIntentType(runtime, operation);
            intent.put("type", ExtensionIntent.canonicalType(metadata.getExtension(), operation));
        });
    }

    private void transformForWrite(ObjectNode root, Map<String, ExtensionMetadata> declared) {
        forEachIntent(root, intent -> {
            String canonicalType = intent.path("type").asText();
            Map.Entry<String, ExtensionMetadata> binding = declared.entrySet().stream()
                    .filter(entry -> canonicalType.startsWith(entry.getValue().getExtension() + ":"))
                    .findFirst().orElse(null);
            if (binding == null) return;
            String operation = canonicalType.substring(
                    binding.getValue().getExtension().length() + 1);
            requireIntentType(runtime(binding.getKey(), binding.getValue()), operation);
            intent.put("type", binding.getKey() + ":" + operation);
        });
    }

    private void validatePlanIntents(TxPlan plan, Map<String, ExtensionMetadata> declared) {
        plan.getTxs().forEach(transaction -> transaction.getIntentions().stream()
                .filter(ExtensionIntent.class::isInstance)
                .map(ExtensionIntent.class::cast)
                .forEach(intent -> {
                    Map.Entry<String, ExtensionMetadata> binding = declared.entrySet().stream()
                            .filter(entry -> intent.getExtensionId().equals(
                                    entry.getValue().getExtension()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No document namespace declares extension "
                                            + intent.getExtensionId()));
                    QuickTxExtension runtime = runtime(binding.getKey(), binding.getValue());
                    Class<? extends ExtensionIntent> expected = requireIntentType(
                            runtime, intent.getOperation());
                    if (!expected.isInstance(intent))
                        throw new IllegalArgumentException("Operation " + intent.getExtensionId()
                                + ":" + intent.getOperation() + " must use " + expected.getName()
                                + " but got " + intent.getClass().getName());
                    intent.validate();
                }));
    }

    /** An explicit namespace binding wins; otherwise the namespace resolves by its declared id. */
    private QuickTxExtension runtime(String namespace, ExtensionMetadata metadata) {
        QuickTxExtension bound = extensions.get(namespace);
        if (bound != null || metadata == null || metadata.getExtension() == null) return bound;
        return extensionsById.get(metadata.getExtension());
    }

    private static Class<? extends ExtensionIntent> requireIntentType(
            QuickTxExtension extension, String operation) {
        Class<? extends ExtensionIntent> type = extension.intentTypes().get(operation);
        if (type == null)
            throw new IllegalArgumentException("Unsupported operation '" + operation
                    + "' for extension " + extension.id());
        return type;
    }

    private static void validateIntentTypes(QuickTxExtension extension) {
        extension.intentTypes().forEach((operation, type) -> {
            if (!extension.operations().contains(operation))
                throw new IllegalArgumentException("Intent type registered for unadvertised operation '"
                        + operation + "' in extension " + extension.id());
            if (type == null || !ExtensionIntent.class.isAssignableFrom(type))
                throw new IllegalArgumentException("Invalid intent type for " + extension.id()
                        + ":" + operation);
        });
    }

    private void forEachIntent(ObjectNode root, java.util.function.Consumer<ObjectNode> consumer) {
        JsonNode transactions = root.get("transaction");
        if (transactions == null || !transactions.isArray()) return;
        transactions.forEach(entry -> {
            JsonNode tx = entry.get("tx");
            if (tx == null) return;
            JsonNode intents = tx.get("intents");
            if (intents != null && intents.isArray()) {
                intents.forEach(intent -> {
                    if (intent.isObject()) consumer.accept((ObjectNode) intent);
                });
            }
        });
    }

    public static final class Builder {
        private final Map<String, QuickTxExtension> extensions = new LinkedHashMap<>();
        private final Map<String, QuickTxExtension> extensionsById = new LinkedHashMap<>();
        private boolean strictIntentFields = true;

        /**
         * Whether core intents are read strictly (the default). A strict codec rejects an unknown or misspelled intent
         * field, a fractional number for an integer field, a numeric enum index and a malformed UTxO reference.
         * {@code false} restores the lenient reading, which drops or coerces them. Unknown keys outside intents
         * ({@code context}, {@code tx}, the root) are rejected either way; TxFlow compilation is always strict.
         *
         * @param strictIntentFields false to read core intents leniently
         * @return this builder
         */
        public Builder strictIntentFields(boolean strictIntentFields) {
            this.strictIntentFields = strictIntentFields;
            return this;
        }

        public Builder withExtension(String namespace, QuickTxExtension extension) {
            if (namespace == null || !namespace.matches("[a-z][a-z0-9_-]{0,31}"))
                throw new IllegalArgumentException("Invalid extension namespace: " + namespace);
            rejectReserved(namespace);
            if (extension == null) throw new IllegalArgumentException("extension is required");
            if (extensions.putIfAbsent(namespace, extension) != null)
                throw new IllegalArgumentException("Duplicate extension namespace: " + namespace);
            return this;
        }

        /**
         * Bind an extension by its id rather than by one namespace. A document may then use any
         * namespace whose declared metadata names this extension; the alias stays document-local.
         * An explicit {@link #withExtension(String, QuickTxExtension)} binding takes precedence.
         *
         * @param extension runtime extension; registering the same instance twice is a no-op
         * @return this builder
         */
        public Builder withExtension(QuickTxExtension extension) {
            if (extension == null) throw new IllegalArgumentException("extension is required");
            if (extension.id() == null || extension.id().isBlank())
                throw new IllegalArgumentException("extension id is required");
            QuickTxExtension existing = extensionsById.putIfAbsent(extension.id(), extension);
            if (existing != null && existing != extension)
                throw new IllegalArgumentException("Extension already registered: " + extension.id());
            return this;
        }

        public TxPlanCodec build() {
            return new TxPlanCodec(extensions, extensionsById, strictIntentFields);
        }
    }

    private static void rejectReserved(String namespace) {
        if (java.util.Set.of("tx", "core", "context", "transaction", "variables", "extensions")
                .contains(namespace))
            throw new IllegalArgumentException("Reserved extension namespace: " + namespace);
    }
}
