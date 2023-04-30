package com.bloxbean.cardano.client.api.util;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.plutus.spec.CostModel;
import com.bloxbean.cardano.client.plutus.spec.Language;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CostModelUtilTest {

    @Test
    void getCostModelFromProtocolParams() {
        Map<String, Long> costModel1 = Map.of("verifyEd25519Signature-cpu-arguments-slope", 1021L,
                "addInteger-cpu-arguments-intercept", 205665L,
                "verifyEd25519Signature-memory-arguments", 10L,
                "unBData-cpu-arguments", 31220L);

        Map<String, Long> costModel2 = Map.of("addInteger-cpu-arguments-slope", 812L,
                "addInteger-cpu-arguments-intercept", 205665L,
                "verifyEd25519Signature-memory-arguments", 10L,
                "bData-cpu-arguments", 1000L);

        Map<String, Map<String, Long>> costModels = Map.of("PlutusV1", costModel1,
                                                            "PlutusV2", costModel2);
        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setCostModels(costModels);

        Optional<CostModel> v1CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V1);
        Optional<CostModel> v2CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V2);

        assertThat(v1CostModel.get().getCosts()).isEqualTo(new long[]{205665L, 31220L, 1021L, 10L});
        assertThat(v2CostModel.get().getCosts()).isEqualTo(new long[]{205665L, 812L, 1000L, 10L});
    }

    @Test
    void getCostModelFromProtocolParams_whenNotExists_returnsNull() {
        Map<String, Long> costModel1 = Map.of("verifyEd25519Signature-cpu-arguments-slope", 1021L,
                "addInteger-cpu-arguments-intercept", 205665L,
                "verifyEd25519Signature-memory-arguments", 10L,
                "unBData-cpu-arguments", 31220L);

        Map<String, Map<String, Long>> costModels = Map.of("PlutusV1", costModel1);
        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setCostModels(costModels);

        Optional<CostModel>  v2CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V2);

        assertThat(v2CostModel).isEmpty();
    }

    @Test
    void getCostModelFromProtocolParams_whenNoCostModelExists_returnsNull() {
        ProtocolParams protocolParams = new ProtocolParams();
        Optional<CostModel>   v2CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V2);

        assertThat(v2CostModel).isEmpty();
    }
}
