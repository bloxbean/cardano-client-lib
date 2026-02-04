package com.bloxbean.cardano.client.txflow.yaml;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.BackoffStrategy;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.SelectionStrategy;
import com.bloxbean.cardano.client.txflow.TxFlow;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
}
