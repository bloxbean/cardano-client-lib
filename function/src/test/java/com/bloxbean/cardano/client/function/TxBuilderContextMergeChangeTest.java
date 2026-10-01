package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TxBuilderContextMergeChangeTest {
    ProtocolParams protocolParams = ProtocolParams.builder().coinsPerUtxoSize("4310").build();

    @Test
    void mergeChange_followsMergeOutputs_byDefault() {
        TxBuilderContext context = new TxBuilderContext(null, protocolParams);

        assertThat(context.isMergeOutputs()).isTrue();
        assertThat(context.isMergeChange()).isTrue();

        context.mergeOutputs(false);
        assertThat(context.isMergeChange()).isFalse();
    }

    @Test
    void mergeChange_false_overridesMergeOutputsTrue() {
        TxBuilderContext context = new TxBuilderContext(null, protocolParams).mergeOutputs(true).mergeChange(false);

        assertThat(context.isMergeOutputs()).isTrue();
        assertThat(context.isMergeChange()).isFalse();
    }
}
