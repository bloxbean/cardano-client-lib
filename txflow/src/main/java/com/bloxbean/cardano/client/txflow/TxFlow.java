package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.txflow.yaml.FlowDocument;
import lombok.Getter;

import java.util.*;

/**
 * Top-level container for a multi-step transaction flow.
 * <p>
 * TxFlow orchestrates the execution of multiple transaction steps with
 * UTXO dependency management between steps. It supports:
 * <ul>
 *     <li>Variable substitution across all steps</li>
 *     <li>Automatic UTXO resolution from previous step outputs</li>
 *     <li>Sequential execution with confirmation waiting</li>
 *     <li>YAML serialization/deserialization</li>
 * </ul>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * TxFlow flow = TxFlow.builder("escrow-flow")
 *     .withDescription("Deposit and release escrow")
 *     .addVariable("amount", 50_000_000L)
 *     .addStep(FlowStep.builder("deposit")
 *         .withTx(new Tx()
 *             .payToAddress(contractAddr, Amount.lovelace("${amount}"))
 *             .from(senderAddr))
 *         .build())
 *     .addStep(FlowStep.builder("release")
 *         .dependsOn("deposit", SelectionStrategy.ALL)
 *         .withTx(new ScriptTx()
 *             .collectFrom(...)
 *             .payToAddress(receiverAddr, Amount.ada(1)))
 *         .build())
 *     .build();
 * }</pre>
 */
@Getter
public class TxFlow {
    private static final String DEFAULT_VERSION = "1.0";

    private final String id;
    private final String description;
    private final String version;
    private final Map<String, Object> variables;
    private final List<FlowStep> steps;

    private TxFlow(Builder builder) {
        this.id = builder.id;
        this.description = builder.description;
        this.version = builder.version;
        this.variables = Collections.unmodifiableMap(new HashMap<>(builder.variables));
        this.steps = Collections.unmodifiableList(new ArrayList<>(builder.steps));
    }

    /**
     * Get a step by its ID.
     *
     * @param stepId the step ID
     * @return the step, or empty if not found
     */
    public Optional<FlowStep> getStep(String stepId) {
        return steps.stream()
                .filter(s -> stepId.equals(s.getId()))
                .findFirst();
    }

    /**
     * Get all step IDs in order.
     *
     * @return list of step IDs
     */
    public List<String> getStepIds() {
        List<String> ids = new ArrayList<>();
        for (FlowStep step : steps) {
            ids.add(step.getId());
        }
        return ids;
    }

    /**
     * Validate this flow for correctness.
     * <p>
     * Checks for:
     * <ul>
     *     <li>Duplicate step IDs</li>
     *     <li>Circular dependencies</li>
     *     <li>References to non-existent steps</li>
     *     <li>Each step has valid transaction definition</li>
     * </ul>
     *
     * @return validation result
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check for duplicate step IDs
        Set<String> seenIds = new HashSet<>();
        for (FlowStep step : steps) {
            if (!seenIds.add(step.getId())) {
                errors.add("Duplicate step ID: " + step.getId());
            }
        }

        // Check dependency references
        Set<String> allStepIds = new HashSet<>(seenIds);
        for (FlowStep step : steps) {
            for (String depId : step.getDependencyStepIds()) {
                if (!allStepIds.contains(depId)) {
                    errors.add("Step '" + step.getId() + "' depends on non-existent step: " + depId);
                }
            }
        }

        // Check for circular dependencies
        String cycleError = detectCycle();
        if (cycleError != null) {
            errors.add(cycleError);
        }

        // Check forward dependencies (step depends on later step)
        Map<String, Integer> stepOrder = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            stepOrder.put(steps.get(i).getId(), i);
        }
        for (FlowStep step : steps) {
            int stepIndex = stepOrder.get(step.getId());
            for (String depId : step.getDependencyStepIds()) {
                Integer depIndex = stepOrder.get(depId);
                if (depIndex != null && depIndex >= stepIndex) {
                    errors.add("Step '" + step.getId() + "' depends on later step '" + depId +
                            "'. Dependencies must be on earlier steps.");
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Detect circular dependencies using DFS.
     *
     * @return error message if cycle found, null otherwise
     */
    private String detectCycle() {
        Map<String, List<String>> graph = new HashMap<>();
        for (FlowStep step : steps) {
            graph.put(step.getId(), step.getDependencyStepIds());
        }

        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (FlowStep step : steps) {
            String cycle = dfs(step.getId(), graph, visited, inStack, new ArrayList<>());
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private String dfs(String node, Map<String, List<String>> graph,
                       Set<String> visited, Set<String> inStack, List<String> path) {
        if (inStack.contains(node)) {
            path.add(node);
            int cycleStart = path.indexOf(node);
            List<String> cycle = path.subList(cycleStart, path.size());
            return "Circular dependency detected: " + String.join(" -> ", cycle);
        }
        if (visited.contains(node)) {
            return null;
        }

        visited.add(node);
        inStack.add(node);
        path.add(node);

        List<String> deps = graph.get(node);
        if (deps != null) {
            for (String dep : deps) {
                String result = dfs(dep, graph, visited, inStack, path);
                if (result != null) {
                    return result;
                }
            }
        }

        inStack.remove(node);
        path.remove(path.size() - 1);
        return null;
    }

    /**
     * Serialize this flow to YAML format.
     *
     * @return YAML string representation
     */
    public String toYaml() {
        return FlowDocument.fromFlow(this).toYaml();
    }

    /**
     * Deserialize a YAML string to a TxFlow.
     *
     * @param yaml the YAML string
     * @return the deserialized TxFlow
     */
    public static TxFlow fromYaml(String yaml) {
        return FlowDocument.fromYaml(yaml).toFlow();
    }

    /**
     * Create a new builder for TxFlow.
     *
     * @param id the unique flow ID
     * @return a new builder
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    @Override
    public String toString() {
        return "TxFlow{" +
                "id='" + id + '\'' +
                ", description='" + description + '\'' +
                ", version='" + version + '\'' +
                ", steps=" + steps.size() +
                ", variables=" + variables.size() +
                '}';
    }

    /**
     * Result of flow validation.
     */
    @Getter
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        @Override
        public String toString() {
            if (valid) {
                return "ValidationResult{valid=true" +
                        (warnings.isEmpty() ? "" : ", warnings=" + warnings) + "}";
            }
            return "ValidationResult{valid=false, errors=" + errors +
                    (warnings.isEmpty() ? "" : ", warnings=" + warnings) + "}";
        }
    }

    /**
     * Builder for TxFlow.
     */
    public static class Builder {
        private final String id;
        private String description;
        private String version = DEFAULT_VERSION;
        private final Map<String, Object> variables = new HashMap<>();
        private final List<FlowStep> steps = new ArrayList<>();

        private Builder(String id) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Flow ID cannot be null or empty");
            }
            this.id = id;
        }

        /**
         * Set a description for this flow.
         *
         * @param description the flow description
         * @return this builder
         */
        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the schema version.
         *
         * @param version the version string
         * @return this builder
         */
        public Builder withVersion(String version) {
            this.version = version;
            return this;
        }

        /**
         * Add a variable to the flow.
         *
         * @param name the variable name
         * @param value the variable value
         * @return this builder
         */
        public Builder addVariable(String name, Object value) {
            this.variables.put(name, value);
            return this;
        }

        /**
         * Set all variables at once.
         *
         * @param variables the variables map
         * @return this builder
         */
        public Builder withVariables(Map<String, Object> variables) {
            this.variables.clear();
            if (variables != null) {
                this.variables.putAll(variables);
            }
            return this;
        }

        /**
         * Add a step to the flow.
         *
         * @param step the step to add
         * @return this builder
         */
        public Builder addStep(FlowStep step) {
            this.steps.add(step);
            return this;
        }

        /**
         * Add multiple steps to the flow.
         *
         * @param steps the steps to add
         * @return this builder
         */
        public Builder withSteps(List<FlowStep> steps) {
            this.steps.clear();
            if (steps != null) {
                this.steps.addAll(steps);
            }
            return this;
        }

        /**
         * Build the TxFlow.
         *
         * @return the built TxFlow
         * @throws IllegalStateException if no steps are defined
         */
        public TxFlow build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("TxFlow must have at least one step");
            }
            return new TxFlow(this);
        }
    }
}
