package com.bloxbean.cardano.client.api.util;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.plutus.spec.CostModel;
import com.bloxbean.cardano.client.plutus.spec.Language;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CostModelUtilTest {

    @Test
    void getCostModelFromProtocolParams() {
        LinkedHashMap<String, Long> costModel1 = new LinkedHashMap<>();
        costModel1.put("verifyEd25519Signature-cpu-arguments-slope", 1021L);
        costModel1.put("addInteger-cpu-arguments-intercept", 205665L);
        costModel1.put("verifyEd25519Signature-memory-arguments", 10L);
        costModel1.put("unBData-cpu-arguments", 31220L);

        LinkedHashMap<String, Long> costModel2 = new LinkedHashMap<>();
        costModel2.put("addInteger-cpu-arguments-slope", 812L);
        costModel2.put("addInteger-cpu-arguments-intercept", 205665L);
        costModel2.put("verifyEd25519Signature-memory-arguments", 10L);
        costModel2.put("bData-cpu-arguments", 1000L);

        LinkedHashMap<String, LinkedHashMap<String, Long>> costModels = new LinkedHashMap<>();
        costModels.put("PlutusV1", costModel1);
        costModels.put("PlutusV2", costModel2);
        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setCostModels(costModels);

        Optional<CostModel> v1CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V1);
        Optional<CostModel> v2CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V2);

        assertThat(v1CostModel.get().getCosts()).isEqualTo(new long[]{1021L, 205665L, 10L, 31220L});
        assertThat(v2CostModel.get().getCosts()).isEqualTo(new long[]{812L, 205665L, 10L, 1000L});
    }

    @Test
    void getCostModelFromProtocolParams_whenNotExists_returnsNull() {
        LinkedHashMap<String, Long> costModel1 = new LinkedHashMap<>();
        costModel1.put("verifyEd25519Signature-cpu-arguments-slope", 1021L);
                costModel1.put("addInteger-cpu-arguments-intercept", 205665L);
                costModel1.put("verifyEd25519Signature-memory-arguments", 10L);
                costModel1.put("unBData-cpu-arguments", 31220L);

        LinkedHashMap<String, LinkedHashMap<String, Long>> costModels = new LinkedHashMap<>();
        costModels.put("PlutusV1", costModel1);
        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setCostModels(costModels);

        Optional<CostModel> v2CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V2);

        assertThat(v2CostModel).isEmpty();
    }

    @Test
    void getCostModelFromProtocolParams_whenNoCostModelExists_returnsNull() {
        ProtocolParams protocolParams = new ProtocolParams();
        Optional<CostModel> v2CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V2);

        assertThat(v2CostModel).isEmpty();
    }

    @Test
    void getDefaultCostModels() {
        assertThat(CostModelUtil.PlutusV1CostModel.getCosts()).hasSize(166);
        assertThat(CostModelUtil.PlutusV2CostModel.getCosts()).hasSize(175);
        assertThat(CostModelUtil.PlutusV3CostModel.getCosts()).hasSize(251);
    }
}
