package com.bloxbean.cardano.client.txflow.compile;

import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionPolicy;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.resource.InMemoryFlowResourceCatalog;
import com.bloxbean.cardano.client.txflow.resource.ResourceCapability;
import com.bloxbean.cardano.client.txflow.resource.ResourceDescriptor;
import com.bloxbean.cardano.client.txflow.resource.ResourceRef;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePolicyPreflightTest {
    private final TxFlowCompiler compiler = new TxFlowCompiler();

    @Test
    void registeredCapabilitiesAndNetworkPassPreflight() {
        TxFlow definition = definition();
        InMemoryFlowResourceCatalog catalog = new InMemoryFlowResourceCatalog()
                .register(new ResourceDescriptor(ResourceRef.of("account://treasury"), "preview",
                        Set.of(ResourceCapability.SPEND, ResourceCapability.SIGN), "treasury"));

        FlowCompilationResult result = compiler.compile(FlowCompilationRequest.builder(definition)
                .bindings(FlowBindings.empty())
                .resources(catalog)
                .policy(FlowExecutionPolicy.builder().allowedNetworks(Set.of("preview")).build())
                .build());

        assertFalse(result.hasErrors(), result.getDiagnostics().toString());
        assertTrue(result.requireCompiledFlow().getSpendingResources().contains("treasury"));
    }

    @Test
    void missingResourceAndPolicyLimitFailBeforeExecution() {
        TxFlow definition = definition();
        FlowCompilationResult missing = compiler.compile(FlowCompilationRequest.builder(definition)
                .resources(new InMemoryFlowResourceCatalog())
                .build());
        assertTrue(missing.hasErrors());
        assertTrue(missing.getDiagnostics().stream().anyMatch(d -> d.code().equals("TXFLOW_RESOURCE_MISSING")));

        FlowCompilationResult policy = compiler.compile(FlowCompilationRequest.builder(definition)
                .policy(FlowExecutionPolicy.builder().allowedNetworks(Set.of("mainnet")).build())
                .build());
        assertTrue(policy.hasErrors());
        assertTrue(policy.getDiagnostics().stream().anyMatch(d -> d.code().equals("TXFLOW_POLICY_NETWORK")));
    }

    @Test
    void validityIntervalCanBeRequiredByServerPolicy() {
        FlowCompilationResult result = compiler.compile(FlowCompilationRequest.builder(definition())
                .policy(FlowExecutionPolicy.builder().requireValidityInterval(true).build())
                .build());
        assertTrue(result.hasErrors());
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("TXFLOW_POLICY_VALIDITY_REQUIRED")));
    }

    @Test
    void numericSettingsAreCappedWithWarningOrRejectedInStrictMode() {
        TxFlow definition = TxFlow.builder("capped")
                .withExecutionSettings(com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings.builder()
                        .retryPolicy(com.bloxbean.cardano.client.txflow.RetryPolicy.builder()
                                .maxAttempts(5).build())
                        .build())
                .addStep(com.bloxbean.cardano.client.txflow.FlowStep.builder("one")
                        .withTxPlan(com.bloxbean.cardano.client.quicktx.serialization.TxPlan.from(
                                "version: '1.0'\ntransaction:\n  - tx: {intents: []}\n"))
                        .build())
                .build();
        FlowCompilationResult capped = compiler.compile(FlowCompilationRequest.builder(definition)
                .policy(FlowExecutionPolicy.builder().maxRetryAttempts(2).build()).build());
        assertFalse(capped.hasErrors(), capped.getDiagnostics().toString());
        assertTrue(capped.getDiagnostics().stream().anyMatch(d -> d.code().equals("TXFLOW_POLICY_RETRY_CAPPED")));
        assertTrue(capped.requireCompiledFlow().getExecutionPlan().getExecutionSettings()
                .getRetryPolicy().getMaxAttempts() == 2);

        FlowCompilationResult strict = compiler.compile(FlowCompilationRequest.builder(definition)
                .policy(FlowExecutionPolicy.builder().maxRetryAttempts(2).strictSettings(true).build()).build());
        assertTrue(strict.hasErrors());
    }

    @Test
    void resourcePrefixAuthorizationIsEnforced() {
        InMemoryFlowResourceCatalog catalog = new InMemoryFlowResourceCatalog()
                .register(new ResourceDescriptor(ResourceRef.of("account://treasury"), "preview",
                        Set.of(ResourceCapability.SPEND, ResourceCapability.SIGN), "treasury"));
        FlowCompilationResult result = compiler.compile(FlowCompilationRequest.builder(definition())
                .resources(catalog)
                .policy(FlowExecutionPolicy.builder()
                        .allowedResourcePrefixes(Set.of("account://approved"))
                        .maxConfirmationTimeout(Duration.ofMinutes(1)).build())
                .build());
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("TXFLOW_RESOURCE_UNAUTHORIZED")));
    }

    @Test
    void portableRollbackAndValidityRequestsAreCappedOrSemanticallyRejected() {
        String yaml = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: policy-settings}
                spec:
                  execution:
                    rollback:
                      action: RECONCILE_AND_REBUILD
                      monitoring_horizon: UNTIL_FLOW_TERMINAL
                      rebuild_scope: INVALIDATED_CLOSURE
                      max_recovery_cycles: 5
                      reinclusion_window: 1m
                      minimum_consistent_absence_observations: 2
                    validity: {window: 2h, resubmit_safety_margin: 10}
                  steps:
                    - id: one
                      transaction: {tx: {intents: []}}
                """;
        TxFlow requested = TxFlowCodec.standard().parse(
                yaml, FlowParseOptions.serverDefaults()).requireFlow();
        FlowCompilationResult capped = compiler.compile(FlowCompilationRequest.builder(requested)
                .policy(FlowExecutionPolicy.builder().maxRollbackRecoveryCycles(2)
                        .maxRequestedValidityWindow(Duration.ofMinutes(30)).build()).build());
        assertFalse(capped.hasErrors(), capped.getDiagnostics().toString());
        assertTrue(capped.requireCompiledFlow().getExecutionPlan().getExecutionSettings()
                .getRollbackPolicy().maxRecoveryCycles() == 2);
        assertTrue(capped.requireCompiledFlow().getExecutionPlan().getExecutionSettings()
                .getValidityPolicy().window().equals(Duration.ofMinutes(30)));

        FlowCompilationResult rejected = compiler.compile(FlowCompilationRequest.builder(requested)
                .policy(FlowExecutionPolicy.builder().allowedRollbackActions(Set.of(
                        com.bloxbean.cardano.client.txflow.config.RollbackAction.FAIL)).build())
                .build());
        assertTrue(rejected.hasErrors());
        assertTrue(rejected.getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("TXFLOW_POLICY_ROLLBACK_ACTION")));
    }

    private TxFlow definition() {
        String yaml = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: resource-flow}
                spec:
                  network: preview
                  steps:
                    - id: pay
                      transaction:
                        tx:
                          from_ref: account://treasury
                          intents: []
                        context:
                          fee_payer_ref: account://treasury
                          signers:
                            - {ref: account://treasury, scope: payment}
                """;
        return TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();
    }
}
