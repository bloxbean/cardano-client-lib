package com.bloxbean.cardano.client.api.util;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.plutus.spec.CostModel;
import com.bloxbean.cardano.client.plutus.spec.Language;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CostModelUtilTest {

    @Test
    void getCostModelFromProtocolParams() {
        LinkedHashMap<String, List<Long>> costModelsRaw = new LinkedHashMap<>();
        costModelsRaw.put("PlutusV1", Arrays.asList(1021L, 205665L, 10L, 31220L));
        costModelsRaw.put("PlutusV2", Arrays.asList(812L, 205665L, 10L, 1000L));

        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setCostModelsRaw(costModelsRaw);

        Optional<CostModel> v1CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V1);
        Optional<CostModel> v2CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V2);

        assertThat(v1CostModel.get().getCosts()).isEqualTo(new long[]{1021L, 205665L, 10L, 31220L});
        assertThat(v2CostModel.get().getCosts()).isEqualTo(new long[]{812L, 205665L, 10L, 1000L});
    }

    @Test
    void getCostModelFromProtocolParams_whenNotExists_returnsNull() {
        LinkedHashMap<String, List<Long>> costModelsRaw = new LinkedHashMap<>();
        costModelsRaw.put("PlutusV1", Arrays.asList(1021L, 205665L, 10L, 31220L));

        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setCostModelsRaw(costModelsRaw);

        Optional<CostModel> v2CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V2);

        assertThat(v2CostModel).isEmpty();
    }

    @Test
    void getCostModelFromProtocolParams_whenOnlyNamedCostModelExists_returnsNamed() {
        LinkedHashMap<String, Long> namedV1 = new LinkedHashMap<>();
        namedV1.put("addInteger-cpu-arguments-intercept", 1L);
        namedV1.put("addInteger-cpu-arguments-slope", 2L);

        LinkedHashMap<String, LinkedHashMap<String, Long>> costModels = new LinkedHashMap<>();
        costModels.put("PlutusV1", namedV1);

        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setCostModels(costModels);

        Optional<CostModel> v1CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V1);

        assertThat(v1CostModel.get().getCosts()).isEqualTo(new long[]{1L, 2L});
    }

    @Test
    void getCostModelFromProtocolParams_whenNoCostModelExists_returnsNull() {
        ProtocolParams protocolParams = new ProtocolParams();
        Optional<CostModel> v2CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V2);

        assertThat(v2CostModel).isEmpty();
    }

    @Test
    void getCostModelFromProtocolParams_whenRawV3Exists_prefersRaw() {
        LinkedHashMap<String, Long> namedV3 = new LinkedHashMap<>();
        namedV3.put("addInteger-cpu-arguments-intercept", 1L);
        namedV3.put("addInteger-cpu-arguments-slope", 2L);

        LinkedHashMap<String, LinkedHashMap<String, Long>> costModels = new LinkedHashMap<>();
        costModels.put("PlutusV3", namedV3);

        LinkedHashMap<String, List<Long>> costModelsRaw = new LinkedHashMap<>();
        costModelsRaw.put("PlutusV3", Arrays.asList(10L, 20L, 30L));

        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setCostModels(costModels);
        protocolParams.setCostModelsRaw(costModelsRaw);

        Optional<CostModel> v3CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V3);

        assertThat(v3CostModel.get().getCosts()).isEqualTo(new long[]{10L, 20L, 30L});
    }

    @Test
    void getCostModelFromProtocolParams_whenRawV1Exists_prefersRaw() {
        LinkedHashMap<String, Long> namedV1 = new LinkedHashMap<>();
        namedV1.put("addInteger-cpu-arguments-intercept", 1L);
        namedV1.put("addInteger-cpu-arguments-slope", 2L);

        LinkedHashMap<String, LinkedHashMap<String, Long>> costModels = new LinkedHashMap<>();
        costModels.put("PlutusV1", namedV1);

        LinkedHashMap<String, List<Long>> costModelsRaw = new LinkedHashMap<>();
        costModelsRaw.put("PlutusV1", Arrays.asList(10L, 20L, 30L));

        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setCostModels(costModels);
        protocolParams.setCostModelsRaw(costModelsRaw);

        Optional<CostModel> v1CostModel = CostModelUtil.getCostModelFromProtocolParams(protocolParams, Language.PLUTUS_V1);

        assertThat(v1CostModel.get().getCosts()).isEqualTo(new long[]{10L, 20L, 30L});
    }

    @Test
    void getDefaultCostModels() {
        assertThat(CostModelUtil.PlutusV1CostModel.getCosts()).hasSize(166);
        assertThat(CostModelUtil.PlutusV2CostModel.getCosts()).hasSize(175);
        assertThat(CostModelUtil.PlutusV3CostModel.getCosts()).hasSize(251);
    }
}
