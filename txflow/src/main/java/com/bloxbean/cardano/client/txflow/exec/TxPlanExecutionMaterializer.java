package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.intent.CollectFromIntent;
import com.bloxbean.cardano.client.quicktx.intent.FlowOutputRef;
import com.bloxbean.cardano.client.quicktx.intent.ReferenceInputIntent;
import com.bloxbean.cardano.client.quicktx.intent.ScriptCollectFromIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.quicktx.intent.UtxoRef;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates an isolated {@link TxPlan} for an execution attempt and resolves
 * explicit flow-output references.
 *
 * <p>The source plan is snapshotted and reparsed before mutation, preventing
 * retries or concurrent executions from sharing mutable intent instances.
 * Run-level variables fill only names not already defined by the plan. Each
 * {@link FlowOutputRef} is then replaced with the concrete UTxO captured for the
 * referenced step output.</p>
 *
 * <p>This helper only materializes QuickTx input; it neither signs nor submits a
 * transaction.</p>
 */
final class TxPlanExecutionMaterializer {
    Set<String> referencedStepIds(TxPlan source) {
        Set<String> stepIds = new LinkedHashSet<>();
        for (var transaction : source.getTxs()) {
            if (transaction.getIntentions() == null) continue;
            for (TxIntent intent : transaction.getIntentions()) {
                if (intent instanceof CollectFromIntent) {
                    collectStepIds(((CollectFromIntent) intent).getUtxoRefs(), stepIds);
                } else if (intent instanceof ReferenceInputIntent) {
                    collectStepIds(((ReferenceInputIntent) intent).getRefs(), stepIds);
                } else if (intent instanceof ScriptCollectFromIntent) {
                    collectStepIds(((ScriptCollectFromIntent) intent).getUtxoRefs(), stepIds);
                }
            }
        }
        return Set.copyOf(stepIds);
    }

    TxPlan materialize(TxPlan source, Map<String, Object> flowVariables,
                       FlowExecutionContext context) {
        // Snapshot the mutable source once per execution, then parse a fresh copy for
        // each attempt so retries remain isolated without repeatedly serializing it.
        TxPlan copy = TxPlan.from(context.snapshotPlan(source));
        for (Map.Entry<String, Object> entry : flowVariables.entrySet()) {
            if (!copy.getVariables().containsKey(entry.getKey())) {
                copy.addVariable(entry.getKey(), entry.getValue());
            }
        }
        for (var transaction : copy.getTxs()) {
            if (transaction.getIntentions() == null) continue;
            for (TxIntent intent : transaction.getIntentions()) {
                if (intent instanceof CollectFromIntent) {
                    CollectFromIntent collect = (CollectFromIntent) intent;
                    collect.setUtxoRefs(resolve(collect.getUtxoRefs(), context));
                } else if (intent instanceof ReferenceInputIntent) {
                    ReferenceInputIntent reference = (ReferenceInputIntent) intent;
                    reference.setRefs(resolve(reference.getRefs(), context));
                } else if (intent instanceof ScriptCollectFromIntent) {
                    ScriptCollectFromIntent collect = (ScriptCollectFromIntent) intent;
                    collect.setUtxoRefs(resolve(collect.getUtxoRefs(), context));
                }
            }
        }
        return copy;
    }

    private List<UtxoRef> resolve(List<UtxoRef> refs, FlowExecutionContext context) {
        if (refs == null || refs.isEmpty()) return refs;
        List<UtxoRef> resolved = new ArrayList<>(refs.size());
        for (UtxoRef ref : refs) {
            if (ref instanceof FlowOutputRef) {
                FlowOutputRef.Pointer pointer = ((FlowOutputRef) ref).getFlowOutput();
                if (pointer == null || pointer.getStep() == null || pointer.getOutput() == null) {
                    throw new FlowExecutionException("Invalid flow_output reference");
                }
                Utxo utxo = context.getNamedOutput(pointer.getStep(), pointer.getOutput())
                        .orElseThrow(() -> new FlowExecutionException(
                                "Unresolved flow output " + pointer.getStep() + "." + pointer.getOutput()));
                resolved.add(UtxoRef.fromUtxo(utxo));
            } else {
                resolved.add(ref);
            }
        }
        return resolved;
    }

    private void collectStepIds(List<UtxoRef> refs, Set<String> stepIds) {
        if (refs == null) return;
        for (UtxoRef ref : refs) {
            if (ref instanceof FlowOutputRef) {
                FlowOutputRef.Pointer pointer = ((FlowOutputRef) ref).getFlowOutput();
                if (pointer != null && pointer.getStep() != null) {
                    stepIds.add(pointer.getStep());
                }
            }
        }
    }
}
