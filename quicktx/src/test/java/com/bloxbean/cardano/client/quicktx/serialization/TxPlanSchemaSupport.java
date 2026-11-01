package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared helpers for the TxPlan schema tests: the published schema, a validator, the acceptance oracle, and a corpus of
 * TxPlan documents that were written by hand (test text blocks, the TxPlan docs) or by the canonical writer.
 */
final class TxPlanSchemaSupport {

    static final String SCHEMA_ID = "https://cardano-client.dev/schemas/txplan/v1alpha1";
    static final ObjectMapper YAML = YamlSerializer.getYamlMapper();

    private static final Schema SCHEMA = loadSchema();
    private static final TxPlanCodec CODEC = TxPlanCodec.builder().build();
    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"[ \\t]*\\R(.*?)\"\"\"", Pattern.DOTALL);
    private static final Pattern YAML_FENCE = Pattern.compile("```ya?ml[ \\t]*\\R(.*?)```", Pattern.DOTALL);

    private TxPlanSchemaSupport() {
    }

    /** The committed schema resource exactly as published. */
    static String schemaResource() {
        try (InputStream in = TxPlanSchemaSupport.class.getResourceAsStream(TxPlanSchemaGenerator.SCHEMA_RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing " + TxPlanSchemaGenerator.SCHEMA_RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Validation errors of a TxPlan document (as written, before variable resolution) against the root schema. */
    static List<String> validate(JsonNode document) {
        List<Error> errors = SCHEMA.validate(document);
        return errors.stream().map(e -> e.getInstanceLocation() + ": " + e.getMessage()).collect(Collectors.toList());
    }

    static List<String> validate(String yaml) {
        return validate(tree(yaml));
    }

    static JsonNode tree(String yaml) {
        try {
            return YAML.readTree(yaml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The acceptance oracle for "the schema is never stricter than the runtime" (ADR I10): a strict reader,
     * {@link TxPlanCodec} or {@link TxPlan#from(String)}, accepts the document. Documents with extensions are left to
     * their extension's own rules.
     */
    static boolean acceptedByRuntime(String yaml) {
        JsonNode raw;
        try {
            raw = YAML.readTree(yaml);
        } catch (Exception e) {
            return false;
        }
        if (!(raw instanceof ObjectNode root) || hasExtensions(root)) return false;
        return accepts(() -> CODEC.fromYaml(yaml)) || accepts(() -> TxPlan.from(yaml));
    }

    private static boolean accepts(Runnable read) {
        try {
            read.run();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasExtensions(ObjectNode root) {
        JsonNode extensions = root.get("extensions");
        return extensions != null && !extensions.isNull() && !extensions.isEmpty();
    }

    /**
     * TxPlan documents that already exist independently of the generator: every Java text block under the quicktx test
     * sources whose root is a mapping with a {@code transaction} list, and every YAML block of the TxPlan docs, plus
     * {@link IntentSamples}. Entries are keyed by origin for failure messages.
     */
    static Map<String, String> corpus() {
        Map<String, String> corpus = new LinkedHashMap<>();
        collect(Path.of("src/test/java"), ".java", TEXT_BLOCK, true, corpus);
        collect(DOCS, ".mdx", YAML_FENCE, false, corpus);
        IntentSamples.documents().forEach((type, yaml) -> corpus.put("sample:" + type, yaml));
        IntentSamples.variants().forEach((name, yaml) -> corpus.put("variant:" + name, yaml));
        return corpus;
    }

    static final Path DOCS = Path.of("../docs/content/preview/txplan");

    private static void collect(Path dir, String suffix, Pattern block, boolean javaTextBlock, Map<String, String> corpus) {
        if (!Files.isDirectory(dir)) throw new IllegalStateException("Corpus directory is missing: " + dir.toAbsolutePath());
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(suffix)).sorted().collect(Collectors.toList())) {
                Matcher m = block.matcher(Files.readString(file));
                int index = 0;
                while (m.find()) {
                    String text = m.group(1);
                    if (javaTextBlock) {
                        try {
                            // Fill String.format templates so blocks used with .formatted(...) still parse.
                            text = text.stripIndent().translateEscapes().replace("%s", "1").replace("%d", "1");
                        } catch (IllegalArgumentException notJavaText) {
                            continue;
                        }
                    }
                    if (isTxPlanDocument(text)) corpus.put(file + "#" + index, text);
                    index++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isTxPlanDocument(String text) {
        try {
            JsonNode root = YAML.readTree(text);
            return root != null && root.isObject() && root.path("transaction").isArray();
        } catch (Exception e) {
            return false;
        }
    }

    private static Schema loadSchema() {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(Map.of(SCHEMA_ID, schemaResource())));
        return registry.getSchema(SchemaLocation.of(SCHEMA_ID));
    }
}
