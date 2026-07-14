package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.PersistedBinding;
import com.bloxbean.cardano.client.txflow.store.SignedPayloadVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Produces the canonical identity of a semantically prepared execution request.
 *
 * <p>The representation is an explicitly ordered JSON document rather than a concatenation of
 * collection {@code toString()} values. Lengths and escaping are therefore unambiguous even when
 * resource identities, parameter names, or secure references contain punctuation. Integral Java
 * wrapper types are normalized to {@code long}, because TxFlow's portable INTEGER type assigns
 * them the same meaning.</p>
 *
 * <p>Sensitive values are represented only by the fingerprint already present in their
 * {@link PersistedBinding}; plaintext secret values never enter this document.</p>
 */
final class ExecutionRequestFingerprinter {
    private static final String FORMAT = "ccl.txflow.execution-request";
    private static final int VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    private ExecutionRequestFingerprinter() {
    }

    static String fingerprint(String compiledFingerprint, Set<String> spendingResources,
                              boolean concurrentSpending,
                              List<PersistedBinding> persistedBindings,
                              Map<String, String> secureBindingReferences) {
        Objects.requireNonNull(compiledFingerprint, "compiledFingerprint");
        Objects.requireNonNull(spendingResources, "spendingResources");
        Objects.requireNonNull(persistedBindings, "persistedBindings");
        Objects.requireNonNull(secureBindingReferences, "secureBindingReferences");

        ObjectNode document = JSON.createObjectNode();
        document.put("format", FORMAT);
        document.put("version", VERSION);
        document.put("compiled_fingerprint", compiledFingerprint);

        ArrayNode resources = JSON.createArrayNode();
        new TreeSet<>(spendingResources).forEach(resources::add);
        document.set("spending_resources", resources);
        document.put("concurrent_spending", concurrentSpending);

        ArrayNode bindings = JSON.createArrayNode();
        List<PersistedBinding> orderedBindings = new ArrayList<>(persistedBindings);
        orderedBindings.sort(Comparator.comparing(PersistedBinding::parameterName));
        orderedBindings.forEach(binding -> bindings.add(encodeBinding(binding)));
        document.set("bindings", bindings);

        ObjectNode secureReferences = JSON.createObjectNode();
        new TreeMap<>(secureBindingReferences).forEach(secureReferences::put);
        document.set("secure_binding_references", secureReferences);

        try {
            return SignedPayloadVerifier.sha256(JSON.writeValueAsBytes(document));
        } catch (JsonProcessingException impossibleForClosedScalars) {
            throw new IllegalStateException(
                    "Could not encode the canonical execution-request fingerprint",
                    impossibleForClosedScalars);
        }
    }

    private static ObjectNode encodeBinding(PersistedBinding binding) {
        Objects.requireNonNull(binding, "persistedBinding");
        ObjectNode encoded = JSON.createObjectNode();
        encoded.put("parameter_name", binding.parameterName());
        encoded.put("parameter_type", binding.parameterType());
        Object value = binding.nonSensitiveValue();
        if (value == null) {
            encoded.putNull("non_sensitive_value");
        } else if (value instanceof String text) {
            encoded.put("non_sensitive_value", text);
        } else if (value instanceof Boolean flag) {
            encoded.put("non_sensitive_value", flag);
        } else if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            encoded.put("non_sensitive_value", ((Number) value).longValue());
        } else {
            throw new IllegalArgumentException(
                    "Persisted bindings must contain portable scalar values");
        }
        if (binding.secureValueRef() == null) encoded.putNull("secure_value_ref");
        else encoded.put("secure_value_ref", binding.secureValueRef());
        encoded.put("value_fingerprint", binding.valueFingerprint());
        return encoded;
    }
}
