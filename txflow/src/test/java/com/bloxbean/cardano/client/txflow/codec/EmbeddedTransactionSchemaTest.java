package com.bloxbean.cardano.client.txflow.codec;

import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationRequest;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.model.ParameterSpec;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every transaction embedded in a portable TxFlow that the TxFlow compiler accepts validates against the QuickTx
 * {@code $defs/transaction} that {@code txflow-v1alpha1.schema.json} references (ADR "Generated TxPlan Intent Schema",
 * I6 and I10). The documents are the hand-written flows of the TxFlow tests and fixtures.
 */
class EmbeddedTransactionSchemaTest {

    private static final String QUICKTX_SCHEMA_ID = "https://cardano-client.dev/schemas/quicktx/transaction/v1alpha1";
    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"[ \\t]*\\R(.*?)\"\"\"", Pattern.DOTALL);

    @Test
    void embeddedTransactionsOfCompilableFlowsValidate() throws IOException {
        String quickTxSchema = resource("/schema/quicktx-transaction-v1alpha1.schema.json");
        Schema transaction = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                        builder -> builder.schemas(Map.of(QUICKTX_SCHEMA_ID, quickTxSchema)))
                .getSchema(SchemaLocation.of(QUICKTX_SCHEMA_ID + "#/$defs/transaction"));

        int checked = 0;
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> document : flows().entrySet()) {
            TxFlow flow = compilable(document.getValue());
            if (flow == null) continue;
            for (FlowStep step : flow.getSteps()) {
                if (!step.hasTransactionTemplate()) continue;
                checked++;
                List<Error> errors = transaction.validate(step.getTransactionTemplate().toJsonNode());
                if (!errors.isEmpty())
                    failures.add(document.getKey() + " step " + step.getId() + ": "
                            + errors.stream().map(e -> e.getInstanceLocation() + " " + e.getMessage()).toList());
            }
        }

        assertThat(failures).as("embedded transactions the TxFlow compiler accepts but the schema rejects").isEmpty();
        // Guards against a vacuous pass, e.g. the corpus scan finding nothing.
        assertThat(checked).as("embedded transactions checked").isGreaterThanOrEqualTo(15);
    }

    /**
     * The flow when it parses and compiles with one valid value per declared parameter; otherwise null (it is not
     * evidence either way). The schema then checks the raw templates, placeholders included.
     */
    private static TxFlow compilable(String yaml) {
        try {
            FlowParseResult parsed = TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults());
            if (parsed.hasErrors()) return null;
            TxFlow flow = parsed.requireFlow();
            FlowBindings.Builder bindings = FlowBindings.builder();
            flow.getParameters().forEach((name, spec) -> bindings.put(name, sample(spec)));
            return new TxFlowCompiler().compile(FlowCompilationRequest.of(flow, bindings.build())).hasErrors()
                    ? null : flow;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Object sample(ParameterSpec spec) {
        return switch (spec.getType()) {
            case INTEGER -> spec.getMinimum() != null ? spec.getMinimum()
                    : spec.getMaximum() != null ? Math.min(1L, spec.getMaximum()) : 1L;
            case BOOLEAN -> true;
            case ADDRESS -> "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";
            case ASSET_UNIT -> "lovelace";
            case STRING -> "x";
        };
    }

    private static Map<String, String> flows() throws IOException {
        Map<String, String> flows = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(Path.of("src/test"))) {
            for (Path file : files.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                String name = file.toString();
                String content = Files.readString(file);
                if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                    if (content.contains("api_version: txflow")) flows.put(name, content);
                } else if (name.endsWith(".java")) {
                    Matcher m = TEXT_BLOCK.matcher(content);
                    for (int index = 0; m.find(); index++) {
                        String text;
                        try {
                            text = m.group(1).stripIndent().translateEscapes();
                        } catch (IllegalArgumentException notJavaText) {
                            continue;
                        }
                        if (text.contains("api_version: txflow")) flows.put(name + "#" + index, text);
                    }
                }
            }
        }
        return flows;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = EmbeddedTransactionSchemaTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
