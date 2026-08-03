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
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
    private static final Pattern LEGACY_EXPRESSION = Pattern.compile("\\$\\{(?!\\{)");
    private static final Set<String> CONFIRMATION_PRESETS =
            Set.of("defaults", "devnet", "testnet", "quick");
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
            return detect(readSingleTree(source, FlowParseOptions.serverDefaults()));
        } catch (Exception e) {
            return FlowDocumentType.UNKNOWN;
        }
    }

    private FlowDocumentType detect(JsonNode root) {
        if (root == null || !root.isObject()) return FlowDocumentType.UNKNOWN;
        if ("TxFlow".equals(root.path("kind").asText()) || root.has("flow")) {
            return FlowDocumentType.TX_FLOW;
        }
        if (root.has("transaction")) return FlowDocumentType.TX_PLAN;
        return FlowDocumentType.UNKNOWN;
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
            JsonNode root = readSingleTree(source, options);
            FlowDocumentType type = detect(root);
            if (type != FlowDocumentType.TX_FLOW) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_DOCUMENT_KIND",
                        "Document is not a TxFlow", "$"));
                return new FlowParseResult(null, diagnostics);
            }

            if (root.has("api_version")) {
                return parsePortable(source, root, options, diagnostics);
            }
            if (containsPortableExpression(root)) {
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
        validatePortableShape(root, diagnostics);
        if (diagnostics.stream().anyMatch(diagnostic ->
                diagnostic.severity() == DiagnosticSeverity.ERROR)) {
            return new FlowParseResult(null, diagnostics);
        }
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

        if (options.getUnknownFieldPolicy() != UnknownFieldPolicy.IGNORE) {
            collectUnknownFields(root, diagnostics, options.getUnknownFieldPolicy());
        }
        if (diagnostics.stream().anyMatch(diagnostic ->
                diagnostic.severity() == DiagnosticSeverity.ERROR)) {
            return new FlowParseResult(null, diagnostics);
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
                .withDescription(document.metadata.description)
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
     * Legacy step dependencies, step-level retry overrides, and legacy variable
     * maps are also rejected because silently dropping them would change
     * execution semantics. Legacy output is available as YAML only.</p>
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
            try {
                return flow.toYaml();
            } catch (RuntimeException failure) {
                throw new FlowEncodingException("Failed to encode legacy TxFlow", failure);
            }
        }
        validatePortableSemantics(flow);
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("api_version", PORTABLE_API_VERSION);
            root.put("kind", "TxFlow");
            ObjectNode metadata = root.putObject("metadata");
            metadata.put("name", flow.getId());
            if (flow.getDescription() != null) metadata.put("description", flow.getDescription());
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

    private void validatePortableSemantics(TxFlow flow) {
        List<FlowDiagnostic> diagnostics = PortableFlowValidator.validate(flow);
        if (!diagnostics.isEmpty()) {
            FlowDiagnostic first = diagnostics.get(0);
            throw new FlowEncodingException(first.code() + " at " + first.documentPath()
                    + ": " + first.message());
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
            ConfirmationConfig preset = confirmationPreset(
                    confirmation.path("preset").asText("defaults"));
            ConfirmationConfig.Builder confirmationBuilder = copyPortableConfirmation(preset);
            if (confirmation.hasNonNull("min_confirmations"))
                confirmationBuilder.minConfirmations(confirmation.get("min_confirmations").intValue());
            if (confirmation.hasNonNull("check_interval"))
                confirmationBuilder.checkInterval(parseDuration(
                        confirmation.get("check_interval").asText(), "check_interval"));
            if (confirmation.hasNonNull("timeout"))
                confirmationBuilder.timeout(parseDuration(
                        confirmation.get("timeout").asText(), "timeout"));
            builder.confirmationConfig(confirmationBuilder.build());
        }
        JsonNode rollback = node.get("rollback");
        if (rollback != null && rollback.hasNonNull("action")) {
            String action = rollback.get("action").asText().toUpperCase(Locale.ROOT);
            RollbackPolicy defaults = RollbackPolicy.defaults();
            RollbackPolicy policy = new RollbackPolicy(
                    RollbackAction.valueOf(action),
                    rollback.hasNonNull("monitoring_horizon")
                            ? RollbackMonitoringHorizon.valueOf(rollback.get("monitoring_horizon")
                            .asText().toUpperCase(Locale.ROOT)) : defaults.monitoringHorizon(),
                    rollback.hasNonNull("rebuild_scope")
                            ? RollbackRebuildScope.valueOf(rollback.get("rebuild_scope")
                            .asText().toUpperCase(Locale.ROOT)) : defaults.rebuildScope(),
                    rollback.hasNonNull("max_recovery_cycles")
                            ? rollback.get("max_recovery_cycles").intValue()
                            : defaults.maxRecoveryCycles(),
                    rollback.hasNonNull("reinclusion_window")
                            ? parseDuration(rollback.get("reinclusion_window").asText(),
                                    "reinclusion_window")
                            : defaults.reinclusionWindow(),
                    rollback.hasNonNull("minimum_consistent_absence_observations")
                            ? rollback.get("minimum_consistent_absence_observations").intValue()
                            : defaults.minimumConsistentAbsenceObservations());
            builder.rollbackPolicy(policy);
        }
        JsonNode retry = node.get("retry");
        if (retry != null && retry.isObject()) {
            RetryPolicy.RetryPolicyBuilder retryBuilder = RetryPolicy.builder();
            if (retry.hasNonNull("max_attempts"))
                retryBuilder.maxAttempts(retry.get("max_attempts").intValue());
            if (retry.hasNonNull("backoff")) retryBuilder.backoffStrategy(
                    BackoffStrategy.valueOf(retry.get("backoff").asText().toUpperCase(Locale.ROOT)));
            if (retry.hasNonNull("initial_delay")) retryBuilder.initialDelay(
                    parseDuration(retry.get("initial_delay").asText(), "initial_delay"));
            if (retry.hasNonNull("max_delay")) retryBuilder.maxDelay(
                    parseDuration(retry.get("max_delay").asText(), "max_delay"));
            if (retry.hasNonNull("jitter"))
                retryBuilder.jitterFactor(retry.get("jitter").doubleValue());
            builder.retryPolicy(retryBuilder.build());
        }
        JsonNode validity = node.get("validity");
        if (validity != null && validity.hasNonNull("window")) {
            builder.validityPolicy(new ValidityPolicy(
                    parseDuration(validity.get("window").asText(), "window"),
                    validity.hasNonNull("resubmit_safety_margin")
                            ? validity.get("resubmit_safety_margin").longValue() : 0));
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
        RollbackPolicy rollbackPolicy = settings.getRollbackPolicy();
        if (rollbackPolicy != null) {
            ObjectNode rollback = node.putObject("rollback");
            rollback.put("action", rollbackPolicy.action().name());
            rollback.put("monitoring_horizon", rollbackPolicy.monitoringHorizon().name());
            rollback.put("rebuild_scope", rollbackPolicy.rebuildScope().name());
            rollback.put("max_recovery_cycles", rollbackPolicy.maxRecoveryCycles());
            rollback.put("reinclusion_window", formatDuration(rollbackPolicy.reinclusionWindow()));
            rollback.put("minimum_consistent_absence_observations",
                    rollbackPolicy.minimumConsistentAbsenceObservations());
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

    private ConfirmationConfig confirmationPreset(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "devnet": return ConfirmationConfig.devnet();
            case "testnet": return ConfirmationConfig.testnet();
            case "quick": return ConfirmationConfig.quick();
            case "defaults": return ConfirmationConfig.defaults();
            default: throw new IllegalArgumentException("Unsupported confirmation preset: " + name);
        }
    }

    private ConfirmationConfig.Builder copyPortableConfirmation(ConfirmationConfig value) {
        return ConfirmationConfig.builder()
                .minConfirmations(value.getMinConfirmations())
                .checkInterval(value.getCheckInterval())
                .timeout(value.getTimeout());
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
            return LEGACY_EXPRESSION.matcher(node.asText()).find();
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) if (containsLegacyExpression(child)) return true;
        }
        return false;
    }

    private boolean containsPortableExpression(JsonNode node) {
        if (node.isTextual()) return node.asText().contains("${{");
        if (node.isContainerNode()) {
            for (JsonNode child : node) if (containsPortableExpression(child)) return true;
        }
        return false;
    }

    private void validatePortableShape(JsonNode root,
                                       List<FlowDiagnostic> diagnostics) {
        requireField(root, "api_version", "$.api_version", diagnostics);
        validateText(root, "api_version", "$.api_version", diagnostics);
        requireField(root, "kind", "$.kind", diagnostics);
        validateText(root, "kind", "$.kind", diagnostics);

        requireField(root, "metadata", "$.metadata", diagnostics);
        JsonNode metadata = validateObject(root, "metadata", "$.metadata", diagnostics);
        if (metadata != null) {
            requireField(metadata, "name", "$.metadata.name", diagnostics);
            validateText(metadata, "name", "$.metadata.name", diagnostics);
            validateText(metadata, "description", "$.metadata.description", diagnostics);
            validateText(metadata, "version", "$.metadata.version", diagnostics);
            JsonNode annotations = validateObject(metadata, "annotations",
                    "$.metadata.annotations", diagnostics);
            if (annotations != null) {
                annotations.fields().forEachRemaining(entry -> {
                    if (!entry.getValue().isTextual()) {
                        addTypeError(diagnostics,
                                "$.metadata.annotations." + entry.getKey(), "string");
                    }
                });
            }
        }

        requireField(root, "spec", "$.spec", diagnostics);
        JsonNode spec = validateObject(root, "spec", "$.spec", diagnostics);
        if (spec == null) return;
        validateText(spec, "network", "$.spec.network", diagnostics);
        validateParameters(spec, diagnostics);
        JsonNode execution = spec.get("execution");
        if (execution != null) validateExecutionShape(execution, diagnostics);
        validateSteps(spec, diagnostics);
    }

    private void validateParameters(JsonNode spec,
                                    List<FlowDiagnostic> diagnostics) {
        JsonNode parameters = validateObject(spec, "parameters", "$.spec.parameters", diagnostics);
        if (parameters == null) return;
        parameters.fields().forEachRemaining(entry -> {
            String path = "$.spec.parameters." + entry.getKey();
            JsonNode parameter = entry.getValue();
            if (!parameter.isObject()) {
                addTypeError(diagnostics, path, "object");
                return;
            }
            requireField(parameter, "type", path + ".type", diagnostics);
            validateEnum(parameter, "type", path + ".type", diagnostics,
                    Set.of("string", "integer", "boolean", "address", "asset_unit"), false);
            validateBoolean(parameter, "required", path + ".required", diagnostics);
            validateInteger(parameter, "minimum", path + ".minimum", diagnostics, true);
            validateInteger(parameter, "maximum", path + ".maximum", diagnostics, true);
            validateInteger(parameter, "max_length", path + ".max_length", diagnostics, false);
            validateBoolean(parameter, "sensitive", path + ".sensitive", diagnostics);
        });
    }

    private void validateSteps(JsonNode spec,
                               List<FlowDiagnostic> diagnostics) {
        requireField(spec, "steps", "$.spec.steps", diagnostics);
        JsonNode steps = spec.get("steps");
        if (steps == null) return;
        if (!steps.isArray()) {
            addTypeError(diagnostics, "$.spec.steps", "array");
            return;
        }
        for (int index = 0; index < steps.size(); index++) {
            JsonNode step = steps.get(index);
            String path = "$.spec.steps[" + index + "]";
            if (!step.isObject()) {
                addTypeError(diagnostics, path, "object");
                continue;
            }
            requireField(step, "id", path + ".id", diagnostics);
            validateText(step, "id", path + ".id", diagnostics);
            validateText(step, "description", path + ".description", diagnostics);
            validateStringArray(step, "needs", path + ".needs", diagnostics);
            requireField(step, "transaction", path + ".transaction", diagnostics);
            validateObject(step, "transaction", path + ".transaction", diagnostics);
            validateOutputs(step, path, diagnostics);
        }
    }

    private void validateOutputs(JsonNode step, String stepPath,
                                 List<FlowDiagnostic> diagnostics) {
        JsonNode outputs = validateObject(step, "outputs", stepPath + ".outputs", diagnostics);
        if (outputs == null) return;
        outputs.fields().forEachRemaining(entry -> {
            String path = stepPath + ".outputs." + entry.getKey();
            JsonNode output = entry.getValue();
            if (!output.isObject()) {
                addTypeError(diagnostics, path, "object");
                return;
            }
            requireField(output, "select", path + ".select", diagnostics);
            JsonNode select = validateObject(output, "select", path + ".select", diagnostics);
            if (select != null) {
                requireField(select, "output_index", path + ".select.output_index", diagnostics);
                validateInteger(select, "output_index", path + ".select.output_index",
                        diagnostics, false);
            }
            requireField(output, "expect", path + ".expect", diagnostics);
            validateEnum(output, "expect", path + ".expect", diagnostics,
                    Set.of("exactly_one"), false);
        });
    }

    private void validateExecutionShape(JsonNode execution,
                                        List<FlowDiagnostic> diagnostics) {
        if (!execution.isObject()) {
            addTypeError(diagnostics, "$.spec.execution", "object");
            return;
        }
        validateEnum(execution, "mode", "$.spec.execution.mode", diagnostics,
                Set.of("SEQUENTIAL", "PIPELINED", "BATCH"), false);

        JsonNode confirmation = validateObject(execution, "confirmation",
                "$.spec.execution.confirmation", diagnostics);
        if (confirmation != null) {
            validateText(confirmation, "preset", "$.spec.execution.confirmation.preset", diagnostics);
            JsonNode preset = confirmation.get("preset");
            if (preset != null && preset.isTextual()
                    && !CONFIRMATION_PRESETS.contains(preset.asText())) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_CONFIRMATION_PRESET_UNSUPPORTED",
                        "Unsupported confirmation preset: " + preset.asText(),
                        "$.spec.execution.confirmation.preset"));
            }
            validateInteger(confirmation, "min_confirmations",
                    "$.spec.execution.confirmation.min_confirmations", diagnostics, false);
            validateDuration(confirmation, "check_interval",
                    "$.spec.execution.confirmation.check_interval", diagnostics);
            validateDuration(confirmation, "timeout",
                    "$.spec.execution.confirmation.timeout", diagnostics);
        }

        JsonNode rollback = validateObject(execution, "rollback",
                "$.spec.execution.rollback", diagnostics);
        if (rollback != null) {
            requireField(rollback, "action", "$.spec.execution.rollback.action", diagnostics);
            validateEnum(rollback, "action", "$.spec.execution.rollback.action", diagnostics,
                    enumNames(RollbackAction.values()), false);
            validateEnum(rollback, "monitoring_horizon",
                    "$.spec.execution.rollback.monitoring_horizon", diagnostics,
                    enumNames(RollbackMonitoringHorizon.values()), false);
            validateEnum(rollback, "rebuild_scope",
                    "$.spec.execution.rollback.rebuild_scope", diagnostics,
                    enumNames(RollbackRebuildScope.values()), false);
            validateInteger(rollback, "max_recovery_cycles",
                    "$.spec.execution.rollback.max_recovery_cycles", diagnostics, false);
            validateDuration(rollback, "reinclusion_window",
                    "$.spec.execution.rollback.reinclusion_window", diagnostics);
            validateInteger(rollback, "minimum_consistent_absence_observations",
                    "$.spec.execution.rollback.minimum_consistent_absence_observations",
                    diagnostics, false);
        }

        JsonNode retry = validateObject(execution, "retry", "$.spec.execution.retry", diagnostics);
        if (retry != null) {
            validateInteger(retry, "max_attempts", "$.spec.execution.retry.max_attempts",
                    diagnostics, false);
            validateEnum(retry, "backoff", "$.spec.execution.retry.backoff", diagnostics,
                    Set.of("fixed", "linear", "exponential",
                            "FIXED", "LINEAR", "EXPONENTIAL"), false);
            validateDuration(retry, "initial_delay", "$.spec.execution.retry.initial_delay",
                    diagnostics);
            validateDuration(retry, "max_delay", "$.spec.execution.retry.max_delay", diagnostics);
            JsonNode jitter = retry.get("jitter");
            if (jitter != null && (!jitter.isNumber() || !Double.isFinite(jitter.doubleValue()))) {
                addTypeError(diagnostics, "$.spec.execution.retry.jitter", "finite number");
            }
        }

        JsonNode validity = validateObject(execution, "validity",
                "$.spec.execution.validity", diagnostics);
        if (validity != null) {
            requireField(validity, "window", "$.spec.execution.validity.window", diagnostics);
            validateDuration(validity, "window", "$.spec.execution.validity.window", diagnostics);
            validateInteger(validity, "resubmit_safety_margin",
                    "$.spec.execution.validity.resubmit_safety_margin", diagnostics, true);
        }
    }

    private JsonNode validateObject(JsonNode parent, String field, String path,
                                    List<FlowDiagnostic> diagnostics) {
        JsonNode value = parent.get(field);
        if (value == null) return null;
        if (!value.isObject()) {
            addTypeError(diagnostics, path, "object");
            return null;
        }
        return value;
    }

    private void validateText(JsonNode parent, String field, String path,
                              List<FlowDiagnostic> diagnostics) {
        JsonNode value = parent.get(field);
        if (value != null && !value.isTextual()) addTypeError(diagnostics, path, "string");
    }

    private void validateBoolean(JsonNode parent, String field, String path,
                                 List<FlowDiagnostic> diagnostics) {
        JsonNode value = parent.get(field);
        if (value != null && !value.isBoolean()) addTypeError(diagnostics, path, "boolean");
    }

    private void validateStringArray(JsonNode parent, String field, String path,
                                     List<FlowDiagnostic> diagnostics) {
        JsonNode value = parent.get(field);
        if (value == null) return;
        if (!value.isArray()) {
            addTypeError(diagnostics, path, "array");
            return;
        }
        for (int index = 0; index < value.size(); index++) {
            if (!value.get(index).isTextual()) {
                addTypeError(diagnostics, path + "[" + index + "]", "string");
            }
        }
    }

    private void requireField(JsonNode parent, String field, String path,
                              List<FlowDiagnostic> diagnostics) {
        if (!parent.has(field)) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_FIELD_VALUE",
                    "Required field is missing", path));
        }
    }

    private void validateInteger(JsonNode parent, String field, String path,
                                 List<FlowDiagnostic> diagnostics, boolean allowLong) {
        JsonNode value = parent.get(field);
        if (value != null && (!value.isIntegralNumber()
                || (allowLong ? !value.canConvertToLong() : !value.canConvertToInt()))) {
            addTypeError(diagnostics, path, allowLong ? "64-bit integer" : "32-bit integer");
        }
    }

    private void validateDuration(JsonNode parent, String field, String path,
                                  List<FlowDiagnostic> diagnostics) {
        JsonNode value = parent.get(field);
        if (value == null) return;
        if (!value.isTextual()) {
            addTypeError(diagnostics, path, "duration string");
            return;
        }
        try {
            parseDuration(value.asText(), field);
        } catch (RuntimeException failure) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_FIELD_VALUE", failure.getMessage(), path));
        }
    }

    private void validateEnum(JsonNode parent, String field, String path,
                              List<FlowDiagnostic> diagnostics, Set<String> allowed,
                              boolean caseInsensitive) {
        JsonNode value = parent.get(field);
        if (value == null) return;
        if (!value.isTextual()) {
            addTypeError(diagnostics, path, "string");
            return;
        }
        String candidate = caseInsensitive
                ? value.asText().toUpperCase(Locale.ROOT) : value.asText();
        Set<String> comparison = caseInsensitive
                ? allowed.stream().map(item -> item.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet()) : allowed;
        if (!comparison.contains(candidate)) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_FIELD_VALUE",
                    "Unsupported value '" + value.asText() + "'", path));
        }
    }

    private Set<String> enumNames(Enum<?>[] values) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (Enum<?> value : values) names.add(value.name());
        return names;
    }

    private void addTypeError(List<FlowDiagnostic> diagnostics, String path, String expected) {
        diagnostics.add(FlowDiagnostic.error("TXFLOW_FIELD_TYPE",
                "Expected " + expected, path));
    }

    private FlowDiagnostic parseDiagnostic(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof JsonProcessingException) {
                JsonProcessingException json = (JsonProcessingException) current;
                return new FlowDiagnostic("TXFLOW_PARSE_ERROR", DiagnosticSeverity.ERROR,
                        json.getOriginalMessage(), "$", json.getLocation().getLineNr(),
                        json.getLocation().getColumnNr(), null);
            }
            if (current instanceof MarkedYAMLException) {
                MarkedYAMLException yaml = (MarkedYAMLException) current;
                Mark mark = yaml.getProblemMark();
                return new FlowDiagnostic("TXFLOW_PARSE_ERROR", DiagnosticSeverity.ERROR,
                        yaml.getProblem() != null ? yaml.getProblem() : yaml.getMessage(), "$",
                        mark != null ? mark.getLine() + 1 : null,
                        mark != null ? mark.getColumn() + 1 : null, null);
            }
            current = current.getCause();
        }
        return FlowDiagnostic.error("TXFLOW_PARSE_ERROR", exception.getMessage(), "$" );
    }

    private ObjectMapper mapperFor(String source, FlowParseOptions options) {
        if (!source.stripLeading().startsWith("{")) return yamlMapper(options);
        if (options.getUnknownFieldPolicy() == UnknownFieldPolicy.REJECT) return JSON;
        return JSON.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private JsonNode readSingleTree(String source, FlowParseOptions options) throws Exception {
        if (!source.stripLeading().startsWith("{")) validateYamlSafety(source, options);
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

    private void validateYamlSafety(String source, FlowParseOptions options) {
        int documentCount = 0;
        for (org.yaml.snakeyaml.nodes.Node ignored
                : new Yaml(loaderOptions(options)).composeAll(new StringReader(source))) {
            if (++documentCount > 1) {
                throw new IllegalArgumentException("Multiple documents are not allowed");
            }
        }
    }

    private ObjectMapper yamlMapper(FlowParseOptions options) {
        LoaderOptions loader = loaderOptions(options);
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

    private LoaderOptions loaderOptions(FlowParseOptions options) {
        LoaderOptions loader = new LoaderOptions();
        loader.setAllowDuplicateKeys(false);
        loader.setMaxAliasesForCollections(options.getMaxAliases());
        loader.setNestingDepthLimit(options.getMaxNestingDepth());
        loader.setCodePointLimit(options.getMaxDocumentBytes());
        return loader;
    }

    private void collectUnknownFields(JsonNode root, List<FlowDiagnostic> diagnostics,
                                      UnknownFieldPolicy policy) {
        warnUnknown(root, "$", diagnostics, policy, "api_version", "kind", "metadata", "spec");
        JsonNode metadata = root.path("metadata");
        warnUnknown(metadata, "$.metadata", diagnostics, policy,
                "name", "description", "version", "annotations");
        JsonNode spec = root.path("spec");
        warnUnknown(spec, "$.spec", diagnostics, policy,
                "network", "parameters", "execution", "steps");
        spec.path("parameters").fields().forEachRemaining(entry -> warnUnknown(entry.getValue(),
                "$.spec.parameters." + entry.getKey(), diagnostics, policy,
                "type", "required", "default", "minimum", "maximum", "max_length", "sensitive"));
        JsonNode execution = spec.path("execution");
        warnUnknown(execution, "$.spec.execution", diagnostics, policy,
                "mode", "confirmation", "rollback", "retry", "validity");
        warnUnknown(execution.path("confirmation"), "$.spec.execution.confirmation", diagnostics, policy,
                "preset", "min_confirmations", "check_interval", "timeout");
        warnUnknown(execution.path("rollback"), "$.spec.execution.rollback", diagnostics, policy,
                "action", "monitoring_horizon", "rebuild_scope", "max_recovery_cycles",
                "reinclusion_window", "minimum_consistent_absence_observations");
        warnUnknown(execution.path("retry"), "$.spec.execution.retry", diagnostics, policy,
                "max_attempts", "backoff", "initial_delay", "max_delay", "jitter");
        warnUnknown(execution.path("validity"), "$.spec.execution.validity", diagnostics, policy,
                "window", "resubmit_safety_margin");
        JsonNode steps = spec.path("steps");
        for (int i = 0; i < steps.size(); i++) {
            JsonNode step = steps.get(i);
            String path = "$.spec.steps[" + i + "]";
            warnUnknown(step, path, diagnostics, policy,
                    "id", "description", "needs", "transaction", "outputs");
            step.path("outputs").fields().forEachRemaining(entry -> {
                String outputPath = path + ".outputs." + entry.getKey();
                warnUnknown(entry.getValue(), outputPath, diagnostics, policy, "select", "expect");
                warnUnknown(entry.getValue().path("select"), outputPath + ".select", diagnostics, policy,
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
                             UnknownFieldPolicy policy, String... knownFields) {
        if (!node.isObject()) return;
        java.util.Set<String> known = java.util.Set.of(knownFields);
        node.fieldNames().forEachRemaining(field -> {
            if (!known.contains(field)) {
                diagnostics.add(new FlowDiagnostic("TXFLOW_UNKNOWN_FIELD",
                        policy == UnknownFieldPolicy.REJECT
                                ? DiagnosticSeverity.ERROR : DiagnosticSeverity.WARNING,
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
        public String description;
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
