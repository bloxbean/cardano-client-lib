package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Id-bound codec registration and the literal read/copy boundary (ADR 0008 D3, D4a).
 */
class TxPlanCodecIdBindingTest {
    private static final String SENDER = "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";

    @Test
    void idBoundExtensionResolvesWhateverNamespaceTheDocumentChose() {
        QuickTxExtension extension = new NoteExtension(true);
        TxPlanCodec codec = TxPlanCodec.builder().withExtension(extension).build();

        for (String namespace : new String[]{"note", "n2"}) {
            TxPlan plan = plan("hello").withExtension(namespace, extension.metadata());
            String yaml = codec.toYaml(plan);
            assertThat(yaml).contains("type: " + namespace + ":write");

            TxPlan restored = codec.fromYaml(yaml);
            assertThat(restored.getExtensions()).containsOnlyKeys(namespace);
            assertThat(onlyIntent(restored).getNote()).isEqualTo("hello");
            assertThat(codec.toYaml(restored)).isEqualTo(yaml);
        }
    }

    @Test
    void explicitNamespaceBindingTakesPrecedenceOverTheIdBinding() {
        QuickTxExtension accepting = new NoteExtension(true);
        QuickTxExtension rejecting = new NoteExtension(false);
        TxPlanCodec codec = TxPlanCodec.builder()
                .withExtension("pinned", accepting)
                .withExtension(rejecting)
                .build();

        assertThat(codec.toYaml(plan("a").withExtension("pinned", accepting.metadata())))
                .contains("pinned:write");
        assertThatThrownBy(() -> codec.toYaml(plan("a").withExtension("other", accepting.metadata())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejected by test");
    }

    @Test
    void idRegistrationIsIdempotentForTheSameInstanceOnly() {
        QuickTxExtension extension = new NoteExtension(true);
        TxPlanCodec.Builder builder = TxPlanCodec.builder().withExtension(extension).withExtension(extension);
        assertThat(builder.build()).isNotNull();

        assertThatThrownBy(() -> builder.withExtension(new NoteExtension(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Extension already registered: note");
    }

    @Test
    void aNamespaceWithNoBindingForItsDeclaredIdIsRejected() {
        TxPlanCodec codec = TxPlanCodec.builder().withExtension(new NoteExtension(true)).build();
        String yaml = TxPlanCodec.builder().withExtension(new NoteExtension(true)).build()
                .toYaml(plan("a").withExtension("x", new NoteExtension(true).metadata()))
                .replace("extension: note", "extension: other");

        assertThatThrownBy(() -> codec.fromYaml(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No codec extension registered for namespace 'x'");
    }

    @Test
    void readLiteralNeverExpandsPlaceholdersWithOrWithoutAMatchingVariable() {
        QuickTxExtension extension = new NoteExtension(true);
        TxPlanCodec codec = TxPlanCodec.builder().withExtension(extension).build();
        TxPlan plan = plan("literal ${other} stays").withExtension("note", extension.metadata());
        String withoutVariable = codec.toYaml(plan);
        String withVariable = codec.toYaml(plan.setVariables(Map.of("other", "REINTERPRETED")));

        assertThat(onlyIntent(codec.readLiteral(withoutVariable)).getNote())
                .isEqualTo("literal ${other} stays");
        TxPlan literal = codec.readLiteral(withVariable);
        assertThat(onlyIntent(literal).getNote()).isEqualTo("literal ${other} stays");
        assertThat(literal.getVariables()).containsEntry("other", "REINTERPRETED");

        // Contrast: the authoring decoder binds the placeholder, which is why TxFlow must not use it.
        assertThat(onlyIntent(codec.fromYaml(withVariable)).getNote()).isEqualTo("literal REINTERPRETED stays");
        assertThatThrownBy(() -> codec.fromYaml(withoutVariable))
                .hasMessageContaining("Variable not found: other");
    }

    @Test
    void copyIsLiterallyEqualAndSharesNoIntentInstance() {
        QuickTxExtension extension = new NoteExtension(true);
        TxPlanCodec codec = TxPlanCodec.builder().withExtension(extension).build();
        TxPlan source = plan("literal ${other} stays")
                .withExtension("note", extension.metadata())
                .setVariables(Map.of("other", "REINTERPRETED"));

        TxPlan copy = codec.copy(source);

        assertThat(codec.toYaml(copy)).isEqualTo(codec.toYaml(source));
        assertThat(copy.getExtensions()).isEqualTo(source.getExtensions());
        assertThat(copy.getVariables()).isEqualTo(source.getVariables());
        assertThat(onlyIntent(copy)).isNotSameAs(onlyIntent(source));
        assertThat(onlyIntent(copy).getNote()).isEqualTo("literal ${other} stays");

        onlyIntent(copy).setNote("changed");
        assertThat(onlyIntent(source).getNote()).isEqualTo("literal ${other} stays");
    }

    @Test
    void copyKeepsDeploymentValueTypes() {
        QuickTxExtension extension = new NoteExtension(true);
        TxPlanCodec codec = TxPlanCodec.builder().withExtension(extension).build();
        ExtensionMetadata metadata = extension.metadata().toBuilder()
                .deployment(Map.of("network", "0", "count", 7, "flag", true, "version", "1.0"))
                .build();
        TxPlan source = plan("a").withExtension("note", metadata);

        TxPlan copy = codec.copy(source);

        assertThat(copy.getExtensions().get("note")).isEqualTo(metadata);
        assertThat(copy.getExtensions().get("note").getDeployment().get("network")).isEqualTo("0");
        assertThat(copy.getExtensions().get("note").getDeployment().get("count")).isEqualTo(7L);
    }

    private static TxPlan plan(String note) {
        Tx tx = new Tx().from(SENDER);
        NoteIntent intent = new NoteIntent();
        intent.setNote(note);
        tx.addIntention(intent);
        return TxPlan.from(tx);
    }

    private static NoteIntent onlyIntent(TxPlan plan) {
        return (NoteIntent) plan.getTxs().get(0).getIntentions().get(0);
    }

    public static final class NoteIntent implements ExtensionIntent {
        private String note;

        @Override public String getExtensionId() { return "note"; }
        @Override public String getOperation() { return "write"; }
        @Override public boolean hasRedeemer() { return false; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    private static final class NoteExtension implements QuickTxExtension {
        private final boolean accept;

        private NoteExtension(boolean accept) {
            this.accept = accept;
        }

        @Override public String id() { return "note"; }
        @Override public String schemaVersion() { return "1"; }
        @Override public Set<String> operations() { return Set.of("write"); }
        @Override public Map<String, Class<? extends ExtensionIntent>> intentTypes() {
            return Map.of("write", NoteIntent.class);
        }
        @Override public TxBuildExtension newBuildExtension() { return new TxBuildExtension() { }; }

        @Override
        public void validateMetadata(ExtensionMetadata metadata) {
            QuickTxExtension.super.validateMetadata(metadata);
            if (!accept) throw new IllegalArgumentException("rejected by test");
        }
    }
}
