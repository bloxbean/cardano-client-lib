package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import io.adabox.client.OgmiosWSClient;
import io.adabox.model.query.response.CurrentProtocolParameters;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class OgmiosEpochService implements EpochService {

    private final OgmiosWSClient client;

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
        protocolParams.setMinFeeA(currentProtocolParameters.getProtocolParameters().getMinFeeCoefficient());
        protocolParams.setMinFeeB(currentProtocolParameters.getProtocolParameters().getMinFeeConstant());
        protocolParams.setMaxBlockSize(currentProtocolParameters.getProtocolParameters().getMaxBlockBodySize());
        protocolParams.setMaxTxSize(currentProtocolParameters.getProtocolParameters().getMaxTxSize());
        protocolParams.setMaxBlockHeaderSize(currentProtocolParameters.getProtocolParameters().getMaxBlockHeaderSize());
        protocolParams.setKeyDeposit(currentProtocolParameters.getProtocolParameters().getStakeKeyDeposit());
        protocolParams.setPoolDeposit(currentProtocolParameters.getProtocolParameters().getPoolDeposit());
        protocolParams.setEMax(currentProtocolParameters.getProtocolParameters().getPoolRetirementEpochBound());
        protocolParams.setNOpt(currentProtocolParameters.getProtocolParameters().getDesiredNumberOfPools());
        protocolParams.setA0(stringToDecimal(currentProtocolParameters.getProtocolParameters().getPoolInfluence()));
        protocolParams.setRho(stringToDecimal(currentProtocolParameters.getProtocolParameters().getMonetaryExpansion()));
        protocolParams.setTau(stringToDecimal(currentProtocolParameters.getProtocolParameters().getTreasuryExpansion()));
        protocolParams.setDecentralisationParam(stringToDecimal(currentProtocolParameters.getProtocolParameters().getDecentralizationParameter())); //Deprecated. Not there
        protocolParams.setExtraEntropy(currentProtocolParameters.getProtocolParameters().getExtraEntropy());
        protocolParams.setProtocolMajorVer(currentProtocolParameters.getProtocolParameters().getProtocolVersion().getMajor());
        protocolParams.setProtocolMinorVer(currentProtocolParameters.getProtocolParameters().getProtocolVersion().getMinor());
        protocolParams.setMinUtxo(currentProtocolParameters.getProtocolParameters().getMinUtxoValue()); //TODO
        protocolParams.setMinPoolCost(currentProtocolParameters.getProtocolParameters().getMinPoolCost());
//        protocolParams.setNonce(currentProtocolParameters.getProtocolParameters().getNonce()); //TODO

        Map<String, Long> plutusV1CostModel = currentProtocolParameters.getProtocolParameters().getCostModels().get("plutus:v1");
        Map<String, Long> plutusV2CostModel = currentProtocolParameters.getProtocolParameters().getCostModels().get("plutus:v2");
        Map<String, Map<String, Long>> costModels = new HashMap<>();
        costModels.put("PlutusV1", plutusV1CostModel);
        costModels.put("PlutusV2", plutusV2CostModel);
        protocolParams.setCostModels(costModels);

        protocolParams.setPriceMem(stringToDecimal(currentProtocolParameters.getProtocolParameters().getPrices().getMemory()));
        protocolParams.setPriceStep(stringToDecimal(currentProtocolParameters.getProtocolParameters().getPrices().getSteps()));
        protocolParams.setMaxTxExMem(currentProtocolParameters.getProtocolParameters().getMaxExecutionUnitsPerTransaction().getMemory());
        protocolParams.setMaxTxExSteps(currentProtocolParameters.getProtocolParameters().getMaxExecutionUnitsPerTransaction().getSteps());
        protocolParams.setMaxBlockExMem(currentProtocolParameters.getProtocolParameters().getMaxExecutionUnitsPerBlock().getMemory());
        protocolParams.setMaxBlockExSteps(currentProtocolParameters.getProtocolParameters().getMaxExecutionUnitsPerBlock().getSteps());
        protocolParams.setMaxValSize(currentProtocolParameters.getProtocolParameters().getMaxValueSize());
        protocolParams.setCollateralPercent(currentProtocolParameters.getProtocolParameters().getCollateralPercentage());
        protocolParams.setMaxCollateralInputs(currentProtocolParameters.getProtocolParameters().getMaxCollateralInputs());
        protocolParams.setCoinsPerUtxoSize(currentProtocolParameters.getProtocolParameters().getCoinsPerUtxoByte());
        protocolParams.setCoinsPerUtxoWord(currentProtocolParameters.getProtocolParameters().getCoinsPerUtxoWord());
        return protocolParams;
    }

    /**
     * Convert a string to BigDecimal. String format :  a/b
     * @param str str
     * @return BigDecimal
     */
    private static BigDecimal stringToDecimal(String str) {
        try {
            if (str == null || str.isEmpty()) {
                return null;
            }
            String[] parts = str.split("/");
            double d = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            return new BigDecimal(d);
        } catch (Exception e) {
            log.error("Error in conversion", e);
            log.error("Error converting {} to BigDecimal", str);
            return BigDecimal.ZERO;
        }
    }
}
