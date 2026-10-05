package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runtime variables are resolved on the parsed tree, so a value can carry anything YAML would
 * otherwise interpret, and an exact placeholder keeps the value's native type.
 */
class TxPlanCodecVariableResolutionTest {

    private static final String HEADER = "version: '1.0'\nextensions:\n  ex:\n    extension: example\n"
            + "    schema_version: '1'\n    deployment:\n      network: ${network_id}\n"
            + "      tags: ${tags}\n";

    private final TxPlanCodec codec = TxPlanCodec.builder().withExtension("ex", extension()).build();

    @Test
    void exactPlaceholdersKeepNativeTypesAndEmbeddedOnesInterpolateAsText() {
        String yaml = HEADER
                + "transaction:\n  - tx:\n      intents:\n        - type: ex:rich\n"
                + "          note: ${note}\n          count: ${count}\n          flag: ${flag}\n"
                + "          labels: ${labels}\n          extra: ${extra}\n"
                + "          greeting: 'hello ${name}: ${count}'\n";
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("network_id", 0);
        variables.put("tags", List.of("a", "b"));
        variables.put("note", "quote \" colon: hash # brace } newline\nnext");
        variables.put("count", 42);
        variables.put("flag", true);
        variables.put("labels", List.of("x", "y"));
        variables.put("extra", Map.of("nested", List.of(1, 2)));
        variables.put("name", "world");

        TxPlan plan = codec.fromYaml(yaml, variables);
        RichIntent intent = (RichIntent) plan.getTxs().get(0).getIntentions().get(0);

        assertThat(intent.getNote()).isEqualTo("quote \" colon: hash # brace } newline\nnext");
        assertThat(intent.getCount()).isEqualTo(42);
        assertThat(intent.isFlag()).isTrue();
        assertThat(intent.getLabels()).containsExactly("x", "y");
        assertThat(intent.getExtra()).containsEntry("nested", List.of(1, 2));
        assertThat(intent.getGreeting()).isEqualTo("hello world: 42");
        assertThat(plan.getExtensions().get("ex").getDeployment())
                .containsEntry("network", 0)
                .containsEntry("tags", List.of("a", "b"));
    }

    @Test
    void replacementValuesAreNeverReadAsTemplates() {
        String yaml = HEADER
                + "transaction:\n  - tx:\n      intents:\n        - type: ex:rich\n"
                + "          note: ${note}\n          greeting: 'said ${note}'\n";
        Map<String, Object> variables = Map.of("network_id", 0, "tags", List.of(),
                "note", "literal ${other} stays", "other", "SHOULD NOT APPEAR");

        RichIntent intent = (RichIntent) codec.fromYaml(yaml, variables)
                .getTxs().get(0).getIntentions().get(0);

        assertThat(intent.getNote()).isEqualTo("literal ${other} stays");
        assertThat(intent.getGreeting()).isEqualTo("said literal ${other} stays");
    }

    /**
     * The core reconstruction runs its own resolution pass over {@code from}, change address and
     * every intent. It must not see the variables again, or a replacement value containing
     * {@code ${...}} would be substituted a second time.
     */
    @Test
    void theCoreReconstructionNeverResolvesAReplacementValueAgain() {
        String yaml = HEADER
                + "transaction:\n  - tx:\n      from: ${address}\n      intents:\n"
                + "        - type: ex:rich\n          note: plain\n";
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("network_id", 0);
        variables.put("tags", List.of("a"));
        variables.put("address", "literal ${other}");
        variables.put("other", "REINTERPRETED");

        assertThatThrownBy(() -> codec.fromYaml(yaml, variables))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kept literal")
                .hasMessageContaining("other")
                .hasMessageNotContaining("REINTERPRETED");
    }

    @Test
    void aMissingVariableNamesItsDocumentPath() {
        String yaml = HEADER
                + "transaction:\n  - tx:\n      intents:\n        - type: ex:rich\n"
                + "          note: ${missing}\n";

        assertThatThrownBy(() -> codec.fromYaml(yaml, Map.of("network_id", 0, "tags", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining("transaction[0].tx.intents[0].note");
    }

    @Test
    void runtimeValuesWinOverDocumentDefaultsAndAreRetained() {
        String yaml = "version: '1.0'\nvariables:\n  network_id: 9\n  tags: []\n  note: default\n"
                + "extensions:\n  ex:\n    extension: example\n    schema_version: '1'\n"
                + "    deployment:\n      network: ${network_id}\n"
                + "transaction:\n  - tx:\n      intents:\n        - type: ex:rich\n          note: ${note}\n";

        TxPlan plan = codec.fromYaml(yaml, Map.of("note", "runtime"));

        assertThat(((RichIntent) plan.getTxs().get(0).getIntentions().get(0)).getNote()).isEqualTo("runtime");
        assertThat(plan.getExtensions().get("ex").getDeployment()).containsEntry("network", 9);
        assertThat(plan.getVariables()).containsEntry("note", "runtime").containsEntry("network_id", 9);
    }

    @Test
    void extensionMetadataIsImmutableAtEveryBoundary() {
        Map<String, Object> deployment = new LinkedHashMap<>();
        deployment.put("network", 0);
        deployment.put("list", new ArrayList<>(List.of("a")));
        ExtensionMetadata metadata = ExtensionMetadata.builder()
                .extension("example").schemaVersion("1").deployment(deployment).build();

        deployment.put("network", 1);                       // caller mutation after construction
        assertThat(metadata.getDeployment()).containsEntry("network", 0);
        assertThatThrownBy(() -> metadata.getDeployment().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) metadata.getDeployment().get("list");
        assertThatThrownBy(() -> list.add("b")).isInstanceOf(UnsupportedOperationException.class);

        TxPlan plan = new TxPlan().withExtension("ex", metadata);
        assertThatThrownBy(() -> plan.getExtensions().put("other", metadata))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.getExtensions().get("ex").getDeployment().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        ExtensionMetadata same = ExtensionMetadata.builder()
                .extension("example").schemaVersion("1")
                .deployment(Map.of("network", 0, "list", List.of("a"))).build();
        assertThat(same).isEqualTo(metadata);
        assertThat(codec.toYaml(plan)).isEqualTo(codec.toYaml(new TxPlan().withExtension("ex", same)));
    }

    private static QuickTxExtension extension() {
        return new QuickTxExtension() {
            @Override public String id() { return "example"; }
            @Override public String schemaVersion() { return "1"; }
            @Override public Set<String> operations() { return Set.of("rich"); }
            @Override public Map<String, Class<? extends ExtensionIntent>> intentTypes() {
                return Map.of("rich", RichIntent.class);
            }
            @Override public TxBuildExtension newBuildExtension() { return new TxBuildExtension() { }; }
        };
    }

    public static final class RichIntent implements ExtensionIntent {
        private String note;
        private int count;
        private boolean flag;
        private List<String> labels;
        private Map<String, Object> extra;
        private String greeting;

        @Override public String getExtensionId() { return "example"; }
        @Override public String getOperation() { return "rich"; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public boolean isFlag() { return flag; }
        public void setFlag(boolean flag) { this.flag = flag; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
        public Map<String, Object> getExtra() { return extra; }
        public void setExtra(Map<String, Object> extra) { this.extra = extra; }
        public String getGreeting() { return greeting; }
        public void setGreeting(String greeting) { this.greeting = greeting; }
    }
}
