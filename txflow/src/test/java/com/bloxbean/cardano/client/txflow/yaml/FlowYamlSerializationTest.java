package com.bloxbean.cardano.client.txflow.yaml;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.BackoffStrategy;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.SelectionStrategy;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.exec.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.exec.RollbackStrategy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TxFlow YAML serialization.
 *
 * Note: These tests use TxPlan (YAML-first workflow) because factory functions
 * (withTxContext) cannot be serialized to YAML. See ADR-003 for details.
 */
class FlowYamlSerializationTest {

    @Test
    void toYaml_shouldSerializeBasicFlow() {
        // Given - use TxPlan since factory functions can't be serialized
        TxPlan plan = TxPlan.from(new Tx()
                .from("addr1_sender")
                .payToAddress("addr1_receiver", Amount.ada(100)));

        TxFlow flow = TxFlow.builder("basic-flow")
                .withDescription("A basic test flow")
                .addVariable("amount", 100_000_000L)
                .addStep(FlowStep.builder("step1")
                        .withDescription("First step")
                        .withTxPlan(plan)
                        .build())
                .build();

        // When
        String yaml = flow.toYaml();

        // Then
        System.out.println("Generated YAML:");
        System.out.println(yaml);

        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("version:");
        assertThat(yaml).contains("flow:");
        assertThat(yaml).contains("id: basic-flow");
        assertThat(yaml).contains("description: A basic test flow");
        assertThat(yaml).contains("step1");
    }

    @Test
    void toYaml_shouldSerializeFlowWithDependencies() {
        // Given
        TxPlan plan1 = TxPlan.from(new Tx().from("addr1"));
        TxPlan plan2 = TxPlan.from(new Tx().from("addr1"));

        TxFlow flow = TxFlow.builder("dep-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxPlan(plan1)
                        .build())
                .addStep(FlowStep.builder("step2")
                        .dependsOn("step1")
                        .withTxPlan(plan2)
                        .build())
                .build();

        // When
        String yaml = flow.toYaml();

        // Then
        System.out.println("Generated YAML:");
        System.out.println(yaml);

        assertThat(yaml).contains("depends_on:");
        assertThat(yaml).contains("from_step: step1");
    }

    @Test
    void roundTrip_shouldPreserveFlowStructure() {
        // Given
        TxPlan depositPlan = TxPlan.from(new Tx()
                .from("addr1_sender")
                .payToAddress("addr1_contract", Amount.ada(50)));
        TxPlan withdrawPlan = TxPlan.from(new Tx()
                .from("addr1_contract")
                .payToAddress("addr1_receiver", Amount.ada(25)));

        TxFlow original = TxFlow.builder("roundtrip-flow")
                .withDescription("Test roundtrip")
                .addVariable("sender", "addr1_sender")
                .addStep(FlowStep.builder("deposit")
                        .withDescription("Deposit funds")
                        .withTxPlan(depositPlan)
                        .build())
                .addStep(FlowStep.builder("withdraw")
                        .withDescription("Withdraw funds")
                        .dependsOn("deposit", SelectionStrategy.ALL)
                        .withTxPlan(withdrawPlan)
                        .build())
                .build();

        // When
        String yaml = original.toYaml();
        System.out.println("Serialized YAML:");
        System.out.println(yaml);

        TxFlow restored = TxFlow.fromYaml(yaml);

        // Then
        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getDescription()).isEqualTo(original.getDescription());
        assertThat(restored.getSteps()).hasSameSizeAs(original.getSteps());

        // Check first step
        assertThat(restored.getStep("deposit")).isPresent();
        assertThat(restored.getStep("deposit").get().getDescription()).isEqualTo("Deposit funds");
        assertThat(restored.getStep("deposit").get().hasDependencies()).isFalse();

        // Check second step
        assertThat(restored.getStep("withdraw")).isPresent();
        assertThat(restored.getStep("withdraw").get().getDescription()).isEqualTo("Withdraw funds");
        assertThat(restored.getStep("withdraw").get().hasDependencies()).isTrue();
        assertThat(restored.getStep("withdraw").get().getDependencyStepIds()).containsExactly("deposit");
    }

    @Test
    void fromYaml_shouldParseValidYaml() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "flow:\n" +
                "  id: parsed-flow\n" +
                "  description: Parsed from YAML\n" +
                "  variables:\n" +
                "    amount: 100000000\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        description: First step\n" +
                "        tx:\n" +
                "          from: addr1_sender\n" +
                "          intents:\n" +
                "            - type: payment\n" +
                "              receiver: addr1_receiver\n" +
                "              amount:\n" +
                "                lovelace: 100000000\n";

        // When
        TxFlow flow = TxFlow.fromYaml(yaml);

        // Then
        assertThat(flow.getId()).isEqualTo("parsed-flow");
        assertThat(flow.getDescription()).isEqualTo("Parsed from YAML");
        assertThat(flow.getVariables()).containsKey("amount");
        assertThat(flow.getSteps()).hasSize(1);
        assertThat(flow.getStep("step1")).isPresent();
    }

    @Test
    void fromYaml_shouldParseFlowWithDependencies() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "flow:\n" +
                "  id: dep-flow\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        tx:\n" +
                "          from: addr1\n" +
                "          intents:\n" +
                "            - type: payment\n" +
                "              receiver: addr2\n" +
                "              amount:\n" +
                "                lovelace: 1000000\n" +
                "    - step:\n" +
                "        id: step2\n" +
                "        depends_on:\n" +
                "          - from_step: step1\n" +
                "            strategy: all\n" +
                "        tx:\n" +
                "          from: addr2\n" +
                "          intents:\n" +
                "            - type: payment\n" +
                "              receiver: addr3\n" +
                "              amount:\n" +
                "                lovelace: 500000\n";

        // When
        TxFlow flow = TxFlow.fromYaml(yaml);

        // Then
        assertThat(flow.getSteps()).hasSize(2);

        var step2 = flow.getStep("step2");
        assertThat(step2).isPresent();
        assertThat(step2.get().hasDependencies()).isTrue();
        assertThat(step2.get().getDependencyStepIds()).containsExactly("step1");
        assertThat(step2.get().getDependencies().get(0).getStrategy()).isEqualTo(SelectionStrategy.ALL);
    }

    @Test
    void toYaml_shouldSerializeRetryPolicy() {
        // Given
        TxPlan plan = TxPlan.from(new Tx()
                .from("addr1_sender")
                .payToAddress("addr1_receiver", Amount.ada(100)));

        TxFlow flow = TxFlow.builder("retry-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxPlan(plan)
                        .withRetryPolicy(RetryPolicy.builder()
                                .maxAttempts(5)
                                .backoffStrategy(BackoffStrategy.EXPONENTIAL)
                                .initialDelay(Duration.ofSeconds(2))
                                .maxDelay(Duration.ofSeconds(60))
                                .build())
                        .build())
                .build();

        // When
        String yaml = flow.toYaml();

        // Then
        System.out.println("Generated YAML with retry:");
        System.out.println(yaml);

        assertThat(yaml).contains("retry:");
        assertThat(yaml).contains("max_attempts: 5");
        assertThat(yaml).contains("backoff: exponential");
        assertThat(yaml).contains("initial_delay: 2s");
        assertThat(yaml).contains("max_delay: 1m");
    }

    @Test
    void fromYaml_shouldParseRetryPolicy() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "flow:\n" +
                "  id: retry-flow\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        retry:\n" +
                "          max_attempts: 5\n" +
                "          backoff: exponential\n" +
                "          initial_delay: 2s\n" +
                "          max_delay: 30s\n" +
                "        tx:\n" +
                "          from: addr1_sender\n" +
                "          intents:\n" +
                "            - type: payment\n" +
                "              receiver: addr1_receiver\n" +
                "              amount:\n" +
                "                lovelace: 100000000\n";

        // When
        TxFlow flow = TxFlow.fromYaml(yaml);

        // Then
        assertThat(flow.getSteps()).hasSize(1);

        var step1 = flow.getStep("step1");
        assertThat(step1).isPresent();
        assertThat(step1.get().hasRetryPolicy()).isTrue();

        RetryPolicy policy = step1.get().getRetryPolicy();
        assertThat(policy.getMaxAttempts()).isEqualTo(5);
        assertThat(policy.getBackoffStrategy()).isEqualTo(BackoffStrategy.EXPONENTIAL);
        assertThat(policy.getInitialDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.getMaxDelay()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void roundTrip_shouldPreserveRetryPolicy() {
        // Given
        RetryPolicy originalPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .backoffStrategy(BackoffStrategy.LINEAR)
                .initialDelay(Duration.ofMillis(500))
                .maxDelay(Duration.ofSeconds(10))
                .retryOnTimeout(false)
                .retryOnNetworkError(false)
                .build();

        TxPlan plan = TxPlan.from(new Tx()
                .from("addr1_sender")
                .payToAddress("addr1_receiver", Amount.ada(100)));

        TxFlow original = TxFlow.builder("roundtrip-retry-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxPlan(plan)
                        .withRetryPolicy(originalPolicy)
                        .build())
                .build();

        // When
        String yaml = original.toYaml();
        System.out.println("Serialized YAML with retry:");
        System.out.println(yaml);

        TxFlow restored = TxFlow.fromYaml(yaml);

        // Then
        var step1 = restored.getStep("step1");
        assertThat(step1).isPresent();
        assertThat(step1.get().hasRetryPolicy()).isTrue();

        RetryPolicy restoredPolicy = step1.get().getRetryPolicy();
        assertThat(restoredPolicy.getMaxAttempts()).isEqualTo(originalPolicy.getMaxAttempts());
        assertThat(restoredPolicy.getBackoffStrategy()).isEqualTo(originalPolicy.getBackoffStrategy());
        assertThat(restoredPolicy.getInitialDelay()).isEqualTo(originalPolicy.getInitialDelay());
        assertThat(restoredPolicy.getMaxDelay()).isEqualTo(originalPolicy.getMaxDelay());
        assertThat(restoredPolicy.isRetryOnTimeout()).isEqualTo(originalPolicy.isRetryOnTimeout());
        assertThat(restoredPolicy.isRetryOnNetworkError()).isEqualTo(originalPolicy.isRetryOnNetworkError());
    }

    @Test
    void fromYaml_shouldParseDurationFormats() {
        // Given - test various duration formats
        String yaml = "version: \"1.0\"\n" +
                "flow:\n" +
                "  id: duration-test-flow\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        retry:\n" +
                "          max_attempts: 3\n" +
                "          initial_delay: 500ms\n" +
                "          max_delay: 2m\n" +
                "        tx:\n" +
                "          from: addr1\n" +
                "          intents:\n" +
                "            - type: payment\n" +
                "              receiver: addr2\n" +
                "              amount:\n" +
                "                lovelace: 1000000\n";

        // When
        TxFlow flow = TxFlow.fromYaml(yaml);

        // Then
        var step1 = flow.getStep("step1");
        assertThat(step1).isPresent();
        assertThat(step1.get().hasRetryPolicy()).isTrue();

        RetryPolicy policy = step1.get().getRetryPolicy();
        assertThat(policy.getInitialDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(policy.getMaxDelay()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void fromYaml_shouldParseFlowExecutionContext() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "context:\n" +
                "  chaining_mode: batch\n" +
                "  confirmation:\n" +
                "    preset: quick\n" +
                "    min_confirmations: 0\n" +
                "    check_interval: 250ms\n" +
                "    timeout: 7s\n" +
                "    max_rollback_retries: 0\n" +
                "    wait_for_backend_after_rollback: false\n" +
                "    post_rollback_wait_attempts: 2\n" +
                "    post_rollback_utxo_sync_delay: 500ms\n" +
                "  rollback_strategy: notify_only\n" +
                "  retry:\n" +
                "    max_attempts: 4\n" +
                "    backoff: fixed\n" +
                "    initial_delay: 250ms\n" +
                "    retry_on_timeout: false\n" +
                "flow:\n" +
                "  id: context-flow\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        tx:\n" +
                "          from: addr1_sender\n" +
                "          intents:\n" +
                "            - type: payment\n" +
                "              receiver: addr1_receiver\n" +
                "              amount:\n" +
                "                lovelace: 1000000\n";

        // When
        TxFlow flow = TxFlow.fromYaml(yaml);

        // Then
        assertThat(flow.getExecutionSettings().getChainingMode()).isEqualTo(ChainingMode.BATCH);
        assertThat(flow.getExecutionSettings().getRollbackStrategy()).isEqualTo(RollbackStrategy.NOTIFY_ONLY);

        ConfirmationConfig confirmationConfig = flow.getExecutionSettings().getConfirmationConfig();
        assertThat(confirmationConfig).isNotNull();
        assertThat(confirmationConfig.getMinConfirmations()).isZero();
        assertThat(confirmationConfig.getMaxRollbackRetries()).isZero();
        assertThat(confirmationConfig.isWaitForBackendAfterRollback()).isFalse();
        assertThat(confirmationConfig.getCheckInterval()).isEqualTo(Duration.ofMillis(250));
        assertThat(confirmationConfig.getTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(confirmationConfig.getPostRollbackWaitAttempts()).isEqualTo(2);
        assertThat(confirmationConfig.getPostRollbackUtxoSyncDelay()).isEqualTo(Duration.ofMillis(500));

        RetryPolicy retryPolicy = flow.getExecutionSettings().getRetryPolicy();
        assertThat(retryPolicy).isNotNull();
        assertThat(retryPolicy.getMaxAttempts()).isEqualTo(4);
        assertThat(retryPolicy.getBackoffStrategy()).isEqualTo(BackoffStrategy.FIXED);
        assertThat(retryPolicy.getInitialDelay()).isEqualTo(Duration.ofMillis(250));
        assertThat(retryPolicy.isRetryOnTimeout()).isFalse();
    }

    @Test
    void toYaml_shouldSerializeFlowExecutionContextWithStableRootOrder() {
        // Given
        TxPlan plan = TxPlan.from(new Tx()
                .from("addr1_sender")
                .payToAddress("addr1_receiver", Amount.ada(1)));

        ConfirmationConfig confirmationConfig = ConfirmationConfig.builder()
                .minConfirmations(0)
                .checkInterval(Duration.ofMillis(500))
                .timeout(Duration.ofSeconds(45))
                .maxRollbackRetries(0)
                .waitForBackendAfterRollback(false)
                .postRollbackWaitAttempts(2)
                .postRollbackUtxoSyncDelay(Duration.ofMillis(250))
                .build();

        TxFlow flow = TxFlow.builder("context-roundtrip-flow")
                .withChainingMode(ChainingMode.BATCH)
                .withConfirmationConfig(confirmationConfig)
                .withRollbackStrategy(RollbackStrategy.NOTIFY_ONLY)
                .withDefaultRetryPolicy(RetryPolicy.builder()
                        .maxAttempts(2)
                        .backoffStrategy(BackoffStrategy.LINEAR)
                        .initialDelay(Duration.ofSeconds(2))
                        .maxDelay(Duration.ofSeconds(6))
                        .build())
                .addStep(FlowStep.builder("step1")
                        .withTxPlan(plan)
                        .build())
                .build();

        // When
        String yaml = flow.toYaml();
        TxFlow restored = TxFlow.fromYaml(yaml);

        // Then
        assertThat(yaml.indexOf("context:")).isLessThan(yaml.indexOf("flow:"));
        assertThat(yaml).contains("chaining_mode: BATCH");
        assertThat(yaml).contains("rollback_strategy: NOTIFY_ONLY");
        assertThat(yaml).contains("max_rollback_retries: 0");
        assertThat(yaml).contains("wait_for_backend_after_rollback: false");
        assertThat(yaml).contains("post_rollback_utxo_sync_delay: 250ms");
        assertThat(restored.getExecutionSettings().getChainingMode()).isEqualTo(ChainingMode.BATCH);
        assertThat(restored.getExecutionSettings().getRollbackStrategy()).isEqualTo(RollbackStrategy.NOTIFY_ONLY);
        assertThat(restored.getExecutionSettings().getConfirmationConfig().getMinConfirmations()).isZero();
        assertThat(restored.getExecutionSettings().getRetryPolicy().getBackoffStrategy()).isEqualTo(BackoffStrategy.LINEAR);
    }

    @Test
    void fromYaml_shouldRejectContextMaxRollbackRetriesAlias() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "context:\n" +
                "  max_rollback_retries: 0\n" +
                "flow:\n" +
                "  id: invalid-context-alias\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        tx:\n" +
                "          from: addr1\n";

        // Then
        assertThatThrownBy(() -> TxFlow.fromYaml(yaml))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize YAML to FlowDocument");
    }

    @Test
    void fromYaml_shouldRejectEmptyConfirmationObject() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "context:\n" +
                "  confirmation: {}\n" +
                "flow:\n" +
                "  id: empty-confirmation\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        tx:\n" +
                "          from: addr1\n";

        // Then
        assertThatThrownBy(() -> TxFlow.fromYaml(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("context.confirmation object cannot be empty");
    }

    @Test
    void fromYaml_shouldRejectInvalidFlowLevelRetryStrictly() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "context:\n" +
                "  retry:\n" +
                "    backoff: sometimes\n" +
                "flow:\n" +
                "  id: invalid-flow-retry\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        tx:\n" +
                "          from: addr1\n";

        // Then
        assertThatThrownBy(() -> TxFlow.fromYaml(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown backoff: sometimes");
    }

    @Test
    void fromYaml_shouldRejectInvalidStepRetryStrictly() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "flow:\n" +
                "  id: invalid-step-retry\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        retry:\n" +
                "          initial_delay: soon\n" +
                "        tx:\n" +
                "          from: addr1\n";

        // Then
        assertThatThrownBy(() -> TxFlow.fromYaml(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid duration for initial_delay: soon");
    }

    @Test
    void fromYaml_shouldRejectInvalidDependencyStrategyStrictly() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "flow:\n" +
                "  id: invalid-dependency-strategy\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        tx:\n" +
                "          from: addr1\n" +
                "    - step:\n" +
                "        id: step2\n" +
                "        depends_on:\n" +
                "          - from_step: step1\n" +
                "            strategy: frist\n" +
                "        tx:\n" +
                "          from: addr2\n";

        // Then
        assertThatThrownBy(() -> TxFlow.fromYaml(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown strategy: frist");
    }

    @Test
    void fromYaml_shouldRejectInvalidConfirmationBounds() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "context:\n" +
                "  confirmation:\n" +
                "    check_interval: 0s\n" +
                "flow:\n" +
                "  id: invalid-confirmation-bounds\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        tx:\n" +
                "          from: addr1\n";

        // Then
        assertThatThrownBy(() -> TxFlow.fromYaml(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check_interval must be positive");
    }

    @Test
    void fromYaml_shouldRejectNegativeRollbackRetryCount() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "context:\n" +
                "  confirmation:\n" +
                "    max_rollback_retries: -1\n" +
                "flow:\n" +
                "  id: invalid-rollback-retries\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        tx:\n" +
                "          from: addr1\n";

        // Then
        assertThatThrownBy(() -> TxFlow.fromYaml(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max_rollback_retries cannot be negative");
    }

    @Test
    void fromYaml_shouldRejectInvalidRetryBounds() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "context:\n" +
                "  retry:\n" +
                "    max_attempts: 0\n" +
                "flow:\n" +
                "  id: invalid-retry-bounds\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        tx:\n" +
                "          from: addr1\n";

        // Then
        assertThatThrownBy(() -> TxFlow.fromYaml(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max_attempts must be positive");
    }

    @Test
    void fromYaml_shouldRejectNegativeRetryDelay() {
        // Given
        String yaml = "version: \"1.0\"\n" +
                "context:\n" +
                "  retry:\n" +
                "    initial_delay: -1s\n" +
                "flow:\n" +
                "  id: invalid-retry-delay\n" +
                "  steps:\n" +
                "    - step:\n" +
                "        id: step1\n" +
                "        tx:\n" +
                "          from: addr1\n";

        // Then
        assertThatThrownBy(() -> TxFlow.fromYaml(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initial_delay cannot be negative");
    }
}
