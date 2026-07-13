package com.bloxbean.cardano.client.txflow.compile;

import com.bloxbean.cardano.client.quicktx.serialization.TransactionDocument;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.StepDependency;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionPolicy;
import com.bloxbean.cardano.client.txflow.codec.FlowDiagnostic;
import com.bloxbean.cardano.client.txflow.codec.FlowFormat;
import com.bloxbean.cardano.client.txflow.codec.FlowSchemaVersion;
import com.bloxbean.cardano.client.txflow.codec.FlowWriteOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.model.ParameterSpec;
import com.bloxbean.cardano.client.txflow.model.ParameterType;
import com.bloxbean.cardano.client.txflow.resource.ResourceCapability;
import com.bloxbean.cardano.client.txflow.resource.ResourceDescriptor;
import com.bloxbean.cardano.client.txflow.resource.ResourceRef;
import com.bloxbean.cardano.client.txflow.store.SignedPayloadVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Side-effect-free compiler from a reusable TxFlow definition to a bound execution
 * plan.
 *
 * <p>Compilation evaluates server policy, validates the graph and named-output
 * references, applies typed {@code ${{ inputs.name }}} bindings, preflights resource
 * references, and materializes each portable transaction as a fresh QuickTx plan.
 * Java transaction factories are deliberately not portable. Expected validation
 * failures are returned as stable {@link FlowDiagnostic} values rather than thrown.</p>
 */
public final class TxFlowCompiler {
    private static final Pattern EXACT_INPUT = Pattern.compile(
            "^\\$\\{\\{\\s*inputs\\.([A-Za-z_][A-Za-z0-9_.-]*)\\s*}}$");
    private static final Pattern INTERPOLATED_INPUT = Pattern.compile(
            "\\$\\{\\{\\s*inputs\\.([A-Za-z_][A-Za-z0-9_.-]*)\\s*}}");
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Binds, validates, and preflights a reusable definition.
     *
     * <p>The source definition and embedded transaction templates are not mutated.
     * A successful result includes a deterministic fingerprint and the runtime
     * coordination metadata derived during preflight.</p>
     *
     * @param request definition, bindings, resources, and policy to compile
     * @return compilation outcome with diagnostics
     */
    public FlowCompilationResult compile(FlowCompilationRequest request) {
        List<FlowDiagnostic> diagnostics = new ArrayList<>();
        TxFlow definition = request.definition();
        FlowExecutionPolicy.Evaluation policyEvaluation = request.policy().evaluate(definition);
        diagnostics.addAll(policyEvaluation.diagnostics());
        TxFlow.ValidationResult graph = definition.validate();
        graph.getErrors().forEach(error -> diagnostics.add(
                FlowDiagnostic.error("TXFLOW_GRAPH_INVALID", error, "$.spec.steps")));
        validateFlowOutputReferences(definition, diagnostics);

        Map<String, Object> values = validateBindings(
                definition.getParameters(), request.bindings(), diagnostics);
        if (hasErrors(diagnostics)) return new FlowCompilationResult(null, diagnostics);

        try {
            Set<String> spendingResources = new TreeSet<>();
            TxFlow.Builder compiled = TxFlow.builder(definition.getId())
                    .withDescription(definition.getDescription())
                    .withDefinitionVersion(definition.getDefinitionVersion())
                    .withNetwork(definition.getNetwork())
                    .withExecutionSettings(policyEvaluation.effectiveSettings());
            definition.getAnnotations().forEach(compiled::addAnnotation);
            definition.getParameters().values().forEach(compiled::addParameter);

            for (FlowStep step : definition.getSteps()) {
                FlowStep.Builder target = FlowStep.builder(step.getId()).withDescription(step.getDescription());
                step.getNeeds().forEach(target::needs);
                step.getOutputBindings().forEach(target::bindOutput);
                for (StepDependency dependency : step.getDependencies()) target.dependsOn(dependency);
                if (step.hasTransactionTemplate()) {
                    JsonNode bound = bindNode(step.getTransactionTemplate().toJsonNode(), values,
                            "$.spec.steps[" + step.getId() + "].transaction", diagnostics);
                    diagnostics.addAll(request.policy().evaluateTransaction(bound,
                            "$.spec.steps[" + step.getId() + "].transaction"));
                    preflightResources(bound, request, definition.getNetwork(), diagnostics,
                            spendingResources, "$.spec.steps[" + step.getId() + "].transaction");
                    if (hasErrors(diagnostics)) continue;
                    target.withTxPlan(toTxPlan(bound));
                } else if (step.hasTxPlan()) {
                    target.withTxPlan(TxPlan.from(step.getTxPlan().toYaml()));
                } else {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_NON_PORTABLE_FACTORY",
                            "Java transaction factories cannot be compiled as a portable plan",
                            "$.spec.steps[" + step.getId() + "]"));
                    continue;
                }
                if (step.hasRetryPolicy()) target.withRetryPolicy(step.getRetryPolicy());
                compiled.addStep(target.build());
            }
            if (hasErrors(diagnostics)) return new FlowCompilationResult(null, diagnostics);
            TxFlow plan = compiled.build();
            String canonical = TxFlowCodec.standard().write(plan,
                    FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1));
            return new FlowCompilationResult(new CompiledTxFlow(
                    plan, SignedPayloadVerifier.sha256(canonical), spendingResources,
                    explicitConsumers(definition)), diagnostics);
        } catch (Exception e) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_COMPILATION_FAILED", e.getMessage(), "$"));
            return new FlowCompilationResult(null, diagnostics);
        }
    }

    /**
     * Performs the same full compilation pipeline as {@link #compile(FlowCompilationRequest)}.
     * This alias is useful to callers interested primarily in diagnostics; a valid
     * result still contains the compiled plan.
     *
     * @param request compilation inputs
     * @return compilation outcome with diagnostics
     */
    public FlowCompilationResult validate(FlowCompilationRequest request) {
        return compile(request);
    }

    private boolean hasErrors(List<FlowDiagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic ->
                diagnostic.severity() == com.bloxbean.cardano.client.txflow.codec.DiagnosticSeverity.ERROR);
    }

    private void validateFlowOutputReferences(TxFlow definition,
                                              List<FlowDiagnostic> diagnostics) {
        Map<String, Integer> stepIndexes = new LinkedHashMap<>();
        for (int i = 0; i < definition.getSteps().size(); i++) {
            stepIndexes.put(definition.getSteps().get(i).getId(), i);
        }
        for (int i = 0; i < definition.getSteps().size(); i++) {
            FlowStep consumer = definition.getSteps().get(i);
            if (!consumer.hasTransactionTemplate()) continue;
            validateFlowOutputNode(consumer.getTransactionTemplate().toJsonNode(), definition,
                    stepIndexes, i, diagnostics,
                    "$.spec.steps[" + i + "].transaction");
        }
    }

    private Map<String, Set<String>> explicitConsumers(TxFlow definition) {
        Map<String, Set<String>> consumers = new LinkedHashMap<>();
        for (FlowStep consumer : definition.getSteps()) {
            if (consumer.hasTransactionTemplate()) {
                collectExplicitConsumers(consumer.getTransactionTemplate().toJsonNode(),
                        consumer.getId(), consumers);
            }
        }
        return consumers;
    }

    private void collectExplicitConsumers(JsonNode node, String consumerId,
                                          Map<String, Set<String>> consumers) {
        if (node.isObject()) {
            JsonNode reference = node.get("flow_output");
            if (reference != null && reference.path("step").isTextual()) {
                consumers.computeIfAbsent(reference.path("step").asText(), ignored ->
                        new java.util.LinkedHashSet<>()).add(consumerId);
            }
            node.elements().forEachRemaining(child ->
                    collectExplicitConsumers(child, consumerId, consumers));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child ->
                    collectExplicitConsumers(child, consumerId, consumers));
        }
    }

    private void validateFlowOutputNode(JsonNode node, TxFlow definition,
                                        Map<String, Integer> stepIndexes, int consumerIndex,
                                        List<FlowDiagnostic> diagnostics, String path) {
        if (node.isObject()) {
            JsonNode reference = node.get("flow_output");
            if (reference != null) {
                String stepId = reference.path("step").asText(null);
                String outputName = reference.path("output").asText(null);
                Integer producerIndex = stepIndexes.get(stepId);
                if (stepId == null || outputName == null) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_FLOW_OUTPUT_INVALID",
                            "flow_output requires step and output", path + ".flow_output"));
                } else if (producerIndex == null) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_FLOW_OUTPUT_STEP_MISSING",
                            "Referenced producer step does not exist: " + stepId,
                            path + ".flow_output.step"));
                } else if (producerIndex >= consumerIndex) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_FLOW_OUTPUT_NOT_PRIOR",
                            "flow_output must reference an earlier step",
                            path + ".flow_output.step"));
                } else if (!definition.getSteps().get(producerIndex)
                        .getOutputBindings().containsKey(outputName)) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_FLOW_OUTPUT_BINDING_MISSING",
                            "Referenced named output does not exist: " + stepId + "." + outputName,
                            path + ".flow_output.output"));
                }
            }
            node.fields().forEachRemaining(entry -> validateFlowOutputNode(entry.getValue(),
                    definition, stepIndexes, consumerIndex, diagnostics,
                    path + "." + entry.getKey()));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                validateFlowOutputNode(node.get(i), definition, stepIndexes, consumerIndex,
                        diagnostics, path + "[" + i + "]");
            }
        }
    }

    private Map<String, Object> validateBindings(Map<String, ParameterSpec> specs, FlowBindings bindings,
                                                 List<FlowDiagnostic> diagnostics) {
        Map<String, Object> values = new LinkedHashMap<>();
        specs.values().forEach(spec -> {
            if (spec.getDefaultValue() != null) values.put(spec.getName(), spec.getDefaultValue());
        });
        for (String name : bindings.asMap().keySet()) {
            if (!specs.containsKey(name)) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_UNKNOWN_BINDING",
                        "Unknown binding: " + name, "$.bindings." + name));
            }
        }
        bindings.asMap().forEach(values::put);
        for (ParameterSpec spec : specs.values()) {
            Object value = values.get(spec.getName());
            if (value == null) {
                if (spec.isRequired()) diagnostics.add(FlowDiagnostic.error("TXFLOW_REQUIRED_BINDING",
                        "Required binding is missing: " + spec.getName(), "$.bindings." + spec.getName()));
                continue;
            }
            if (!matchesType(spec.getType(), value)) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_BINDING_TYPE",
                        "Binding '" + spec.getName() + "' must be " + spec.getType(),
                        "$.bindings." + spec.getName()));
                continue;
            }
            if (value instanceof Number) {
                long number = ((Number) value).longValue();
                if (spec.getMinimum() != null && number < spec.getMinimum()) diagnostics.add(
                        FlowDiagnostic.error("TXFLOW_BINDING_MINIMUM", "Binding is below minimum",
                                "$.bindings." + spec.getName()));
                if (spec.getMaximum() != null && number > spec.getMaximum()) diagnostics.add(
                        FlowDiagnostic.error("TXFLOW_BINDING_MAXIMUM", "Binding exceeds maximum",
                                "$.bindings." + spec.getName()));
            }
            if (value instanceof String && spec.getMaxLength() != null
                    && ((String) value).length() > spec.getMaxLength()) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_BINDING_MAX_LENGTH",
                        "Binding exceeds maximum length", "$.bindings." + spec.getName()));
            }
        }
        return values;
    }

    private boolean matchesType(ParameterType type, Object value) {
        switch (type) {
            case INTEGER: return value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long;
            case BOOLEAN: return value instanceof Boolean;
            default: return value instanceof String;
        }
    }

    private JsonNode bindNode(JsonNode node, Map<String, Object> values, String path,
                              List<FlowDiagnostic> diagnostics) {
        if (node.isTextual()) {
            String text = node.asText();
            Matcher exact = EXACT_INPUT.matcher(text);
            if (exact.matches()) {
                Object value = values.get(exact.group(1));
                if (value == null) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_UNBOUND_INPUT",
                            "No value for input " + exact.group(1), path));
                    return node;
                }
                return JSON.valueToTree(value);
            }
            Matcher interpolation = INTERPOLATED_INPUT.matcher(text);
            StringBuffer resolved = new StringBuffer();
            while (interpolation.find()) {
                Object value = values.get(interpolation.group(1));
                if (!(value instanceof String)) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_INVALID_INTERPOLATION",
                            "Only string inputs may be interpolated", path));
                    return node;
                }
                interpolation.appendReplacement(resolved, Matcher.quoteReplacement((String) value));
            }
            interpolation.appendTail(resolved);
            return TextNode.valueOf(resolved.toString());
        }
        if (node.isObject()) {
            ObjectNode copy = (ObjectNode) node.deepCopy();
            Iterator<Map.Entry<String, JsonNode>> fields = copy.fields();
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
            fields.forEachRemaining(entries::add);
            for (Map.Entry<String, JsonNode> entry : entries) {
                if (entry.getKey().contains("${{")) {
                    diagnostics.add(FlowDiagnostic.error("TXFLOW_EXPRESSION_IN_KEY",
                            "Expressions are not allowed in property names", path));
                } else {
                    copy.set(entry.getKey(), bindNode(entry.getValue(), values,
                            path + "." + entry.getKey(), diagnostics));
                }
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JSON.createArrayNode();
            for (int i = 0; i < node.size(); i++) {
                copy.add(bindNode(node.get(i), values, path + "[" + i + "]", diagnostics));
            }
            return copy;
        }
        return node.deepCopy();
    }

    private TxPlan toTxPlan(JsonNode transaction) throws Exception {
        TransactionDocument document = new TransactionDocument();
        document.setVersion("1.0");
        document.setContext(JSON.treeToValue(transaction.get("context"), TransactionDocument.TxContext.class));
        TransactionDocument.TxContent tx = JSON.treeToValue(
                transaction.get("tx"), TransactionDocument.TxContent.class);
        document.setTransaction(List.of(new TransactionDocument.TxEntry(tx)));
        return TxPlan.from(YamlSerializer.serialize(document));
    }

    private void preflightResources(JsonNode node, FlowCompilationRequest request, String network,
                                    List<FlowDiagnostic> diagnostics, Set<String> spendingResources,
                                    String path) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String childPath = path + "." + entry.getKey();
                if ((entry.getKey().equals("ref") || entry.getKey().endsWith("_ref"))
                        && entry.getValue().isTextual()
                        && entry.getValue().asText().contains("://")) {
                    ResourceRef ref;
                    try {
                        ref = ResourceRef.of(entry.getValue().asText());
                    } catch (Exception e) {
                        diagnostics.add(FlowDiagnostic.error("TXFLOW_RESOURCE_REF_INVALID",
                                e.getMessage(), childPath));
                        return;
                    }
                    if (!request.policy().isResourceAllowed(ref.value())) {
                        diagnostics.add(FlowDiagnostic.error("TXFLOW_RESOURCE_UNAUTHORIZED",
                                "Resource is not authorized by server policy", childPath));
                        return;
                    }
                    if (request.resources() == null) return;
                    ResourceDescriptor descriptor = request.resources().resolve(ref).orElse(null);
                    if (descriptor == null) {
                        diagnostics.add(FlowDiagnostic.error("TXFLOW_RESOURCE_MISSING",
                                "Resource is not registered: " + ref.value(), childPath));
                        return;
                    }
                    if (network != null && descriptor.network() != null
                            && !network.equals(descriptor.network())) {
                        diagnostics.add(FlowDiagnostic.error("TXFLOW_RESOURCE_NETWORK",
                                "Resource belongs to a different network", childPath));
                    }
                    ResourceCapability required = requiredCapability(entry.getKey(), ref);
                    if (!descriptor.supports(required)) {
                        diagnostics.add(FlowDiagnostic.error("TXFLOW_RESOURCE_CAPABILITY",
                                "Resource does not provide " + required, childPath));
                    }
                    if (required == ResourceCapability.SPEND && descriptor.spendingIdentity() != null
                            && !descriptor.spendingIdentity().isBlank()) {
                        spendingResources.add(descriptor.spendingIdentity());
                    }
                } else {
                    preflightResources(entry.getValue(), request, network, diagnostics,
                            spendingResources, childPath);
                }
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                preflightResources(node.get(i), request, network, diagnostics,
                        spendingResources, path + "[" + i + "]");
            }
        }
    }

    private ResourceCapability requiredCapability(String fieldName, ResourceRef ref) {
        if (ref.value().startsWith("script://")) return ResourceCapability.SCRIPT;
        if (ref.value().startsWith("policy://")) return ResourceCapability.MINT;
        if (fieldName.equals("ref")) return ResourceCapability.SIGN;
        return ResourceCapability.SPEND;
    }

}
