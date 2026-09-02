package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Instance-scoped TxPlan codec for typed, qualified extension intents.
 * Core intent serialization remains backward compatible through {@link TxPlan#toYaml()}.
 */
public final class TxPlanCodec {
    private final Map<String, QuickTxExtension> extensions;
    private final ObjectMapper mapper;

    private TxPlanCodec(Map<String, QuickTxExtension> extensions) {
        this.extensions = Map.copyOf(extensions);
        this.mapper = YamlSerializer.getYamlMapper().copy();

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

        this.extensions.values().forEach(extension -> {
            validateIntentTypes(extension);
            extension.intentTypes().forEach((operation, type) -> mapper.registerSubtypes(
                    new NamedType(type, ExtensionIntent.canonicalType(extension.id(), operation))));
        });
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
        try {
            JsonNode unresolved = mapper.readTree(yaml);
            JsonNode variablesNode = unresolved.get("variables");
            if (variablesNode != null && variablesNode.isObject()) {
                Map<String, Object> variables = mapper.convertValue(variablesNode, Map.class);
                yaml = VariableResolver.resolve(yaml, variables);
            }
            ObjectNode root = (ObjectNode) mapper.readTree(yaml);
            Map<String, ExtensionMetadata> declared = readMetadata(root);
            validateBindings(declared);
            transformForRead(root, declared);
            TransactionDocument document = mapper.treeToValue(root, TransactionDocument.class);
            TxPlan plan = TxPlan.from(document);
            validatePlanIntents(plan, declared);
            return plan;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize extension-aware TxPlan", e);
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
            QuickTxExtension runtime = extensions.get(entry.getKey());
            ExtensionMetadata metadata = entry.getValue();
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
            QuickTxExtension runtime = extensions.get(alias);
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
            requireIntentType(extensions.get(binding.getKey()), operation);
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
                    QuickTxExtension runtime = extensions.get(binding.getKey());
                    Class<? extends ExtensionIntent> expected = requireIntentType(
                            runtime, intent.getOperation());
                    if (!expected.isInstance(intent))
                        throw new IllegalArgumentException("Operation " + intent.getExtensionId()
                                + ":" + intent.getOperation() + " must use " + expected.getName()
                                + " but got " + intent.getClass().getName());
                    intent.validate();
                }));
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

        public Builder withExtension(String namespace, QuickTxExtension extension) {
            if (namespace == null || !namespace.matches("[a-z][a-z0-9_-]{0,31}"))
                throw new IllegalArgumentException("Invalid extension namespace: " + namespace);
            rejectReserved(namespace);
            if (extension == null) throw new IllegalArgumentException("extension is required");
            if (extensions.putIfAbsent(namespace, extension) != null)
                throw new IllegalArgumentException("Duplicate extension namespace: " + namespace);
            return this;
        }

        public TxPlanCodec build() {
            return new TxPlanCodec(extensions);
        }
    }

    private static void rejectReserved(String namespace) {
        if (java.util.Set.of("tx", "core", "context", "transaction", "variables", "extensions")
                .contains(namespace))
            throw new IllegalArgumentException("Reserved extension namespace: " + namespace);
    }
}
