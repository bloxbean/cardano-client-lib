package com.bloxbean.cardano.client.txflow.codec;

import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.StepDependency;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates that an in-memory flow has a lossless portable v1alpha1 projection.
 *
 * <p>The portable writer and compiler deliberately share this validator. That
 * prevents an execution plan from being fingerprinted with a representation
 * that omitted Java-only behavior. Diagnostics use document paths so build
 * tools and server callers can point authors to the unsupported construct.</p>
 */
public final class PortableFlowValidator {
    private PortableFlowValidator() {
    }

    /**
     * Reports every known construct that portable v1alpha1 cannot encode without
     * changing behavior.
     *
     * @param flow flow definition to inspect
     * @return immutable, path-specific error diagnostics
     */
    public static List<FlowDiagnostic> validate(TxFlow flow) {
        Objects.requireNonNull(flow, "flow");
        List<FlowDiagnostic> diagnostics = new ArrayList<>();
        if (!flow.getVariables().isEmpty()) {
            diagnostics.add(error("TXFLOW_NON_PORTABLE_FLOW_VARIABLES",
                    "Legacy flow variables are not portable; declare parameters and provide "
                            + "FlowBindings at execution time",
                    "$.variables"));
        }
        flow.getAnnotations().forEach((name, value) -> {
            if (name == null || value == null) {
                diagnostics.add(error("TXFLOW_NON_PORTABLE_ANNOTATION",
                        "Portable annotation names and values must be non-null strings",
                        "$.metadata.annotations"));
            }
        });

        validateExecutionSettings(flow, diagnostics);
        for (int index = 0; index < flow.getSteps().size(); index++) {
            FlowStep step = flow.getSteps().get(index);
            String path = "$.spec.steps[" + index + "]";
            for (int dependencyIndex = 0;
                 dependencyIndex < step.getDependencies().size(); dependencyIndex++) {
                StepDependency dependency = step.getDependencies().get(dependencyIndex);
                diagnostics.add(error("TXFLOW_NON_PORTABLE_DEPENDENCY",
                        "Step '" + step.getId() + "' uses legacy " + dependency.getStrategy()
                                + " output selection from '" + dependency.getStepId()
                                + "'; use needs(...) for ordering and bindOutput(...) with a "
                                + "flow_output reference for consumption",
                        path + ".depends_on[" + dependencyIndex + "]"));
            }
            if (step.hasRetryPolicy()) {
                diagnostics.add(error("TXFLOW_NON_PORTABLE_STEP_RETRY",
                        "Step-level retry policies are not portable; use spec.execution.retry",
                        path + ".retry"));
            }
            if (step.hasTxContextFactory()) {
                diagnostics.add(error("TXFLOW_NON_PORTABLE_FACTORY",
                        "Java transaction factories cannot be compiled as a portable plan",
                        path));
            }
            if (step.hasTxPlan()) {
                if (!step.getTxPlan().getVariables().isEmpty()) {
                    diagnostics.add(error("TXFLOW_NON_PORTABLE_TXPLAN_VARIABLES",
                            "TxPlan variables are not portable; declare parameters and provide "
                                    + "FlowBindings at execution time",
                            path + ".transaction.variables"));
                }
                if (step.getTxPlan().getTxs().size() != 1) {
                    diagnostics.add(error("TXFLOW_NON_PORTABLE_TXPLAN_CARDINALITY",
                            "A portable step must contain exactly one QuickTx transaction",
                            path + ".transaction"));
                }
            }
        }
        return List.copyOf(diagnostics);
    }

    private static void validateExecutionSettings(TxFlow flow,
                                                  List<FlowDiagnostic> diagnostics) {
        ConfirmationConfig confirmation = flow.getExecutionSettings().getConfirmationConfig();
        if (confirmation != null && !hasDefaultCompatibilityFields(confirmation)) {
            diagnostics.add(error("TXFLOW_NON_PORTABLE_CONFIRMATION_COMPATIBILITY",
                    "Legacy confirmation rollback/backend-wait settings are not represented by "
                            + "portable v1alpha1; keep their ordinary defaults and configure "
                            + "portable rollback separately",
                    "$.spec.execution.confirmation"));
        }
        if (flow.getExecutionSettings().getRetryPolicy() != null
                && (!flow.getExecutionSettings().getRetryPolicy().isRetryOnTimeout()
                || !flow.getExecutionSettings().getRetryPolicy().isRetryOnNetworkError())) {
            diagnostics.add(error("TXFLOW_NON_PORTABLE_RETRY_FILTERS",
                    "retryOnTimeout=false and retryOnNetworkError=false are not represented by "
                            + "portable v1alpha1",
                    "$.spec.execution.retry"));
        }
        if (flow.getExecutionSettings().getRollbackStrategy() != null) {
            diagnostics.add(error("TXFLOW_NON_PORTABLE_ROLLBACK_STRATEGY",
                    "Legacy RollbackStrategy has no lossless portable representation; use "
                            + "RollbackPolicy",
                    "$.spec.execution.rollback"));
        }
    }

    private static boolean hasDefaultCompatibilityFields(ConfirmationConfig left) {
        ConfirmationConfig right = ConfirmationConfig.defaults();
        return left.getMaxRollbackRetries() == right.getMaxRollbackRetries()
                && left.isWaitForBackendAfterRollback() == right.isWaitForBackendAfterRollback()
                && left.getPostRollbackWaitAttempts() == right.getPostRollbackWaitAttempts()
                && left.getPostRollbackUtxoSyncDelay().equals(right.getPostRollbackUtxoSyncDelay())
                && left.getRequiredAuthoritativeAbsences()
                == right.getRequiredAuthoritativeAbsences();
    }

    private static FlowDiagnostic error(String code, String message, String path) {
        return FlowDiagnostic.error(code, message, path);
    }
}
