package com.bloxbean.cardano.client.quicktx.extension;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class QuickTxExtensionTest {

    @Test
    void qualifiedIntentRoundTripsWithoutAQuickTxDependencyOnItsOwner() {
        QuickTxExtension extension = extension(new AtomicBoolean());
        Tx tx = new Tx();
        tx.addIntention(new ExampleIntent(42));
        TxPlan plan = new TxPlan().addTransaction(tx)
                .withExtension("ex", extension.metadata());
        TxPlanCodec codec = TxPlanCodec.builder().withExtension("ex", extension).build();

        String yaml = codec.toYaml(plan);
        TxPlan restored = codec.fromYaml(yaml);
        ExampleIntent intent = (ExampleIntent) restored.getTxs().get(0).getIntentions().get(0);

        assertThat(yaml).contains("type: ex:act").doesNotContain("extensionId:");
        assertThat(intent.getExtensionId()).isEqualTo("example");
        assertThat(intent.getOperation()).isEqualTo("act");
        assertThat(intent.getValue()).isEqualTo(42);
    }

    @Test
    void undeclaredNamespaceFailsClosed() {
        QuickTxExtension extension = extension(new AtomicBoolean());
        TxPlanCodec codec = TxPlanCodec.builder().withExtension("ex", extension).build();
        String yaml = "version: '1.0'\ntransaction:\n  - tx:\n      intents:\n"
                + "        - type: missing:act\n          value: 1\n";

        assertThatThrownBy(() -> codec.fromYaml(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("undeclared extension namespace");
    }

    @Test
    void extensionMetadataVariablesResolveBeforeCompatibilityValidation() {
        QuickTxExtension extension = extension(new AtomicBoolean());
        TxPlanCodec codec = TxPlanCodec.builder().withExtension("ex", extension).build();
        String yaml = "version: '1.0'\nvariables:\n  schema: '1'\nextensions:\n"
                + "  ex:\n    extension: example\n    schema_version: ${schema}\n"
                + "transaction:\n  - tx:\n      intents:\n        - type: ex:act\n          value: 1\n";

        assertThat(codec.fromYaml(yaml).getExtensions().get("ex").getSchemaVersion()).isEqualTo("1");
    }

    @Test
    void runtimeVariablesOverrideDocumentDefaultsAndRemainOnPlan() {
        QuickTxExtension extension = extension(new AtomicBoolean());
        TxPlanCodec codec = TxPlanCodec.builder().withExtension("ex", extension).build();
        String yaml = "version: '1.0'\nvariables:\n  action_value: 1\nextensions:\n"
                + "  ex:\n    extension: example\n    schema_version: '1'\n"
                + "transaction:\n  - tx:\n      intents:\n"
                + "        - type: ex:act\n          value: ${action_value}\n";

        TxPlan plan = codec.fromYaml(yaml,
                Map.of("action_value", 42, "execution_id", "devkit-run"));
        ExampleIntent intent = (ExampleIntent) plan.getTxs().get(0).getIntentions().get(0);

        assertThat(intent.getValue()).isEqualTo(42);
        assertThat(plan.getVariables()).containsEntry("action_value", 42)
                .containsEntry("execution_id", "devkit-run");
    }

    @Test
    void reservedNamespaceIsRejected() {
        assertThatThrownBy(() -> TxPlanCodec.builder()
                .withExtension("core", extension(new AtomicBoolean())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reserved");
    }

    @Test
    void builderInvokesExtensionPreparationBeforeCoreCompletion() {
        AtomicBoolean prepared = new AtomicBoolean();
        QuickTxExtension extension = extension(prepared);
        Tx tx = new Tx();
        tx.addIntention(new ExampleIntent(0));

        QuickTxBuilder builder = new QuickTxBuilder(
                mock(UtxoSupplier.class), mock(ProtocolParamsSupplier.class), null)
                .withExtension(extension);

        assertThatThrownBy(() -> builder.compose(tx).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("prepared");
        assertThat(prepared).isTrue();
    }

    @Test
    void missingRuntimeExtensionFailsBeforeChainAccess() {
        Tx tx = new Tx();
        tx.addIntention(new MissingIntent());
        QuickTxBuilder builder = new QuickTxBuilder(
                mock(UtxoSupplier.class), mock(ProtocolParamsSupplier.class), null);

        assertThatThrownBy(() -> builder.compose(tx).build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("No runtime extension registered");
    }

    @Test
    void planMetadataIsPassedToTheBuildLocalParticipant() {
        AtomicReference<String> observedContract = new AtomicReference<>();
        QuickTxExtension extension = new QuickTxExtension() {
            @Override public String id() { return "example"; }
            @Override public String schemaVersion() { return "1"; }
            @Override public Set<String> operations() { return Set.of("act"); }
            @Override public Map<String, Class<? extends ExtensionIntent>> intentTypes() {
                return Map.of("act", ExampleIntent.class);
            }
            @Override public TxBuildExtension newBuildExtension() { return new TxBuildExtension() { }; }
            @Override public TxBuildExtension newBuildExtension(ExtensionMetadata metadata) {
                observedContract.set(metadata.getContractVersion());
                return new TxBuildExtension() {
                    @Override public void prepare(ExtensionBuildContext context) {
                        throw new IllegalStateException("prepared from plan metadata");
                    }
                };
            }
        };
        Tx tx = new Tx();
        tx.addIntention(new ExampleIntent(0));
        TxPlan plan = TxPlan.from(tx).withExtension("ex", ExtensionMetadata.builder()
                .extension("example").schemaVersion("1").contractVersion("plan-version").build());

        assertThatThrownBy(() -> new QuickTxBuilder(
                mock(UtxoSupplier.class), mock(ProtocolParamsSupplier.class), null)
                .withExtension(extension).compose(plan).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("prepared from plan metadata");
        assertThat(observedContract).hasValue("plan-version");
    }

    @Test
    void oneExtensionCannotBeBoundToAmbiguousNamespaces() {
        ExtensionMetadata metadata = ExtensionMetadata.builder()
                .extension("example").schemaVersion("1").build();
        TxPlan plan = new TxPlan().withExtension("ex", metadata);

        assertThatThrownBy(() -> plan.withExtension("other", metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another namespace");
    }

    @Test
    void unsupportedTypedOperationFailsBeforeExtensionPreparation() {
        AtomicBoolean prepared = new AtomicBoolean();
        Tx tx = new Tx();
        tx.addIntention(new UnknownExampleIntent());

        assertThatThrownBy(() -> new QuickTxBuilder(
                mock(UtxoSupplier.class), mock(ProtocolParamsSupplier.class), null)
                .withExtension(extension(prepared)).compose(tx).build())
                .isInstanceOf(TxBuildException.class)
                .hasMessageContaining("Unsupported operation");
        assertThat(prepared).isFalse();
    }

    @Test
    void extensionSemanticIntentApplyIsIntentionallyNoOp() {
        ExampleIntent intent = new ExampleIntent(1);
        Transaction transaction = Transaction.builder().build();

        intent.apply(com.bloxbean.cardano.client.quicktx.IntentContext.empty())
                .apply(null, transaction);

        assertThat(transaction).isEqualTo(Transaction.builder().build());
    }

    private static QuickTxExtension extension(AtomicBoolean prepared) {
        return new QuickTxExtension() {
            @Override public String id() { return "example"; }
            @Override public String schemaVersion() { return "1"; }
            @Override public Set<String> operations() { return Set.of("act"); }
            @Override public Map<String, Class<? extends ExtensionIntent>> intentTypes() {
                return Map.of("act", ExampleIntent.class);
            }
            @Override public TxBuildExtension newBuildExtension() {
                return new TxBuildExtension() {
                    @Override public void prepare(ExtensionBuildContext context) {
                        prepared.set(true);
                        throw new IllegalStateException("prepared");
                    }
                    @Override public void beforeScriptEvaluation(ExtensionBuildContext context,
                                                                 Transaction transaction) { }
                };
            }
        };
    }

    public static final class ExampleIntent implements ExtensionIntent {
        private int value;

        public ExampleIntent() { }

        ExampleIntent(int value) {
            this.value = value;
        }

        @Override public String getExtensionId() { return "example"; }
        @Override public String getOperation() { return "act"; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    private static final class MissingIntent implements ExtensionIntent {
        @Override public String getExtensionId() { return "missing"; }
        @Override public String getOperation() { return "act"; }
    }

    private static final class UnknownExampleIntent implements ExtensionIntent {
        @Override public String getExtensionId() { return "example"; }
        @Override public String getOperation() { return "unknown"; }
    }
}
