package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.backend.ogmios.model.query.response.CurrentProtocolParameters;

import java.util.HashMap;
import java.util.Map;

public class OgmiosEpochService implements EpochService {

    private OgmiosWSClient client;

    public OgmiosEpochService(OgmiosWSClient client) {
        this.client = client;
    }


    @Override
    public Result<EpochContent> getLatestEpoch() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<EpochContent> getEpoch(Integer epoch) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters(Integer epoch) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters() {
        CurrentProtocolParameters currentProtocolParameters = client.currentProtocolParameters();

        if (currentProtocolParameters != null && currentProtocolParameters.getFault() == null) {
            ProtocolParams protocolParams = copyProtocolParams(currentProtocolParameters);
            return Result.success("OK").withValue(protocolParams).code(200);
        } else {
            return Result.error(String.valueOf(currentProtocolParameters))
                    .code(500);
        }
    }

    private ProtocolParams copyProtocolParams(CurrentProtocolParameters currentProtocolParameters) {
        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setMinFeeA(currentProtocolParameters.getMinFeeA());
        protocolParams.setMinFeeB(currentProtocolParameters.getMinFeeB());
        protocolParams.setMaxBlockSize(currentProtocolParameters.getMaxBlockSize());
        protocolParams.setMaxTxSize(currentProtocolParameters.getMaxTxSize());
        protocolParams.setMaxBlockHeaderSize(currentProtocolParameters.getMaxBlockHeaderSize());
        protocolParams.setKeyDeposit(currentProtocolParameters.getKeyDeposit());
        protocolParams.setPoolDeposit(currentProtocolParameters.getPoolDeposit());
        protocolParams.setEMax(currentProtocolParameters.getEMax());
        protocolParams.setNOpt(currentProtocolParameters.getNOpt());
        protocolParams.setA0(currentProtocolParameters.getA0());
        protocolParams.setRho(currentProtocolParameters.getRho());
        protocolParams.setTau(currentProtocolParameters.getTau());
        protocolParams.setDecentralisationParam(currentProtocolParameters.getDecentralisationParam()); //Deprecated. Not there
        protocolParams.setExtraEntropy(currentProtocolParameters.getExtraEntropy()); //TODO check if there
        protocolParams.setProtocolMajorVer(currentProtocolParameters.getProtocolMajorVer());
        protocolParams.setProtocolMinorVer(currentProtocolParameters.getProtocolMinorVer());
        protocolParams.setMinUtxo(currentProtocolParameters.getMinUtxo()); //TODO
        protocolParams.setMinPoolCost(currentProtocolParameters.getMinPoolCost());
        protocolParams.setNonce(currentProtocolParameters.getNonce()); //TODO

        Map<String, Long> plutusV1CostModel = currentProtocolParameters.getCostModels().get("plutus:v1");
        Map<String, Long> plutusV2CostModel = currentProtocolParameters.getCostModels().get("plutus:v2");
        Map<String, Map<String, Long>> costModels = new HashMap<>();
        costModels.put("PlutusV1", plutusV1CostModel);
        costModels.put("PlutusV2", plutusV2CostModel);
        protocolParams.setCostModels(costModels);

        protocolParams.setPriceMem(currentProtocolParameters.getPriceMem());
        protocolParams.setPriceStep(currentProtocolParameters.getPriceStep());
        protocolParams.setMaxTxExMem(currentProtocolParameters.getMaxTxExMem());
        protocolParams.setMaxTxExSteps(currentProtocolParameters.getMaxTxExSteps());
        protocolParams.setMaxBlockExMem(currentProtocolParameters.getMaxBlockExMem());
        protocolParams.setMaxBlockExSteps(currentProtocolParameters.getMaxBlockExSteps());
        protocolParams.setMaxValSize(currentProtocolParameters.getMaxValSize());
        protocolParams.setCollateralPercent(currentProtocolParameters.getCollateralPercent());
        protocolParams.setMaxCollateralInputs(currentProtocolParameters.getMaxCollateralInputs());
        protocolParams.setCoinsPerUtxoSize(currentProtocolParameters.getCoinsPerUtxoSize());

        return protocolParams;
    }
}
