package com.bloxbean.cardano.client.txflow.internal;

import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowDiagnostic;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The QuickTx extensions an operator registered with a TxFlow engine, executor, compiler or codec
 * (ADR 0008 D2). This is the only extension state TxFlow consults: TxFlow never names a concrete
 * extension, intent class or operation, so any registered extension works without TxFlow changes.
 *
 * <p>Immutable. Plans are copied and snapshotted through one id-bound {@link TxPlanCodec} read
 * literally, so a value is never bound twice (ADR 0008 D4a).</p>
 *
 * <p><b>Internal and experimental:</b> follows the experimental QuickTx extension SPI.</p>
 */
public final class FlowExtensions {
    /** No registered extension: extension plans are reported as unregistered. */
    public static final FlowExtensions NONE = new FlowExtensions(Map.of());

    private final Map<String, QuickTxExtension> extensions;
    private final TxPlanCodec codec;

    private FlowExtensions(Map<String, QuickTxExtension> extensions) {
        this.extensions = extensions;
        TxPlanCodec.Builder builder = TxPlanCodec.builder();
        extensions.values().forEach(builder::withExtension);
        this.codec = builder.build();
    }

    /**
     * Freezes a set of registrations, keyed by extension id.
     *
     * @param registered extensions; the same instance twice is accepted, two instances with one id are not
     * @return the registrations
     */
    public static FlowExtensions of(Collection<? extends QuickTxExtension> registered) {
        Objects.requireNonNull(registered, "extensions");
        Map<String, QuickTxExtension> byId = new LinkedHashMap<>();
        registered.forEach(extension -> register(byId, extension));
        return byId.isEmpty() ? NONE : new FlowExtensions(byId);
    }

    /**
     * Returns these registrations plus one more extension.
     *
     * @param extension extension to add
     * @return a new registration set
     */
    public FlowExtensions with(QuickTxExtension extension) {
        Map<String, QuickTxExtension> byId = new LinkedHashMap<>(extensions);
        register(byId, extension);
        return new FlowExtensions(byId);
    }

    private static void register(Map<String, QuickTxExtension> byId, QuickTxExtension extension) {
        Objects.requireNonNull(extension, "extension");
        if (extension.id() == null || extension.id().isBlank())
            throw new IllegalArgumentException("extension id is required");
        QuickTxExtension existing = byId.putIfAbsent(extension.id(), extension);
        if (existing != null && existing != extension)
            throw new IllegalArgumentException("Extension already registered: " + extension.id());
    }

    /** @return the registered extensions in registration order */
    public Collection<QuickTxExtension> extensions() {
        return List.copyOf(extensions.values());
    }

    /** @return whether no extension is registered */
    public boolean isEmpty() {
        return extensions.isEmpty();
    }

    /**
     * Registers every extension on a builder TxFlow created for an extension plan.
     *
     * @param builder builder to configure
     * @return the same builder
     */
    public QuickTxBuilder applyTo(QuickTxBuilder builder) {
        extensions.values().forEach(builder::withExtension);
        return builder;
    }

    /**
     * Writes an extension plan with its bindings and namespaced intents.
     *
     * @param plan plan to write
     * @return extension-aware TxPlan YAML
     */
    public String write(TxPlan plan) {
        return codec.toYaml(plan);
    }

    /**
     * Reads a plan written by {@link #write(TxPlan)} without expanding any placeholder.
     *
     * @param yaml extension-aware TxPlan YAML with final values
     * @return the reconstructed plan
     */
    public TxPlan readLiteral(String yaml) {
        return codec.readLiteral(yaml);
    }

    /**
     * Deep-copies an extension plan literally.
     *
     * @param plan plan to copy
     * @return an independent copy that keeps every value and variable as authored
     */
    public TxPlan copy(TxPlan plan) {
        return codec.copy(plan);
    }

    /**
     * Checks everything a flow needs from the registered extensions before any step is built:
     * consistent and used bindings, registered extensions, accepted metadata and valid extension
     * intents. Returns no diagnostic for a flow that uses no extension.
     *
     * @param flow flow to check
     * @return error diagnostics, empty when the flow can run with these registrations
     */
    public List<FlowDiagnostic> check(TxFlow flow) {
        List<FlowDiagnostic> diagnostics = new ArrayList<>();
        Map<String, Binding> bindings = mergeBindings(flow, diagnostics);
        reportUnusedBindings(flow, bindings, diagnostics);
        for (Binding binding : bindings.values()) {
            if (binding.unused) continue;
            QuickTxExtension extension = extensions.get(binding.metadata.getExtension());
            if (extension == null) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_UNREGISTERED",
                        "Extension '" + binding.metadata.getExtension() + "' bound to namespace '"
                                + binding.namespace + "' is not registered with this TxFlow runtime",
                        binding.path));
                continue;
            }
            // The schema version is checked here as the codec checks it, independently of the
            // extension's overridable validateMetadata hook.
            if (!extension.schemaVersion().equals(binding.metadata.getSchemaVersion())) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_METADATA_INVALID",
                        "Namespace '" + binding.namespace + "': unsupported schema_version '"
                                + binding.metadata.getSchemaVersion() + "' for extension " + extension.id(),
                        binding.path));
                continue;
            }
            try {
                extension.validateMetadata(binding.metadata);
            } catch (RuntimeException e) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_METADATA_INVALID",
                        "Namespace '" + binding.namespace + "': " + e.getMessage(), binding.path));
            }
        }
        for (int index = 0; index < flow.getSteps().size(); index++) {
            FlowStep step = flow.getSteps().get(index);
            String path = stepPath(index) + ".transaction";
            if (step.hasTransactionTemplate()) {
                checkTemplateIntents(flow, step, path, diagnostics);
            }
            if (step.hasTxPlan() && step.getTxPlan().hasExtensions()) {
                checkIntents(step.getTxPlan(), path, diagnostics);
            }
        }
        return List.copyOf(diagnostics);
    }

    /**
     * Checks only what needs no registration: binding conflicts and unused bindings. Parsers use
     * this, since a document can be read before any runtime exists.
     *
     * @param flow flow to check
     * @return error diagnostics
     */
    public static List<FlowDiagnostic> checkBindings(TxFlow flow) {
        List<FlowDiagnostic> diagnostics = new ArrayList<>();
        reportUnusedBindings(flow, mergeBindings(flow, diagnostics), diagnostics);
        return List.copyOf(diagnostics);
    }

    /**
     * Whether two binding maps can share one flow: merging them binds no namespace to different
     * metadata and no extension under two namespaces, so it raises no
     * {@code TXFLOW_EXTENSION_BINDING_CONFLICT}. Planners that put several items in one flow use it.
     *
     * @param left namespace to metadata
     * @param right namespace to metadata
     * @return whether the maps merge without conflict
     */
    public static boolean mergeable(Map<String, ExtensionMetadata> left, Map<String, ExtensionMetadata> right) {
        List<FlowDiagnostic> diagnostics = new ArrayList<>();
        Map<String, Binding> merged = new LinkedHashMap<>();
        left.forEach((namespace, metadata) -> merge(merged, new Binding(namespace, metadata, ""), diagnostics));
        right.forEach((namespace, metadata) -> merge(merged, new Binding(namespace, metadata, ""), diagnostics));
        return diagnostics.isEmpty();
    }

    /**
     * The canonical flow-level binding map: the flow's own bindings merged with every step plan's
     * bindings, sorted by namespace. Callers check conflicts first with {@link #check(TxFlow)}.
     *
     * @param flow flow whose bindings to merge
     * @return namespace to metadata, empty for an extension-free flow
     */
    public static Map<String, ExtensionMetadata> bindings(TxFlow flow) {
        Map<String, ExtensionMetadata> result = new java.util.TreeMap<>();
        mergeBindings(flow, new ArrayList<>()).forEach((namespace, binding) ->
                result.put(namespace, binding.metadata));
        return result;
    }

    private void checkIntents(TxPlan plan, String path, List<FlowDiagnostic> diagnostics) {
        List<AbstractTx<?>> transactions = plan.getTxs();
        for (int t = 0; t < transactions.size(); t++) {
            List<TxIntent> intents = transactions.get(t).getIntentions();
            for (int i = 0; i < intents.size(); i++) {
                if (!(intents.get(i) instanceof ExtensionIntent)) continue;
                ExtensionIntent intent = (ExtensionIntent) intents.get(i);
                String intentPath = path + ".tx" + (t == 0 ? "" : "[" + t + "]") + ".intents[" + i + "]";
                String type = intent.getExtensionId() + ":" + intent.getOperation();
                if (namespaceOf(plan, intent.getExtensionId()) == null) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_INTENT_INVALID",
                            "Intent " + type + " has no namespace binding in its plan; bind the "
                                    + "extension to the plan (for example extension.configure(plan))",
                            intentPath));
                    continue;
                }
                QuickTxExtension extension = extensions.get(intent.getExtensionId());
                if (extension == null) continue; // reported once for its binding
                Class<? extends ExtensionIntent> expected = extension.intentTypes().get(intent.getOperation());
                if (expected == null || !extension.operations().contains(intent.getOperation())) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_INTENT_INVALID",
                            "Extension " + extension.id() + " has no operation '" + intent.getOperation() + "'",
                            intentPath));
                } else if (!expected.isInstance(intent)) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_INTENT_INVALID",
                            "Intent " + type + " must use " + expected.getName() + " but got "
                                    + intent.getClass().getName(), intentPath));
                } else {
                    try {
                        intent.validate();
                    } catch (RuntimeException e) {
                        diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_INTENT_INVALID",
                                "Intent " + type + " is invalid: " + e.getMessage(), intentPath));
                    }
                }
            }
        }
        // Writing needs valid, registered bindings and intents, so it runs once nothing else failed.
        if (diagnostics.isEmpty()) checkWrittenIntents(plan, path, diagnostics);
    }

    /**
     * D8 for Java plans: an extension intent holds no {@code flow_output} pointer, checked on the
     * form the canonical writer emits, exactly as a portable template is checked.
     */
    private void checkWrittenIntents(TxPlan plan, String path, List<FlowDiagnostic> diagnostics) {
        JsonNode written;
        try {
            written = YamlSerializer.getYamlMapper().readTree(write(plan)).path("transaction");
        } catch (Exception e) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_INTENT_INVALID",
                    "The plan's extension intents cannot be written: " + e.getMessage(), path));
            return;
        }
        List<AbstractTx<?>> transactions = plan.getTxs();
        for (int t = 0; t < transactions.size(); t++) {
            List<JsonNode> writtenExtensionIntents = new ArrayList<>();
            for (JsonNode content : written.path(t)) {
                content.path("intents").forEach(intent -> {
                    if (isNamespaced(intent)) writtenExtensionIntents.add(intent);
                });
            }
            List<TxIntent> intents = transactions.get(t).getIntentions();
            int n = 0;
            for (int i = 0; i < intents.size() && n < writtenExtensionIntents.size(); i++) {
                if (!(intents.get(i) instanceof ExtensionIntent)) continue;
                if (!writtenExtensionIntents.get(n++).findValues("flow_output").isEmpty()) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_FLOW_OUTPUT_UNSUPPORTED",
                            "flow_output references are not supported inside extension intents",
                            path + ".tx" + (t == 0 ? "" : "[" + t + "]") + ".intents[" + i + "]"));
                }
            }
        }
    }

    /**
     * Checks what a portable template can be checked for before binding: each
     * {@code <namespace>:<operation>} intent sits in {@code tx.intents}, names a declared
     * namespace and a known operation, and holds no {@code flow_output} pointer (ADR 0008 D6, D8).
     * Decoding and the round-trip guard run in the compiler on the bound tree.
     */
    private void checkTemplateIntents(TxFlow flow, FlowStep step, String path,
                                      List<FlowDiagnostic> diagnostics) {
        // A value that is not an array is left to the core template checks, which report it.
        JsonNode tx = step.getTransactionTemplate().toJsonNode().path("tx");
        for (String array : List.of("inputs", "scripts")) {
            JsonNode entries = tx.path(array);
            for (int i = 0; entries.isArray() && i < entries.size(); i++) {
                if (isNamespaced(entries.get(i))) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_INTENT_INVALID",
                            "Extension intents are allowed only in tx.intents",
                            path + ".tx." + array + "[" + i + "]"));
                }
            }
        }
        JsonNode intents = tx.path("intents");
        for (int i = 0; intents.isArray() && i < intents.size(); i++) {
            JsonNode intent = intents.get(i);
            if (!isNamespaced(intent)) continue;
            String intentPath = path + ".tx.intents[" + i + "]";
            String type = intent.path("type").asText();
            String namespace = type.substring(0, type.indexOf(':'));
            String operation = type.substring(type.indexOf(':') + 1);
            ExtensionMetadata metadata = flow.getExtensions().get(namespace);
            if (metadata == null) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_INTENT_INVALID",
                        "Intent " + type + " uses undeclared extension namespace '" + namespace
                                + "'; declare it in spec.extensions", intentPath));
                continue;
            }
            if (!intent.findValues("flow_output").isEmpty()) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_FLOW_OUTPUT_UNSUPPORTED",
                        "flow_output references are not supported inside extension intents",
                        intentPath));
            }
            QuickTxExtension extension = extensions.get(metadata.getExtension());
            if (extension != null && (!extension.operations().contains(operation)
                    || !extension.intentTypes().containsKey(operation))) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_INTENT_INVALID",
                        "Extension " + extension.id() + " has no operation '" + operation + "'",
                        intentPath));
            }
        }
    }

    private static boolean isNamespaced(JsonNode intent) {
        return intent.path("type").asText("").indexOf(':') > 0;
    }

    /** Flow-level bindings first, then each plan's, reporting namespace and id conflicts. */
    private static Map<String, Binding> mergeBindings(TxFlow flow, List<FlowDiagnostic> diagnostics) {
        Map<String, Binding> merged = new LinkedHashMap<>();
        flow.getExtensions().forEach((namespace, metadata) ->
                merge(merged, new Binding(namespace, metadata, "$.spec.extensions." + namespace), diagnostics));
        for (int index = 0; index < flow.getSteps().size(); index++) {
            FlowStep step = flow.getSteps().get(index);
            if (!step.hasTxPlan()) continue;
            String path = stepPath(index) + ".transaction";
            step.getTxPlan().getExtensions().forEach((namespace, metadata) ->
                    merge(merged, new Binding(namespace, metadata, path), diagnostics));
        }
        return merged;
    }

    private static void merge(Map<String, Binding> merged, Binding binding, List<FlowDiagnostic> diagnostics) {
        Binding existing = merged.get(binding.namespace);
        if (existing != null) {
            if (!existing.metadata.equals(binding.metadata)) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_BINDING_CONFLICT",
                        "Namespace '" + binding.namespace + "' is bound to different extension metadata",
                        binding.path));
            }
            return;
        }
        for (Binding other : merged.values()) {
            if (other.metadata.getExtension().equals(binding.metadata.getExtension())) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_BINDING_CONFLICT",
                        "Extension '" + binding.metadata.getExtension() + "' is bound to namespaces '"
                                + other.namespace + "' and '" + binding.namespace + "'",
                        binding.path));
                return;
            }
        }
        merged.put(binding.namespace, binding);
    }

    /** A binding is used when some step holds an extension intent through its namespace. */
    private static void reportUnusedBindings(TxFlow flow, Map<String, Binding> bindings,
                                             List<FlowDiagnostic> diagnostics) {
        Set<String> used = new LinkedHashSet<>();
        for (FlowStep step : flow.getSteps()) {
            if (step.hasTransactionTemplate()) used.addAll(templateNamespaces(step));
            if (!step.hasTxPlan()) continue;
            TxPlan plan = step.getTxPlan();
            for (AbstractTx<?> transaction : plan.getTxs()) {
                for (TxIntent intent : transaction.getIntentions()) {
                    if (!(intent instanceof ExtensionIntent)) continue;
                    String namespace = namespaceOf(plan, ((ExtensionIntent) intent).getExtensionId());
                    if (namespace != null) used.add(namespace);
                }
            }
        }
        for (Binding binding : bindings.values()) {
            if (used.contains(binding.namespace)) continue;
            binding.unused = true;
            diagnostics.add(FlowDiagnostic.error("TXFLOW_EXTENSION_BINDING_UNUSED",
                    "Extension namespace '" + binding.namespace + "' is declared but no extension "
                            + "intent uses it", binding.path));
        }
    }

    private static String namespaceOf(TxPlan plan, String extensionId) {
        return plan.getExtensions().entrySet().stream()
                .filter(entry -> extensionId.equals(entry.getValue().getExtension()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    /** Namespaces of {@code <namespace>:<operation>} intents in a portable template's tx.intents. */
    public static Set<String> templateNamespaces(FlowStep step) {
        Set<String> namespaces = new LinkedHashSet<>();
        for (JsonNode intent : step.getTransactionTemplate().toJsonNode().path("tx").path("intents")) {
            if (isNamespaced(intent)) {
                String type = intent.path("type").asText();
                namespaces.add(type.substring(0, type.indexOf(':')));
            }
        }
        return namespaces;
    }

    private static String stepPath(int index) {
        return "$.spec.steps[" + index + "]";
    }

    private static final class Binding {
        private final String namespace;
        private final ExtensionMetadata metadata;
        private final String path;
        private boolean unused;

        private Binding(String namespace, ExtensionMetadata metadata, String path) {
            this.namespace = namespace;
            this.metadata = metadata;
            this.path = path;
        }
    }
}
