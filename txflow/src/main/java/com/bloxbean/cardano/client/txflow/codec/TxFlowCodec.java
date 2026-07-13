package com.bloxbean.cardano.client.txflow.codec;

import com.bloxbean.cardano.client.quicktx.serialization.TransactionDocument;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.BackoffStrategy;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.model.ParameterSpec;
import com.bloxbean.cardano.client.txflow.model.ParameterType;
import com.bloxbean.cardano.client.txflow.model.TransactionTemplate;
import com.bloxbean.cardano.client.txflow.model.FlowOutputSelector;
import com.bloxbean.cardano.client.txflow.config.RollbackAction;
import com.bloxbean.cardano.client.txflow.config.RollbackMonitoringHorizon;
import com.bloxbean.cardano.client.txflow.config.RollbackPolicy;
import com.bloxbean.cardano.client.txflow.config.RollbackRebuildScope;
import com.bloxbean.cardano.client.txflow.config.ValidityPolicy;
import com.bloxbean.cardano.client.txflow.internal.DurationCodec;
import com.bloxbean.cardano.client.txflow.yaml.FlowDocument;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.yaml.snakeyaml.LoaderOptions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.Duration;

/**
 * Reads, classifies, and writes legacy and portable TxFlow documents.
 *
 * <p>The codec owns the flow envelope while embedded transactions retain the
 * QuickTx transaction shape. Parsing reports malformed or unsupported documents
 * through {@link FlowParseResult} diagnostics; writing fails fast with
 * {@link FlowEncodingException} when an in-memory flow has no representation in
 * the requested contract. Instances contain no per-document state and may be
 * reused.</p>
 */
public final class TxFlowCodec {
    /** API-version identifier emitted and accepted for the portable v1alpha1 contract. */
    public static final String PORTABLE_API_VERSION = "txflow.cardano-client.dev/v1alpha1";
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private TxFlowCodec() {
    }

    /**
     * Creates the standard stateless codec.
     *
     * @return reusable codec instance
     */
    public static TxFlowCodec standard() {
        return new TxFlowCodec();
    }

    /**
     * Classifies a document from its top-level shape without fully validating it.
     * Malformed, empty, and unrecognized inputs are classified as
     * {@link FlowDocumentType#UNKNOWN} rather than throwing.
     *
     * @param source YAML or JSON source
     * @return best-effort document type
     */
    public FlowDocumentType detect(String source) {
        try {
            JsonNode root = readSingleTree(source, FlowParseOptions.serverDefaults());
            if (root == null || !root.isObject()) return FlowDocumentType.UNKNOWN;
            if ("TxFlow".equals(root.path("kind").asText()) || root.has("flow")) {
                return FlowDocumentType.TX_FLOW;
            }
            if (root.has("transaction")) return FlowDocumentType.TX_PLAN;
            return FlowDocumentType.UNKNOWN;
        } catch (Exception e) {
            return FlowDocumentType.UNKNOWN;
        }
    }

    /**
     * Decodes and validates a portable or legacy TxFlow document.
     *
     * <p>Expected authoring and syntax failures are accumulated as diagnostics.
     * Use {@link FlowParseResult#requireFlow()} only after inspecting the result
     * when diagnostic reporting is important.</p>
     *
     * @param source YAML or JSON source; {@code null} produces an error diagnostic
     * @param options non-null safety limits and compatibility policy
     * @return parse outcome containing diagnostics and, on success, a flow
     */
    public FlowParseResult parse(String source, FlowParseOptions options) {
        List<FlowDiagnostic> diagnostics = new ArrayList<>();
        if (source == null) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_PARSE_NULL", "Document cannot be null", "$"));
            return new FlowParseResult(null, diagnostics);
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > options.getMaxDocumentBytes()) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_DOCUMENT_TOO_LARGE",
                    "Document exceeds configured byte limit", "$"));
            return new FlowParseResult(null, diagnostics);
        }

        try {
            FlowDocumentType type = detect(source);
            if (type != FlowDocumentType.TX_FLOW) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_DOCUMENT_KIND",
                        "Document is not a TxFlow", "$"));
                return new FlowParseResult(null, diagnostics);
            }

            JsonNode root = readSingleTree(source, options);
            if (root.has("api_version")) {
                return parsePortable(source, root, options, diagnostics);
            }
            if (source.contains("${{")) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_EXPRESSION_SYNTAX",
                        "Portable expression syntax is not allowed in a legacy document", "$"));
                return new FlowParseResult(null, diagnostics);
            }
            TxFlow legacy = FlowDocument.fromYaml(source).toFlow();
            if (!root.has("version")) {
                diagnostics.add(new FlowDiagnostic("TXFLOW_LEGACY_VERSION_DEFAULTED",
                        DiagnosticSeverity.WARNING,
                        "Versionless legacy TxFlow document was interpreted as version 1.0",
                        "$.version", null, null, null));
            }
            collectLegacyUnusedFilters(root, diagnostics);
            diagnostics.add(new FlowDiagnostic("TXFLOW_LEGACY_FORMAT", DiagnosticSeverity.WARNING,
                    "Legacy TxFlow format should be migrated to v1alpha1", "$", null, null, null));
            return new FlowParseResult(legacy, diagnostics);
        } catch (Exception e) {
            diagnostics.add(parseDiagnostic(e));
            return new FlowParseResult(null, diagnostics);
        }
    }

    private FlowParseResult parsePortable(String source, JsonNode root, FlowParseOptions options,
                                          List<FlowDiagnostic> diagnostics) throws Exception {
        String apiVersion = root.path("api_version").asText();
        if (!options.getSupportedApiVersions().contains(apiVersion)) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_UNSUPPORTED_API_VERSION",
                    "Unsupported api_version: " + apiVersion, "$.api_version"));
            return new FlowParseResult(null, diagnostics);
        }
        if (!"TxFlow".equals(root.path("kind").asText())) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_DOCUMENT_KIND",
                    "kind must be TxFlow", "$.kind"));
            return new FlowParseResult(null, diagnostics);
        }
        if (containsLegacyExpression(root)) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_EXPRESSION_SYNTAX",
                    "Legacy ${name} syntax is not allowed in a portable document", "$"));
            return new FlowParseResult(null, diagnostics);
        }

        if (options.getUnknownFieldPolicy() == UnknownFieldPolicy.WARN) {
            collectUnknownFields(root, diagnostics);
        }

        PortableDocument document = mapperFor(source, options).treeToValue(root, PortableDocument.class);
        if (document.metadata == null || document.metadata.name == null || document.metadata.name.isBlank()) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_METADATA_NAME_REQUIRED",
                    "metadata.name is required", "$.metadata.name"));
            return new FlowParseResult(null, diagnostics);
        }
        if (document.spec == null || document.spec.steps == null || document.spec.steps.isEmpty()) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_STEPS_REQUIRED",
                    "spec.steps must contain at least one step", "$.spec.steps"));
            return new FlowParseResult(null, diagnostics);
        }
        if (document.spec.steps.size() > options.getMaxSteps()) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_TOO_MANY_STEPS",
                    "spec.steps exceeds configured limit", "$.spec.steps"));
            return new FlowParseResult(null, diagnostics);
        }

        TxFlow.Builder flow = TxFlow.builder(document.metadata.name)
                .withDefinitionVersion(document.metadata.version)
                .withNetwork(document.spec.network)
                .withExecutionSettings(parseExecution(document.spec.execution));
        if (document.metadata.annotations != null) {
            document.metadata.annotations.forEach(flow::addAnnotation);
        }
        if (document.spec.parameters != null) {
            document.spec.parameters.forEach((name, parameter) -> flow.addParameter(toParameter(name, parameter)));
        }
        for (PortableStep step : document.spec.steps) {
            if (step.transaction == null || !step.transaction.isObject()) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_TRANSACTION_REQUIRED",
                        "Each step must contain one transaction", "$.spec.steps[" + step.id + "].transaction"));
                continue;
            }
            FlowStep.Builder stepBuilder = FlowStep.builder(step.id)
                    .withDescription(step.description)
                    .withTransactionTemplate(new TransactionTemplate(step.transaction));
            if (step.needs != null) step.needs.forEach(stepBuilder::needs);
            if (step.outputs != null) {
                step.outputs.forEach((name, output) -> {
                    if (output.select == null || output.select.outputIndex == null) {
                        diagnostics.add(FlowDiagnostic.error("TXFLOW_OUTPUT_SELECTOR_REQUIRED",
                                "v1alpha1 output selectors require output_index",
                                "$.spec.steps[" + step.id + "].outputs." + name));
                    } else {
                        if (!"exactly_one".equals(output.expect)) {
                            diagnostics.add(FlowDiagnostic.error("TXFLOW_OUTPUT_EXPECT_UNSUPPORTED",
                                    "v1alpha1 requires expect: exactly_one",
                                    "$.spec.steps[" + step.id + "].outputs." + name + ".expect"));
                            return;
                        }
                        stepBuilder.bindOutput(name, FlowOutputSelector.atIndex(output.select.outputIndex));
                    }
                });
            }
            flow.addStep(stepBuilder.build());
        }
        if (diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
            return new FlowParseResult(null, diagnostics);
        }
        TxFlow parsed = flow.build();
        TxFlow.ValidationResult validation = parsed.validate();
        validation.getErrors().forEach(message -> diagnostics.add(
                FlowDiagnostic.error("TXFLOW_GRAPH_INVALID", message, "$.spec.steps")));
        return new FlowParseResult(parsed, diagnostics);
    }

    /**
     * Serializes a flow using the requested syntax and schema contract.
     *
     * <p>Portable steps must contain an embedded transaction template or an
     * exactly-one-transaction QuickTx plan. Java transaction factories and
     * multi-transaction plans cannot be projected into the portable contract.
     * Legacy output is available as YAML only.</p>
     *
     * @param flow flow to serialize
     * @param options target format and schema version
     * @return serialized document
     * @throws FlowEncodingException if the target cannot represent the flow or
     *                               serialization fails
     */
    public String write(TxFlow flow, FlowWriteOptions options) {
        if (options.schemaVersion() == FlowSchemaVersion.LEGACY) {
            if (options.format() != FlowFormat.YAML) {
                throw new FlowEncodingException("Legacy TxFlow supports YAML output only");
            }
            return flow.toYaml();
        }
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("api_version", PORTABLE_API_VERSION);
            root.put("kind", "TxFlow");
            ObjectNode metadata = root.putObject("metadata");
            metadata.put("name", flow.getId());
            if (flow.getDefinitionVersion() != null) metadata.put("version", flow.getDefinitionVersion());
            if (!flow.getAnnotations().isEmpty()) metadata.set("annotations", JSON.valueToTree(flow.getAnnotations()));
            ObjectNode spec = root.putObject("spec");
            if (flow.getNetwork() != null) spec.put("network", flow.getNetwork());
            if (!flow.getParameters().isEmpty()) {
                ObjectNode parameters = spec.putObject("parameters");
                flow.getParameters().forEach((name, parameter) -> parameters.set(name, parameterNode(parameter)));
            }
            if (flow.getExecutionSettings().hasAnySetting()) {
                spec.set("execution", executionNode(flow.getExecutionSettings()));
            }
            ArrayNode steps = spec.putArray("steps");
            for (FlowStep step : flow.getSteps()) {
                ObjectNode stepNode = steps.addObject();
                stepNode.put("id", step.getId());
                if (step.getDescription() != null) stepNode.put("description", step.getDescription());
                if (!step.getNeeds().isEmpty()) stepNode.set("needs", JSON.valueToTree(step.getNeeds()));
                if (!step.getOutputBindings().isEmpty()) {
                    ObjectNode outputs = stepNode.putObject("outputs");
                    step.getOutputBindings().forEach((name, selector) -> {
                        ObjectNode output = outputs.putObject(name);
                        output.putObject("select").put("output_index", selector.getOutputIndex());
                        output.put("expect", "exactly_one");
                    });
                }
                stepNode.set("transaction", transactionNode(step));
            }
            return options.format() == FlowFormat.JSON
                    ? JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root)
                    : yamlMapper(FlowParseOptions.serverDefaults()).writeValueAsString(root);
        } catch (FlowEncodingException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowEncodingException("Failed to encode portable TxFlow", e);
        }
    }

    private JsonNode transactionNode(FlowStep step) throws Exception {
        if (step.hasTransactionTemplate()) return step.getTransactionTemplate().toJsonNode();
        if (!step.hasTxPlan() || step.getTxPlan().getTxs().size() != 1) {
            throw new FlowEncodingException("Step '" + step.getId()
                    + "' is not an exactly-one-transaction portable step");
        }
        TransactionDocument plan = YamlSerializer.getYamlMapper()
                .readValue(step.getTxPlan().toYaml(), TransactionDocument.class);
        ObjectNode transaction = JSON.createObjectNode();
        transaction.set("tx", JSON.valueToTree(plan.getTransaction().get(0).getTx()));
        if (plan.getContext() != null) transaction.set("context", JSON.valueToTree(plan.getContext()));
        return transaction;
    }

    private ObjectNode parameterNode(ParameterSpec parameter) {
        ObjectNode node = JSON.createObjectNode();
        node.put("type", parameter.getType().name().toLowerCase(Locale.ROOT));
        if (parameter.isRequired()) node.put("required", true);
        if (parameter.getDefaultValue() != null) node.set("default", JSON.valueToTree(parameter.getDefaultValue()));
        if (parameter.getMinimum() != null) node.put("minimum", parameter.getMinimum());
        if (parameter.getMaximum() != null) node.put("maximum", parameter.getMaximum());
        if (parameter.getMaxLength() != null) node.put("max_length", parameter.getMaxLength());
        if (parameter.isSensitive()) node.put("sensitive", true);
        return node;
    }

    private FlowExecutionSettings parseExecution(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return FlowExecutionSettings.empty();
        FlowExecutionSettings.FlowExecutionSettingsBuilder builder = FlowExecutionSettings.builder();
        if (node.hasNonNull("mode")) {
            builder.chainingMode(ChainingMode.valueOf(node.get("mode").asText().toUpperCase(Locale.ROOT)));
        }
        JsonNode confirmation = node.get("confirmation");
        if (confirmation != null && confirmation.isObject()) {
            ConfirmationConfig.Builder confirmationBuilder;
            String preset = confirmation.path("preset").asText("");
            if ("testnet".equalsIgnoreCase(preset)) {
                ConfirmationConfig presetConfig = ConfirmationConfig.testnet();
                confirmationBuilder = ConfirmationConfig.builder()
                        .minConfirmations(presetConfig.getMinConfirmations())
                        .checkInterval(presetConfig.getCheckInterval())
                        .timeout(presetConfig.getTimeout());
            } else {
                confirmationBuilder = ConfirmationConfig.builder();
            }
            if (confirmation.has("min_confirmations"))
                confirmationBuilder.minConfirmations(confirmation.get("min_confirmations").asInt());
            if (confirmation.has("check_interval"))
                confirmationBuilder.checkInterval(parseDuration(
                        confirmation.get("check_interval").asText(), "check_interval"));
            if (confirmation.has("timeout"))
                confirmationBuilder.timeout(parseDuration(
                        confirmation.get("timeout").asText(), "timeout"));
            builder.confirmationConfig(confirmationBuilder.build());
        }
        JsonNode rollback = node.get("rollback");
        if (rollback != null && rollback.hasNonNull("action")) {
            String action = rollback.get("action").asText().toUpperCase(Locale.ROOT);
            RollbackStrategy strategy = action.equals("FAIL") ? RollbackStrategy.FAIL_IMMEDIATELY
                    : action.equals("NOTIFY") || action.equals("WAIT_FOR_REINCLUSION")
                    ? RollbackStrategy.NOTIFY_ONLY
                    : RollbackStrategy.REBUILD_FROM_FAILED;
            builder.rollbackStrategy(strategy);
            RollbackPolicy defaults = RollbackPolicy.defaults();
            RollbackAction portableAction = action.equals("NOTIFY")
                    ? RollbackAction.WAIT_FOR_REINCLUSION
                    : action.equals("REBUILD_FROM_FAILED") || action.equals("REBUILD_ENTIRE_FLOW")
                    ? RollbackAction.RECONCILE_AND_REBUILD : RollbackAction.valueOf(action);
            builder.rollbackPolicy(new RollbackPolicy(
                    portableAction,
                    rollback.hasNonNull("monitoring_horizon")
                            ? RollbackMonitoringHorizon.valueOf(rollback.get("monitoring_horizon")
                            .asText().toUpperCase(Locale.ROOT)) : defaults.monitoringHorizon(),
                    rollback.hasNonNull("rebuild_scope")
                            ? RollbackRebuildScope.valueOf(rollback.get("rebuild_scope")
                            .asText().toUpperCase(Locale.ROOT)) : defaults.rebuildScope(),
                    rollback.path("max_recovery_cycles").asInt(defaults.maxRecoveryCycles()),
                    rollback.hasNonNull("reinclusion_window")
                            ? parseDuration(rollback.get("reinclusion_window").asText(),
                                    "reinclusion_window")
                            : defaults.reinclusionWindow(),
                    rollback.path("minimum_consistent_absence_observations")
                            .asInt(defaults.minimumConsistentAbsenceObservations())));
        }
        JsonNode retry = node.get("retry");
        if (retry != null && retry.isObject()) {
            RetryPolicy.RetryPolicyBuilder retryBuilder = RetryPolicy.builder();
            if (retry.has("max_attempts")) retryBuilder.maxAttempts(retry.get("max_attempts").asInt());
            if (retry.has("backoff")) retryBuilder.backoffStrategy(
                    BackoffStrategy.valueOf(retry.get("backoff").asText().toUpperCase(Locale.ROOT)));
            if (retry.has("initial_delay")) retryBuilder.initialDelay(
                    parseDuration(retry.get("initial_delay").asText(), "initial_delay"));
            if (retry.has("max_delay")) retryBuilder.maxDelay(
                    parseDuration(retry.get("max_delay").asText(), "max_delay"));
            if (retry.has("jitter")) retryBuilder.jitterFactor(retry.get("jitter").asDouble());
            builder.retryPolicy(retryBuilder.build());
        }
        JsonNode validity = node.get("validity");
        if (validity != null && validity.hasNonNull("window")) {
            builder.validityPolicy(new ValidityPolicy(
                    parseDuration(validity.get("window").asText(), "window"),
                    validity.path("resubmit_safety_margin").asLong(0)));
        }
        return builder.build();
    }

    private ObjectNode executionNode(FlowExecutionSettings settings) {
        ObjectNode node = JSON.createObjectNode();
        if (settings.getChainingMode() != null) node.put("mode", settings.getChainingMode().name());
        if (settings.getConfirmationConfig() != null) {
            ObjectNode confirmation = node.putObject("confirmation");
            confirmation.put("min_confirmations", settings.getConfirmationConfig().getMinConfirmations());
            confirmation.put("check_interval", formatDuration(settings.getConfirmationConfig().getCheckInterval()));
            confirmation.put("timeout", formatDuration(settings.getConfirmationConfig().getTimeout()));
        }
        if (settings.getRollbackPolicy() != null) {
            RollbackPolicy rollbackPolicy = settings.getRollbackPolicy();
            ObjectNode rollback = node.putObject("rollback");
            rollback.put("action", rollbackPolicy.action().name());
            rollback.put("monitoring_horizon", rollbackPolicy.monitoringHorizon().name());
            rollback.put("rebuild_scope", rollbackPolicy.rebuildScope().name());
            rollback.put("max_recovery_cycles", rollbackPolicy.maxRecoveryCycles());
            rollback.put("reinclusion_window", formatDuration(rollbackPolicy.reinclusionWindow()));
            rollback.put("minimum_consistent_absence_observations",
                    rollbackPolicy.minimumConsistentAbsenceObservations());
        } else if (settings.getRollbackStrategy() != null) {
            String action = settings.getRollbackStrategy() == RollbackStrategy.FAIL_IMMEDIATELY ? "FAIL"
                    : settings.getRollbackStrategy() == RollbackStrategy.NOTIFY_ONLY ? "NOTIFY"
                    : "RECONCILE_AND_REBUILD";
            node.putObject("rollback").put("action", action);
        }
        if (settings.getRetryPolicy() != null) {
            ObjectNode retry = node.putObject("retry");
            retry.put("max_attempts", settings.getRetryPolicy().getMaxAttempts());
            retry.put("backoff", settings.getRetryPolicy().getBackoffStrategy().name().toLowerCase(Locale.ROOT));
            retry.put("initial_delay", formatDuration(settings.getRetryPolicy().getInitialDelay()));
            retry.put("max_delay", formatDuration(settings.getRetryPolicy().getMaxDelay()));
            retry.put("jitter", settings.getRetryPolicy().getJitterFactor());
        }
        if (settings.getValidityPolicy() != null) {
            ObjectNode validity = node.putObject("validity");
            validity.put("window", formatDuration(settings.getValidityPolicy().window()));
            validity.put("resubmit_safety_margin",
                    settings.getValidityPolicy().resubmitSafetyMargin());
        }
        return node;
    }

    private Duration parseDuration(String value, String fieldName) {
        return DurationCodec.parsePortable(value, fieldName);
    }

    private String formatDuration(Duration value) {
        return DurationCodec.format(value);
    }

    private ParameterSpec toParameter(String name, PortableParameter parameter) {
        ParameterType type = ParameterType.valueOf(parameter.type.toUpperCase(Locale.ROOT));
        ParameterSpec.Builder builder;
        switch (type) {
            case INTEGER: builder = ParameterSpec.integer(name); break;
            case ADDRESS: builder = ParameterSpec.address(name); break;
            case BOOLEAN: builder = ParameterSpec.booleanParameter(name); break;
            case ASSET_UNIT: builder = ParameterSpec.assetUnit(name); break;
            default: builder = ParameterSpec.string(name);
        }
        if (parameter.required) builder.required();
        if (parameter.defaultValue != null) builder.defaultValue(parameter.defaultValue);
        if (parameter.minimum != null) builder.minimum(parameter.minimum);
        if (parameter.maximum != null) builder.maximum(parameter.maximum);
        if (parameter.maxLength != null) builder.maxLength(parameter.maxLength);
        if (parameter.sensitive) builder.sensitive();
        return builder.build();
    }

    private boolean containsLegacyExpression(JsonNode node) {
        if (node.isTextual()) {
            String value = node.asText();
            return value.matches(".*\\$\\{(?!\\{).*" );
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) if (containsLegacyExpression(child)) return true;
        }
        return false;
    }

    private FlowDiagnostic parseDiagnostic(Exception exception) {
        Throwable current = exception;
        while (current != null && !(current instanceof JsonProcessingException)) current = current.getCause();
        if (current instanceof JsonProcessingException) {
            JsonProcessingException json = (JsonProcessingException) current;
            return new FlowDiagnostic("TXFLOW_PARSE_ERROR", DiagnosticSeverity.ERROR,
                    json.getOriginalMessage(), "$", json.getLocation().getLineNr(),
                    json.getLocation().getColumnNr(), null);
        }
        return FlowDiagnostic.error("TXFLOW_PARSE_ERROR", exception.getMessage(), "$" );
    }

    private ObjectMapper mapperFor(String source, FlowParseOptions options) {
        if (!source.stripLeading().startsWith("{")) return yamlMapper(options);
        if (options.getUnknownFieldPolicy() == UnknownFieldPolicy.REJECT) return JSON;
        return JSON.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private JsonNode readSingleTree(String source, FlowParseOptions options) throws Exception {
        ObjectMapper mapper = mapperFor(source, options);
        try (MappingIterator<JsonNode> documents = mapper.readerFor(JsonNode.class).readValues(source)) {
            if (!documents.hasNextValue()) return null;
            JsonNode root = documents.nextValue();
            if (documents.hasNextValue()) {
                throw new IllegalArgumentException("Multiple documents are not allowed");
            }
            return root;
        }
    }

    private ObjectMapper yamlMapper(FlowParseOptions options) {
        LoaderOptions loader = new LoaderOptions();
        loader.setAllowDuplicateKeys(false);
        loader.setMaxAliasesForCollections(options.getMaxAliases());
        loader.setNestingDepthLimit(options.getMaxNestingDepth());
        loader.setCodePointLimit(options.getMaxDocumentBytes());
        YAMLFactory factory = YAMLFactory.builder().loaderOptions(loader).build();
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        ObjectMapper mapper = new ObjectMapper(factory);
        if (options.getUnknownFieldPolicy() == UnknownFieldPolicy.REJECT) {
            mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        } else {
            mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    private void collectUnknownFields(JsonNode root, List<FlowDiagnostic> diagnostics) {
        warnUnknown(root, "$", diagnostics, "api_version", "kind", "metadata", "spec");
        JsonNode metadata = root.path("metadata");
        warnUnknown(metadata, "$.metadata", diagnostics, "name", "version", "annotations");
        JsonNode spec = root.path("spec");
        warnUnknown(spec, "$.spec", diagnostics, "network", "parameters", "execution", "steps");
        spec.path("parameters").fields().forEachRemaining(entry -> warnUnknown(entry.getValue(),
                "$.spec.parameters." + entry.getKey(), diagnostics,
                "type", "required", "default", "minimum", "maximum", "max_length", "sensitive"));
        JsonNode execution = spec.path("execution");
        warnUnknown(execution, "$.spec.execution", diagnostics,
                "mode", "confirmation", "rollback", "retry", "validity");
        warnUnknown(execution.path("confirmation"), "$.spec.execution.confirmation", diagnostics,
                "preset", "min_confirmations", "check_interval", "timeout");
        warnUnknown(execution.path("rollback"), "$.spec.execution.rollback", diagnostics,
                "action", "monitoring_horizon", "rebuild_scope", "max_recovery_cycles",
                "reinclusion_window", "minimum_consistent_absence_observations");
        warnUnknown(execution.path("retry"), "$.spec.execution.retry", diagnostics,
                "max_attempts", "backoff", "initial_delay", "max_delay", "jitter");
        warnUnknown(execution.path("validity"), "$.spec.execution.validity", diagnostics,
                "window", "resubmit_safety_margin");
        JsonNode steps = spec.path("steps");
        for (int i = 0; i < steps.size(); i++) {
            JsonNode step = steps.get(i);
            String path = "$.spec.steps[" + i + "]";
            warnUnknown(step, path, diagnostics,
                    "id", "description", "needs", "transaction", "outputs", "retry");
            step.path("outputs").fields().forEachRemaining(entry -> {
                String outputPath = path + ".outputs." + entry.getKey();
                warnUnknown(entry.getValue(), outputPath, diagnostics, "select", "expect");
                warnUnknown(entry.getValue().path("select"), outputPath + ".select", diagnostics,
                        "output_index");
            });
        }
    }

    private void collectLegacyUnusedFilters(JsonNode root, List<FlowDiagnostic> diagnostics) {
        JsonNode steps = root.path("flow").path("steps");
        for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
            JsonNode dependencies = steps.get(stepIndex).path("step").path("depends_on");
            for (int dependencyIndex = 0; dependencyIndex < dependencies.size(); dependencyIndex++) {
                if (dependencies.get(dependencyIndex).has("filter")) {
                    diagnostics.add(new FlowDiagnostic("TXFLOW_LEGACY_UNUSED_FILTER",
                            DiagnosticSeverity.WARNING,
                            "Legacy dependency filter cannot be reconstructed and was ignored",
                            "$.flow.steps[" + stepIndex + "].step.depends_on["
                                    + dependencyIndex + "].filter",
                            null, null, null));
                }
            }
        }
    }

    private void warnUnknown(JsonNode node, String path, List<FlowDiagnostic> diagnostics,
                             String... knownFields) {
        if (!node.isObject()) return;
        java.util.Set<String> known = java.util.Set.of(knownFields);
        node.fieldNames().forEachRemaining(field -> {
            if (!known.contains(field)) {
                diagnostics.add(new FlowDiagnostic("TXFLOW_UNKNOWN_FIELD", DiagnosticSeverity.WARNING,
                        "Unknown field: " + field, path + "." + field, null, null, null));
            }
        });
    }

    static final class PortableDocument {
        @JsonProperty("api_version") public String apiVersion;
        public String kind;
        public Metadata metadata;
        public Spec spec;
    }

    static final class Metadata {
        public String name;
        public String version;
        public Map<String, String> annotations = new LinkedHashMap<>();
    }

    static final class Spec {
        public String network;
        public JsonNode execution;
        public Map<String, PortableParameter> parameters = new LinkedHashMap<>();
        public List<PortableStep> steps = new ArrayList<>();
    }

    static final class PortableParameter {
        public String type;
        public boolean required;
        @JsonProperty("default") public Object defaultValue;
        public Long minimum;
        public Long maximum;
        @JsonProperty("max_length") public Integer maxLength;
        public boolean sensitive;
    }

    static final class PortableStep {
        public String id;
        public String description;
        public List<String> needs = new ArrayList<>();
        public JsonNode transaction;
        public Map<String, PortableOutput> outputs = new LinkedHashMap<>();
    }

    static final class PortableOutput {
        public PortableSelect select;
        public String expect;
    }

    static final class PortableSelect {
        @JsonProperty("output_index") public Integer outputIndex;
    }
}
