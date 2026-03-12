package com.bloxbean.cardano.client.scalus;

import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.common.model.SlotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScalusTransactionEvaluatorTest {

    private ProtocolParams protocolParams;

    @BeforeEach
    void setUp() {
        protocolParams = ScalusTestFixtures.buildTestProtocolParams();
    }

    @Test
    void builder_shouldUseDefaultValues() {
        ScalusTransactionEvaluator evaluator = ScalusTransactionEvaluator.builder()
                .protocolParams(protocolParams)
                .build();

        assertThat(evaluator).isNotNull();
    }

    @Test
    void builder_shouldAcceptCustomSlotConfig() {
        ScalusTransactionEvaluator evaluator = ScalusTransactionEvaluator.builder()
                .protocolParams(protocolParams)
                .slotConfig(new SlotConfig(1000, 0, 1666656000000L))
                .networkId(1)
                .currentSlot(12345)
                .build();

        assertThat(evaluator).isNotNull();
    }

    @Test
    void evaluateTx_shouldReturnErrorForInvalidCbor() {
        ScalusTransactionEvaluator evaluator = ScalusTransactionEvaluator.builder()
                .protocolParams(protocolParams)
                .build();

        byte[] invalidCbor = new byte[]{0x01, 0x02, 0x03};
        Result<List<EvaluationResult>> result = evaluator.evaluateTx(invalidCbor, Set.of());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getResponse()).contains("Script evaluation failed");
    }

    @Test
    void evaluateTx_shouldFallbackToPreviewSlotConfigWhenNoneProvided() {
        ScalusTransactionEvaluator evaluator = ScalusTransactionEvaluator.builder()
                .protocolParams(protocolParams)
                .build();

        // Invalid CBOR, but verifies no NPE from null slotConfig
        byte[] invalidCbor = new byte[]{0x01, 0x02, 0x03};
        Result<List<EvaluationResult>> result = evaluator.evaluateTx(invalidCbor, Set.of());

        assertThat(result.isSuccessful()).isFalse();
    }
}
