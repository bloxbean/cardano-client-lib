package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Instance-scoped TxPlan codec for qualified extension intents.
 * Core intent serialization remains backward compatible through {@link TxPlan#toYaml()}.
 */
public final class TxPlanCodec {
    private final Map<String, QuickTxExtension> extensions;

    private TxPlanCodec(Map<String, QuickTxExtension> extensions) {
        this.extensions = Map.copyOf(extensions);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String toYaml(TxPlan plan) {
        try {
            ObjectNode root = (ObjectNode) YamlSerializer.getYamlMapper().readTree(plan.toYaml());
            Map<String, ExtensionMetadata> declared = plan.getExtensions();
            validateBindings(declared);
            transformForWrite(root, declared);
            return YamlSerializer.getYamlMapper().writeValueAsString(root);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize extension-aware TxPlan", e);
        }
    }

    public TxPlan fromYaml(String yaml) {
        try {
            JsonNode unresolved = YamlSerializer.getYamlMapper().readTree(yaml);
            JsonNode variablesNode = unresolved.get("variables");
            if (variablesNode != null && variablesNode.isObject()) {
                Map<String, Object> variables = YamlSerializer.getYamlMapper()
                        .convertValue(variablesNode, Map.class);
                yaml = VariableResolver.resolve(yaml, variables);
            }
            ObjectNode root = (ObjectNode) YamlSerializer.getYamlMapper().readTree(yaml);
            Map<String, ExtensionMetadata> declared = readMetadata(root);
            validateBindings(declared);
            transformForRead(root, declared);
            return TxPlan.from(YamlSerializer.getYamlMapper().writeValueAsString(root));
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
            ExtensionMetadata metadata = YamlSerializer.getYamlMapper()
                    .convertValue(entry.getValue(), ExtensionMetadata.class);
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
            if (!runtime.operations().contains(operation))
                throw new IllegalArgumentException("Unsupported operation '" + operation
                        + "' for extension " + runtime.id());

            ObjectNode payload = YamlSerializer.getYamlMapper().createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = intent.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!"type".equals(field.getKey())) payload.set(field.getKey(), field.getValue());
            }
            intent.removeAll();
            intent.put("type", ExtensionIntent.TYPE);
            intent.put("extension_id", metadata.getExtension());
            intent.put("operation", operation);
            intent.set("payload", payload);
        });
    }

    private void transformForWrite(ObjectNode root, Map<String, ExtensionMetadata> declared) {
        forEachIntent(root, intent -> {
            if (!ExtensionIntent.TYPE.equals(intent.path("type").asText())) return;
            String extensionId = intent.path("extension_id").asText();
            String operation = intent.path("operation").asText();
            String alias = declared.entrySet().stream()
                    .filter(e -> extensionId.equals(e.getValue().getExtension()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No document namespace declares extension " + extensionId));
            QuickTxExtension runtime = extensions.get(alias);
            if (!runtime.operations().contains(operation))
                throw new IllegalArgumentException("Unsupported operation '" + operation
                        + "' for extension " + runtime.id());
            JsonNode payload = intent.get("payload");
            intent.removeAll();
            intent.put("type", alias + ":" + operation);
            if (payload != null && payload.isObject()) {
                payload.fields().forEachRemaining(field -> intent.set(field.getKey(), field.getValue()));
            }
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
