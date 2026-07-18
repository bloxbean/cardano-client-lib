package com.bloxbean.cardano.client.txflow.compile;

import com.bloxbean.cardano.client.txflow.TxFlow;

import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Output of successful TxFlow compilation.
 *
 * <p>The execution plan contains bound, materialized transaction plans. The
 * fingerprint is derived from its canonical portable representation. Resource
 * identities and producer-to-consumer relationships are precomputed for runtime
 * coordination and rollback decisions. The metadata collections are deeply
 * immutable; callers should treat the returned execution plan as read-only.</p>
 */
public final class CompiledTxFlow {
    private final TxFlow executionPlan;
    private final String fingerprint;
    private final Set<String> spendingResources;
    private final Map<String, Set<String>> explicitConsumers;

    CompiledTxFlow(TxFlow executionPlan, String fingerprint, Set<String> spendingResources,
                   Map<String, Set<String>> explicitConsumers) {
        this.executionPlan = Objects.requireNonNull(executionPlan, "executionPlan");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.spendingResources = Set.copyOf(spendingResources);
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        explicitConsumers.forEach((producer, consumers) ->
                copy.put(producer, Set.copyOf(consumers)));
        this.explicitConsumers = Map.copyOf(copy);
    }

    /**
     * Returns the bound execution plan ready for the runtime.
     *
     * @return execution plan
     */
    public TxFlow getExecutionPlan() {
        return executionPlan;
    }

    /**
     * Returns the deterministic SHA-256 fingerprint of the compiled portable plan.
     *
     * @return lowercase fingerprint text
     */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Returns canonical identities of resources that may be spent by this plan.
     *
     * @return immutable set of spending-resource identities
     */
    public Set<String> getSpendingResources() {
        return spendingResources;
    }

    /**
     * Returns producer step IDs mapped to steps that explicitly consume one of
     * their named outputs.
     *
     * @return deeply immutable producer-to-consumer mapping
     */
    public Map<String, Set<String>> getExplicitConsumers() {
        return explicitConsumers;
    }
}
