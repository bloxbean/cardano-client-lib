package com.bloxbean.cardano.client.txflow.yaml;

import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TransactionDocument;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.SelectionStrategy;
import com.bloxbean.cardano.client.txflow.StepDependency;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Document structure for TxFlow YAML serialization.
 * <p>
 * Supports the following YAML format:
 * <pre>
 * version: "1.0"
 * flow:
 *   id: deposit-withdraw
 *   description: Deposit and withdraw from contract
 *   variables:
 *     amount: 100000000
 *   steps:
 *     - step:
 *         id: deposit
 *         description: Deposit ADA to contract
 *         tx:
 *           from: ${sender}
 *           intents:
 *             - type: payment
 *               receiver: ${contract}
 *               amount: { lovelace: ${amount} }
 *         context:
 *           signers:
 *             - ref: account://alice
 *     - step:
 *         id: withdraw
 *         depends_on:
 *           - from_step: deposit
 *             strategy: all
 *         scriptTx:
 *           # ... existing TxPlan format
 * </pre>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class FlowDocument {

    private static final ObjectMapper YAML_MAPPER;

    static {
        YAMLFactory factory = new YAMLFactory();
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);

        YAML_MAPPER = new ObjectMapper(factory);
        YAML_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @JsonProperty("version")
    private String version = "1.0";

    @JsonProperty("flow")
    private FlowContent flow;

    /**
     * Flow content with steps and variables.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FlowContent {
        @JsonProperty("id")
        private String id;

        @JsonProperty("description")
        private String description;

        @JsonProperty("variables")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> variables = new HashMap<>();

        @JsonProperty("steps")
        private List<StepEntry> steps = new ArrayList<>();
    }

    /**
     * Entry for a single step in the flow.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StepEntry {
        @JsonProperty("step")
        private StepContent step;
    }

    /**
     * Content of a single step.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StepContent {
        @JsonProperty("id")
        private String id;

        @JsonProperty("description")
        private String description;

        @JsonProperty("depends_on")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<DependencyEntry> dependsOn;

        // Inline tx/scriptTx content (reusing TransactionDocument structure)
        @JsonProperty("tx")
        private TransactionDocument.TxContent tx;

        @JsonProperty("scriptTx")
        private TransactionDocument.ScriptTxContent scriptTx;

        // Context for this step (signers, etc.)
        @JsonProperty("context")
        private TransactionDocument.TxContext context;
    }

    /**
     * Dependency declaration for a step.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DependencyEntry {
        @JsonProperty("from_step")
        private String fromStep;

        @JsonProperty("strategy")
        private String strategy = "all";

        @JsonProperty("utxo_index")
        private Integer utxoIndex;

        @JsonProperty("filter")
        private String filter;

        @JsonProperty("optional")
        private Boolean optional;
    }

    /**
     * Create a FlowDocument from a TxFlow.
     *
     * @param flow the TxFlow to convert
     * @return the FlowDocument
     */
    public static FlowDocument fromFlow(TxFlow flow) {
        FlowDocument doc = new FlowDocument();
        doc.setVersion(flow.getVersion());

        FlowContent content = new FlowContent();
        content.setId(flow.getId());
        content.setDescription(flow.getDescription());
        content.setVariables(new HashMap<>(flow.getVariables()));

        List<StepEntry> stepEntries = new ArrayList<>();
        for (FlowStep step : flow.getSteps()) {
            StepEntry entry = new StepEntry();
            StepContent stepContent = new StepContent();

            stepContent.setId(step.getId());
            stepContent.setDescription(step.getDescription());

            // Convert dependencies
            if (step.hasDependencies()) {
                List<DependencyEntry> deps = new ArrayList<>();
                for (StepDependency dep : step.getDependencies()) {
                    DependencyEntry depEntry = new DependencyEntry();
                    depEntry.setFromStep(dep.getStepId());
                    depEntry.setStrategy(dep.getStrategy().name().toLowerCase());
                    if (dep.getUtxoIndex() != null) {
                        depEntry.setUtxoIndex(dep.getUtxoIndex());
                    }
                    if (dep.isOptional()) {
                        depEntry.setOptional(true);
                    }
                    deps.add(depEntry);
                }
                stepContent.setDependsOn(deps);
            }

            // Convert transaction - either from TxPlan or AbstractTx
            if (step.hasTxPlan()) {
                TxPlan plan = step.getTxPlan();
                // Convert TxPlan to inline format
                convertTxPlanToStepContent(plan, stepContent);
            } else if (step.hasTx()) {
                // Convert AbstractTx to TxPlan first, then to inline format
                TxPlan plan = TxPlan.from(step.getTx());
                convertTxPlanToStepContent(plan, stepContent);
            }

            entry.setStep(stepContent);
            stepEntries.add(entry);
        }

        content.setSteps(stepEntries);
        doc.setFlow(content);

        return doc;
    }

    /**
     * Convert TxPlan content to inline StepContent format.
     */
    private static void convertTxPlanToStepContent(TxPlan plan, StepContent stepContent) {
        // Get the first transaction from the plan
        List<AbstractTx<?>> txs = plan.getTxs();
        if (txs == null || txs.isEmpty()) {
            return;
        }

        // Convert using TxPlan's YAML and then deserialize the transaction part
        String yaml = plan.toYaml();
        try {
            TransactionDocument doc = YAML_MAPPER.readValue(yaml, TransactionDocument.class);
            if (doc.getTransaction() != null && !doc.getTransaction().isEmpty()) {
                TransactionDocument.TxEntry entry = doc.getTransaction().get(0);
                if (entry.isTx()) {
                    stepContent.setTx(entry.getTx());
                } else if (entry.isScriptTx()) {
                    stepContent.setScriptTx(entry.getScriptTx());
                }
            }
            if (doc.getContext() != null) {
                stepContent.setContext(doc.getContext());
            }
        } catch (Exception e) {
            log.error("Failed to convert TxPlan to step content", e);
        }
    }

    /**
     * Create a TxFlow from this FlowDocument.
     *
     * @return the TxFlow
     */
    public TxFlow toFlow() {
        if (flow == null) {
            throw new IllegalStateException("FlowDocument has no flow content");
        }

        TxFlow.Builder builder = TxFlow.builder(flow.getId())
                .withDescription(flow.getDescription())
                .withVersion(version);

        if (flow.getVariables() != null) {
            builder.withVariables(flow.getVariables());
        }

        Map<String, Object> variables = flow.getVariables() != null ? flow.getVariables() : Collections.emptyMap();

        for (StepEntry entry : flow.getSteps()) {
            StepContent stepContent = entry.getStep();
            FlowStep.Builder stepBuilder = FlowStep.builder(stepContent.getId())
                    .withDescription(stepContent.getDescription());

            // Convert dependencies
            if (stepContent.getDependsOn() != null) {
                for (DependencyEntry dep : stepContent.getDependsOn()) {
                    SelectionStrategy strategy = parseStrategy(dep.getStrategy());
                    StepDependency.Builder depBuilder = StepDependency.builder(dep.getFromStep())
                            .withStrategy(strategy);

                    if (dep.getUtxoIndex() != null) {
                        depBuilder.withUtxoIndex(dep.getUtxoIndex());
                    }
                    if (dep.getOptional() != null && dep.getOptional()) {
                        depBuilder.optional();
                    }

                    stepBuilder.dependsOn(depBuilder.build());
                }
            }

            // Convert inline transaction to TxPlan
            TxPlan txPlan = createTxPlanFromStepContent(stepContent, variables);
            stepBuilder.withTxPlan(txPlan);

            builder.addStep(stepBuilder.build());
        }

        return builder.build();
    }

    /**
     * Parse a selection strategy string to enum.
     */
    private SelectionStrategy parseStrategy(String strategy) {
        if (strategy == null || strategy.isEmpty()) {
            return SelectionStrategy.ALL;
        }
        try {
            return SelectionStrategy.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown selection strategy: {}, defaulting to ALL", strategy);
            return SelectionStrategy.ALL;
        }
    }

    /**
     * Create a TxPlan from step content.
     */
    private TxPlan createTxPlanFromStepContent(StepContent stepContent, Map<String, Object> flowVariables) {
        // Build a TransactionDocument from the step content
        TransactionDocument doc = new TransactionDocument();
        doc.setVersion("1.0");
        doc.setContext(stepContent.getContext());

        List<TransactionDocument.TxEntry> entries = new ArrayList<>();
        if (stepContent.getTx() != null) {
            entries.add(new TransactionDocument.TxEntry(stepContent.getTx()));
        } else if (stepContent.getScriptTx() != null) {
            entries.add(new TransactionDocument.TxEntry(stepContent.getScriptTx()));
        }
        doc.setTransaction(entries);

        // Serialize and deserialize through TxPlan
        try {
            String yaml = YAML_MAPPER.writeValueAsString(doc);
            TxPlan plan = TxPlan.from(yaml);

            // Add flow variables
            for (var entry : flowVariables.entrySet()) {
                if (!plan.getVariables().containsKey(entry.getKey())) {
                    plan.addVariable(entry.getKey(), entry.getValue());
                }
            }

            return plan;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TxPlan from step content", e);
        }
    }

    /**
     * Serialize this document to YAML.
     *
     * @return YAML string
     */
    public String toYaml() {
        try {
            return YAML_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize FlowDocument to YAML", e);
        }
    }

    /**
     * Deserialize YAML to FlowDocument.
     *
     * @param yaml the YAML string
     * @return the FlowDocument
     */
    public static FlowDocument fromYaml(String yaml) {
        try {
            // Extract variables and expand template if present
            JsonNode tree = YAML_MAPPER.readTree(yaml);
            JsonNode flowNode = tree.get("flow");
            if (flowNode != null) {
                JsonNode varsNode = flowNode.get("variables");
                if (varsNode != null && varsNode.isObject()) {
                    Map<String, Object> variables = YAML_MAPPER.convertValue(varsNode, Map.class);
                    yaml = VariableResolver.resolve(yaml, variables);
                }
            }

            return YAML_MAPPER.readValue(yaml, FlowDocument.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize YAML to FlowDocument", e);
        }
    }

    /**
     * Validate the YAML version.
     *
     * @param yaml the YAML string
     * @throws IllegalArgumentException if version is unsupported
     */
    public static void validateVersion(String yaml) {
        try {
            JsonNode tree = YAML_MAPPER.readTree(yaml);
            JsonNode versionNode = tree.get("version");
            if (versionNode != null) {
                String version = versionNode.asText();
                if (!"1.0".equals(version)) {
                    throw new IllegalArgumentException("Unsupported flow document version: " + version);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate YAML version", e);
        }
    }
}
