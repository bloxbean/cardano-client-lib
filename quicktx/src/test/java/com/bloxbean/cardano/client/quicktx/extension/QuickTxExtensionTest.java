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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /**
     * Prepared intents are a build-local overlay: the authored plan is unchanged by building it,
     * so the same plan builds again without duplicating what the extension generated.
     */
    @Test
    void preparedIntentsAreABuildLocalOverlayAndThePlanIsReusable() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        ProtocolParamsSupplier protocolParamsSupplier = mock(ProtocolParamsSupplier.class);
        String sender = "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";
        String receiver = com.bloxbean.cardano.client.address.AddressProvider.getEntAddress(
                com.bloxbean.cardano.client.address.Credential.fromKey("22".repeat(28)),
                com.bloxbean.cardano.client.common.model.Networks.testnet()).toBech32();
        when(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(List.of(com.bloxbean.cardano.client.api.model.Utxo.builder()
                        .address(sender)
                        .txHash("7e1eecf7439fb5119a6762985a61c9fb3ca8158d9fc38361f0c4746430d5e0c7")
                        .outputIndex(0)
                        .amount(List.of(com.bloxbean.cardano.client.api.model.Amount.ada(100)))
                        .build()));
        when(protocolParamsSupplier.getProtocolParams())
                .thenReturn(com.bloxbean.cardano.client.api.model.ProtocolParams.builder()
                        .minFeeA(44).minFeeB(155381).minUtxo("1000000").coinsPerUtxoSize("4312")
                        .minFeeRefScriptCostPerByte(java.math.BigDecimal.valueOf(15))
                        .build());

        AtomicBoolean failVerification = new AtomicBoolean(true);
        QuickTxExtension extension = new QuickTxExtension() {
            @Override public String id() { return "example"; }
            @Override public String schemaVersion() { return "1"; }
            @Override public Set<String> operations() { return Set.of("pay"); }
            @Override public Map<String, Class<? extends ExtensionIntent>> intentTypes() {
                return Map.of("pay", PlainExampleIntent.class);
            }
            @Override public TxBuildExtension newBuildExtension() {
                return new TxBuildExtension() {
                    @Override public void prepare(ExtensionBuildContext context) {
                        for (var tx : context.getTransactions()) {
                            if (context.extensionIntents(tx, "example").isEmpty()) continue;
                            context.addPreparedIntent(tx, com.bloxbean.cardano.client.quicktx.intent
                                    .PaymentIntent.builder().address(receiver)
                                    .amounts(List.of(com.bloxbean.cardano.client.api.model.Amount.ada(5)))
                                    .build());
                        }
                    }
                    @Override public void verify(ExtensionBuildContext context, Transaction transaction) {
                        if (failVerification.get()) throw new IllegalStateException("verification failed");
                    }
                };
            }
        };

        Tx tx = new Tx().from(sender);
        tx.addIntention(new PlainExampleIntent());
        TxPlan plan = TxPlan.from(tx).withExtension("ex", extension.metadata());
        TxPlanCodec codec = TxPlanCodec.builder().withExtension("ex", extension).build();
        String yamlBefore = codec.toYaml(plan);
        QuickTxBuilder builder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .withExtension(extension);

        // A failed build leaves the plan exactly as authored.
        assertThatThrownBy(() -> builder.compose(plan).build())
                .isInstanceOf(IllegalStateException.class).hasMessage("verification failed");
        assertThat(tx.getIntentions()).hasSize(1);
        assertThat(codec.toYaml(plan)).isEqualTo(yamlBefore);

        failVerification.set(false);
        Transaction first = builder.compose(plan).build();
        Transaction second = builder.compose(plan).build();

        for (Transaction built : List.of(first, second)) {
            assertThat(built.getBody().getOutputs().stream()
                    .filter(output -> receiver.equals(output.getAddress())).count())
                    .as("the prepared payment appears exactly once per build")
                    .isEqualTo(1);
        }
        assertThat(second.getBody().getOutputs()).hasSameSizeAs(first.getBody().getOutputs());
        assertThat(tx.getIntentions()).hasSize(1);
        assertThat(codec.toYaml(plan)).isEqualTo(yamlBefore);
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

    /** A declaration the extension turns into an ordinary payment; no script involved. */
    /**
     * Generated intents live on the authored Tx only while it is being built. A build that fails
     * after preparation must not leave the generated script state behind, or the next build and
     * every {@code hasScriptIntents()} caller would see it.
     */
    @Test
    void aFailedBuildLeavesNoGeneratedScriptStateOnTheAuthoredTx() {
        UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
        ProtocolParamsSupplier protocolParamsSupplier = mock(ProtocolParamsSupplier.class);
        String sender = "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";
        String rewardAddress = com.bloxbean.cardano.client.address.AddressProvider.getRewardAddress(
                com.bloxbean.cardano.client.address.Credential.fromScript("33".repeat(28)),
                com.bloxbean.cardano.client.common.model.Networks.testnet()).toBech32();
        when(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(List.of(com.bloxbean.cardano.client.api.model.Utxo.builder()
                        .address(sender)
                        .txHash("7e1eecf7439fb5119a6762985a61c9fb3ca8158d9fc38361f0c4746430d5e0c7")
                        .outputIndex(0)
                        .amount(List.of(com.bloxbean.cardano.client.api.model.Amount.ada(100)))
                        .build()));
        when(protocolParamsSupplier.getProtocolParams())
                .thenReturn(com.bloxbean.cardano.client.api.model.ProtocolParams.builder()
                        .minFeeA(44).minFeeB(155381).minUtxo("1000000").coinsPerUtxoSize("4312")
                        .minFeeRefScriptCostPerByte(java.math.BigDecimal.valueOf(15))
                        .build());

        QuickTxExtension extension = new QuickTxExtension() {
            @Override public String id() { return "example"; }
            @Override public String schemaVersion() { return "1"; }
            @Override public Set<String> operations() { return Set.of("pay"); }
            @Override public Map<String, Class<? extends ExtensionIntent>> intentTypes() {
                return Map.of("pay", PlainExampleIntent.class);
            }
            @Override public TxBuildExtension newBuildExtension() {
                return new TxBuildExtension() {
                    @Override public void prepare(ExtensionBuildContext context) {
                        for (var tx : context.getTransactions()) {
                            if (context.extensionIntents(tx, "example").isEmpty()) continue;
                            // A script-bearing generated intent: withdraw zero with a redeemer.
                            context.addPreparedIntent(tx, com.bloxbean.cardano.client.quicktx.intent
                                    .StakeWithdrawalIntent.builder()
                                    .rewardAddress(rewardAddress)
                                    .amount(java.math.BigInteger.ZERO)
                                    .redeemer(com.bloxbean.cardano.client.plutus.spec.PlutusData.unit())
                                    .build());
                        }
                    }
                    @Override public void verify(ExtensionBuildContext context, Transaction transaction) {
                        throw new IllegalStateException("verification failed");
                    }
                };
            }
        };

        Tx tx = new Tx().from(sender);
        tx.addIntention(new PlainExampleIntent());
        assertThat(tx.hasScriptIntents()).isFalse();

        QuickTxBuilder builder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .withExtension(extension);
        assertThatThrownBy(() -> builder.compose(tx).build()).isInstanceOf(RuntimeException.class);

        assertThat(tx.hasScriptIntents())
                .as("the generated withdraw-zero must not survive the failed build")
                .isFalse();
        assertThat(tx.getIntentions()).hasSize(1);
    }

    public static final class PlainExampleIntent implements ExtensionIntent {
        @Override public String getExtensionId() { return "example"; }
        @Override public String getOperation() { return "pay"; }
        @Override public boolean hasRedeemer() { return false; }
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
