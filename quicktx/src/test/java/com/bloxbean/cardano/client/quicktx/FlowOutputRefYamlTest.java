package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.quicktx.intent.CollectFromIntent;
import com.bloxbean.cardano.client.quicktx.intent.FlowOutputRef;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowOutputRefYamlTest {
    @Test
    void flowOutputReferenceRoundTripsThroughSharedQuickTxContract() {
        String yaml = """
                version: 1.0
                transaction:
                  - tx:
                      inputs:
                        - type: collect_from
                          refs:
                            - flow_output:
                                step: funding
                                output: staging
                """;

        TxPlan plan = TxPlan.from(yaml);
        CollectFromIntent intent = (CollectFromIntent) plan.getTxs().get(0).getIntentions().get(0);

        assertThat(intent.getUtxoRefs()).singleElement().isInstanceOf(FlowOutputRef.class);
        FlowOutputRef ref = (FlowOutputRef) intent.getUtxoRefs().get(0);
        assertThat(ref.getFlowOutput().getStep()).isEqualTo("funding");
        assertThat(ref.getFlowOutput().getOutput()).isEqualTo("staging");
        assertThat(plan.toYaml()).contains("flow_output", "funding", "staging");
    }
}
