package com.bloxbean.cardano.client.txflow.yaml;

import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TransactionDocument;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.txflow.BackoffStrategy;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.SelectionStrategy;
import com.bloxbean.cardano.client.txflow.StepDependency;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.internal.DurationCodec;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.LoaderOptions;

import java.time.Duration;
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
@JsonPropertyOrder({"version", "context", "flow"})
@Slf4j
public class FlowDocument {

    private static final ObjectMapper YAML_MAPPER;

    static {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setMaxAliasesForCollections(50);
        loaderOptions.setNestingDepthLimit(100);
        loaderOptions.setCodePointLimit(3_000_000);

        YAMLFactory factory = YAMLFactory.builder()
                .loaderOptions(loaderOptions)
                .build();
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);

        YAML_MAPPER = new ObjectMapper(factory);
        YAML_MAPPER.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        YAML_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @JsonProperty("version")
    private String version = "1.0";

    @JsonProperty("context")
    private ExecutionContext context;

    @JsonProperty("flow")
    private FlowContent flow;

    /**
     * Flow-level execution context.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"chaining_mode", "confirmation", "rollback_strategy", "retry"})
    public static class ExecutionContext {
        @JsonProperty("chaining_mode")
        private String chainingMode;

        @JsonProperty("confirmation")
        private JsonNode confirmation;

        @JsonProperty("rollback_strategy")
        private String rollbackStrategy;

        @JsonProperty("retry")
        private RetryEntry retry;
    }

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

        @JsonProperty("retry")
        private RetryEntry retry;

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

        @JsonProperty("optional")
        private Boolean optional;

        /**
         * Retained only so legacy documents containing the historical, non-portable
         * filter field can still be read. The codec reports that it is not executable.
         */
        @JsonProperty("filter")
        private JsonNode filter;
    }

    /**
     * Retry policy configuration for a step.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RetryEntry {
        @JsonProperty("max_attempts")
        private Integer maxAttempts;

        @JsonProperty("backoff")
        private String backoff;

        @JsonProperty("initial_delay")
        private String initialDelay;

        @JsonProperty("max_delay")
        private String maxDelay;

        @JsonProperty("retry_on_timeout")
        private Boolean retryOnTimeout;

        @JsonProperty("retry_on_network_error")
        private Boolean retryOnNetworkError;
    }

    /**
     * Flow-level confirmation configuration.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConfirmationEntry {
        @JsonProperty("preset")
        private String preset;

        @JsonProperty("min_confirmations")
        private Integer minConfirmations;

        @JsonProperty("check_interval")
        private String checkInterval;

        @JsonProperty("timeout")
        private String timeout;

        @JsonProperty("max_rollback_retries")
        private Integer maxRollbackRetries;

        @JsonProperty("wait_for_backend_after_rollback")
        private Boolean waitForBackendAfterRollback;

        @JsonProperty("post_rollback_wait_attempts")
        private Integer postRollbackWaitAttempts;

        @JsonProperty("post_rollback_utxo_sync_delay")
        private String postRollbackUtxoSyncDelay;

        @JsonProperty("required_authoritative_absences")
        private Integer requiredAuthoritativeAbsences;

        boolean isEmpty() {
            return preset == null
                    && minConfirmations == null
                    && checkInterval == null
                    && timeout == null
                    && maxRollbackRetries == null
                    && waitForBackendAfterRollback == null
                    && postRollbackWaitAttempts == null
                    && postRollbackUtxoSyncDelay == null
                    && requiredAuthoritativeAbsences == null;
        }
    }

    /**
     * Create a FlowDocument from a TxFlow.
     *
     * @param flow the TxFlow to convert
     * @return the FlowDocument
     */
    public static FlowDocument fromFlow(TxFlow flow) {
        FlowDocument doc = new FlowDocument();
        if (flow.getExecutionSettings() != null && flow.getExecutionSettings().hasAnySetting()) {
            doc.setContext(toExecutionContext(flow.getExecutionSettings()));
        }

        FlowContent content = new FlowContent();
        content.setId(flow.getId());
        content.setDescription(flow.getDescription());
        content.setVariables(new HashMap<>(flow.getVariables()));

        List<StepEntry> stepEntries = new ArrayList<>();
        for (FlowStep step : flow.getSteps()) {
            if (step.hasTxContextFactory()) {
                throw new IllegalStateException("Step '" + step.getId()
                        + "' uses a Java transaction factory and cannot be serialized");
            }
            if (!step.hasTxPlan()) {
                throw new IllegalStateException("Step '" + step.getId()
                        + "' has no serializable transaction plan");
            }
            if (step.getTxPlan().getTxs().size() != 1) {
                throw new IllegalStateException("Step '" + step.getId()
                        + "' must contain exactly one transaction for TxFlow YAML");
            }
            for (StepDependency dependency : step.getDependencies()) {
                if (dependency.getStrategy() == SelectionStrategy.FILTER) {
                    throw new IllegalStateException("Step '" + step.getId()
                            + "' uses a predicate FILTER dependency that cannot be serialized");
                }
            }

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

            // Convert retry policy
            if (step.hasRetryPolicy()) {
                stepContent.setRetry(toRetryEntry(step.getRetryPolicy()));
            }

            // Convert transaction - only TxPlan can be serialized to YAML
            // Steps with txContextFactory (factory functions) cannot be serialized
            if (step.hasTxPlan()) {
                TxPlan plan = step.getTxPlan();
                // Convert TxPlan to inline format
                convertTxPlanToStepContent(plan, stepContent);
            } else if (step.hasTxContextFactory()) {
                // Factory functions cannot be serialized to YAML (by design per ADR-003)
                log.debug("Step '{}' has a txContextFactory which cannot be serialized to YAML. " +
                        "Transaction content will be omitted.", step.getId());
            }

            entry.setStep(stepContent);
            stepEntries.add(entry);
        }

        content.setSteps(stepEntries);
        doc.setFlow(content);

        return doc;
    }

    private static ExecutionContext toExecutionContext(FlowExecutionSettings settings) {
        ExecutionContext context = new ExecutionContext();
        if (settings.getChainingMode() != null) {
            context.setChainingMode(settings.getChainingMode().name());
        }
        if (settings.getConfirmationConfig() != null) {
            context.setConfirmation(YAML_MAPPER.valueToTree(toConfirmationEntry(settings.getConfirmationConfig())));
        }
        if (settings.getRollbackStrategy() != null) {
            context.setRollbackStrategy(settings.getRollbackStrategy().name());
        }
        if (settings.getRetryPolicy() != null) {
            context.setRetry(toRetryEntry(settings.getRetryPolicy()));
        }
        return context;
    }

    private static RetryEntry toRetryEntry(RetryPolicy policy) {
        RetryEntry retryEntry = new RetryEntry();
        retryEntry.setMaxAttempts(policy.getMaxAttempts());
        retryEntry.setBackoff(policy.getBackoffStrategy().name().toLowerCase(Locale.ROOT));
        retryEntry.setInitialDelay(formatDuration(policy.getInitialDelay()));
        retryEntry.setMaxDelay(formatDuration(policy.getMaxDelay()));
        if (!policy.isRetryOnTimeout()) {
            retryEntry.setRetryOnTimeout(false);
        }
        if (!policy.isRetryOnNetworkError()) {
            retryEntry.setRetryOnNetworkError(false);
        }
        return retryEntry;
    }

    private static ConfirmationEntry toConfirmationEntry(ConfirmationConfig config) {
        ConfirmationEntry entry = new ConfirmationEntry();
        entry.setMinConfirmations(config.getMinConfirmations());
        entry.setCheckInterval(formatDuration(config.getCheckInterval()));
        entry.setTimeout(formatDuration(config.getTimeout()));
        entry.setMaxRollbackRetries(config.getMaxRollbackRetries());
        entry.setWaitForBackendAfterRollback(config.isWaitForBackendAfterRollback());
        entry.setPostRollbackWaitAttempts(config.getPostRollbackWaitAttempts());
        entry.setPostRollbackUtxoSyncDelay(formatDuration(config.getPostRollbackUtxoSyncDelay()));
        entry.setRequiredAuthoritativeAbsences(config.getRequiredAuthoritativeAbsences());
        return entry;
    }

    /**
     * Convert TxPlan content to inline StepContent format.
     */
    private static void convertTxPlanToStepContent(TxPlan plan, StepContent stepContent) {
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
            throw new IllegalStateException("Failed to serialize TxPlan transaction", e);
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
                .withDescription(flow.getDescription());

        if (context != null) {
            builder.withExecutionSettings(parseExecutionSettings(context));
        }

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

            // Convert retry policy
            if (stepContent.getRetry() != null) {
                RetryPolicy retryPolicy = parseRetryPolicy(stepContent.getRetry());
                stepBuilder.withRetryPolicy(retryPolicy);
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
        if (strategy == null || strategy.trim().isEmpty()) {
            return SelectionStrategy.ALL;
        }
        return parseEnumStrict(SelectionStrategy.class, strategy, "strategy");
    }

    /**
     * Parse a RetryEntry to a RetryPolicy.
     */
    private RetryPolicy parseRetryPolicy(RetryEntry entry) {
        RetryPolicy.RetryPolicyBuilder builder = RetryPolicy.builder();

        if (entry.getMaxAttempts() != null) {
            requirePositive(entry.getMaxAttempts(), "max_attempts");
            builder.maxAttempts(entry.getMaxAttempts());
        }
        if (entry.getBackoff() != null) {
            builder.backoffStrategy(parseBackoffStrategyStrict(entry.getBackoff()));
        }
        if (entry.getInitialDelay() != null) {
            builder.initialDelay(requireNonNegative(
                    parseDurationStrict(entry.getInitialDelay(), "initial_delay"), "initial_delay"));
        }
        if (entry.getMaxDelay() != null) {
            builder.maxDelay(requireNonNegative(
                    parseDurationStrict(entry.getMaxDelay(), "max_delay"), "max_delay"));
        }
        if (entry.getRetryOnTimeout() != null) {
            builder.retryOnTimeout(entry.getRetryOnTimeout());
        }
        if (entry.getRetryOnNetworkError() != null) {
            builder.retryOnNetworkError(entry.getRetryOnNetworkError());
        }

        return builder.build();
    }

    private FlowExecutionSettings parseExecutionSettings(ExecutionContext context) {
        FlowExecutionSettings.FlowExecutionSettingsBuilder builder = FlowExecutionSettings.builder();

        if (context.getChainingMode() != null) {
            builder.chainingMode(parseEnumStrict(ChainingMode.class, context.getChainingMode(), "chaining_mode"));
        }
        if (context.getConfirmation() != null) {
            builder.confirmationConfig(parseConfirmationConfig(context.getConfirmation()));
        }
        if (context.getRollbackStrategy() != null) {
            builder.rollbackStrategy(parseEnumStrict(RollbackStrategy.class, context.getRollbackStrategy(), "rollback_strategy"));
        }
        if (context.getRetry() != null) {
            builder.retryPolicy(parseRetryPolicy(context.getRetry()));
        }

        return builder.build();
    }

    private ConfirmationConfig parseConfirmationConfig(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("context.confirmation cannot be null. Omit it for simple confirmation mode.");
        }

        if (node.isTextual()) {
            String preset = node.asText();
            if (preset == null || preset.trim().isEmpty()) {
                throw new IllegalArgumentException("context.confirmation preset cannot be blank");
            }
            return confirmationPreset(preset);
        }

        if (!node.isObject()) {
            throw new IllegalArgumentException("context.confirmation must be a preset string or an object");
        }

        ConfirmationEntry entry = YAML_MAPPER.convertValue(node, ConfirmationEntry.class);
        if (entry.isEmpty()) {
            throw new IllegalArgumentException("context.confirmation object cannot be empty");
        }

        ConfirmationConfig base = entry.getPreset() != null
                ? confirmationPreset(entry.getPreset())
                : ConfirmationConfig.defaults();

        ConfirmationConfig.Builder builder = ConfirmationConfig.builder()
                .minConfirmations(base.getMinConfirmations())
                .checkInterval(base.getCheckInterval())
                .timeout(base.getTimeout())
                .maxRollbackRetries(base.getMaxRollbackRetries())
                .waitForBackendAfterRollback(base.isWaitForBackendAfterRollback())
                .postRollbackWaitAttempts(base.getPostRollbackWaitAttempts())
                .postRollbackUtxoSyncDelay(base.getPostRollbackUtxoSyncDelay())
                .requiredAuthoritativeAbsences(base.getRequiredAuthoritativeAbsences());

        if (entry.getMinConfirmations() != null) {
            requireNonNegative(entry.getMinConfirmations(), "min_confirmations");
            builder.minConfirmations(entry.getMinConfirmations());
        }
        if (entry.getCheckInterval() != null) {
            builder.checkInterval(requirePositive(
                    parseDurationStrict(entry.getCheckInterval(), "check_interval"), "check_interval"));
        }
        if (entry.getTimeout() != null) {
            builder.timeout(requirePositive(
                    parseDurationStrict(entry.getTimeout(), "timeout"), "timeout"));
        }
        if (entry.getMaxRollbackRetries() != null) {
            requireNonNegative(entry.getMaxRollbackRetries(), "max_rollback_retries");
            builder.maxRollbackRetries(entry.getMaxRollbackRetries());
        }
        if (entry.getWaitForBackendAfterRollback() != null) {
            builder.waitForBackendAfterRollback(entry.getWaitForBackendAfterRollback());
        }
        if (entry.getPostRollbackWaitAttempts() != null) {
            requirePositive(entry.getPostRollbackWaitAttempts(), "post_rollback_wait_attempts");
            builder.postRollbackWaitAttempts(entry.getPostRollbackWaitAttempts());
        }
        if (entry.getPostRollbackUtxoSyncDelay() != null) {
            builder.postRollbackUtxoSyncDelay(requireNonNegative(parseDurationStrict(
                    entry.getPostRollbackUtxoSyncDelay(), "post_rollback_utxo_sync_delay"),
                    "post_rollback_utxo_sync_delay"));
        }
        if (entry.getRequiredAuthoritativeAbsences() != null) {
            requirePositive(entry.getRequiredAuthoritativeAbsences(),
                    "required_authoritative_absences");
            builder.requiredAuthoritativeAbsences(entry.getRequiredAuthoritativeAbsences());
        }

        return builder.build();
    }

    private ConfirmationConfig confirmationPreset(String preset) {
        if (preset == null || preset.trim().isEmpty()) {
            throw new IllegalArgumentException("confirmation preset cannot be blank");
        }

        switch (preset.trim().toLowerCase(Locale.ROOT)) {
            case "defaults":
                return ConfirmationConfig.defaults();
            case "devnet":
                return ConfirmationConfig.devnet();
            case "testnet":
                return ConfirmationConfig.testnet();
            case "quick":
                return ConfirmationConfig.quick();
            default:
                throw new IllegalArgumentException("Unknown confirmation preset: " + preset);
        }
    }

    /**
     * Parse a backoff strategy string to enum.
     */
    private BackoffStrategy parseBackoffStrategyStrict(String strategy) {
        return parseEnumStrict(BackoffStrategy.class, strategy, "backoff");
    }

    private <E extends Enum<E>> E parseEnumStrict(Class<E> enumType, String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown " + fieldName + ": " + value, e);
        }
    }

    private int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return value;
    }

    private Duration requirePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return duration;
    }

    private Duration requireNonNegative(Duration duration, String fieldName) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return duration;
    }

    /**
     * Parse a duration string (e.g., "1s", "500ms", "2m") to Duration.
     */
    private Duration parseDurationStrict(String durationStr, String fieldName) {
        return DurationCodec.parseLegacy(durationStr, fieldName);
    }

    /**
     * Format a Duration to a human-readable string (e.g., "1s", "500ms", "2m").
     */
    private static String formatDuration(Duration duration) {
        if (duration == null) {
            return "1s";
        }

        return DurationCodec.format(duration);
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
            throw new UnsupportedOperationException(
                "scriptTx YAML format is not supported. Use tx format instead — " +
                "all script operations are now available in the unified Tx."
            );
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
            validateSingleDocument(yaml);
            validateVersion(yaml);
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

    private static void validateSingleDocument(String yaml) throws Exception {
        try (MappingIterator<JsonNode> documents = YAML_MAPPER.readerFor(JsonNode.class).readValues(yaml)) {
            if (!documents.hasNextValue()) {
                throw new IllegalArgumentException("TxFlow YAML document is empty");
            }
            documents.nextValue();
            if (documents.hasNextValue()) {
                throw new IllegalArgumentException("Multiple YAML documents are not supported");
            }
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
            // Versionless documents predate the explicit version field and are 1.0.
            if (versionNode == null) return;
            String version = versionNode.asText();
            if (!"1.0".equals(version)) {
                throw new IllegalArgumentException("Unsupported flow document version: " + version);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate YAML version", e);
        }
    }
}
