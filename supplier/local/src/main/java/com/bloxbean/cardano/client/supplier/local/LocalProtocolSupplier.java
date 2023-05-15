package com.bloxbean.cardano.client.supplier.local;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Special;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.core.model.ProtocolParamUpdate;
import com.bloxbean.cardano.yaci.core.protocol.localstate.queries.CurrentProtocolParamQueryResult;
import com.bloxbean.cardano.yaci.core.protocol.localstate.queries.CurrentProtocolParamsQuery;
import com.bloxbean.cardano.yaci.core.util.CborSerializationUtil;
import com.bloxbean.cardano.yaci.helper.LocalStateQueryClient;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class LocalProtocolSupplier implements ProtocolParamsSupplier {
    public static final String PLUTUS_V_1 = "PlutusV1";
    public static final String PLUTUS_V_2 = "PlutusV2";
    private LocalStateQueryClient localStateQueryClient;

    public LocalProtocolSupplier(LocalStateQueryClient localStateQueryClient) {
        this.localStateQueryClient = localStateQueryClient;
    }

    @Override
    public ProtocolParams getProtocolParams() {
        CurrentProtocolParamQueryResult currentProtocolParameters =
                (CurrentProtocolParamQueryResult) localStateQueryClient.executeQuery(new CurrentProtocolParamsQuery()).block();

        ProtocolParamUpdate protocolParamUpdate = currentProtocolParameters.getProtocolParams();

        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setMinFeeA(protocolParamUpdate.getMinFeeA());
        protocolParams.setMinFeeB(protocolParamUpdate.getMinFeeB());
        protocolParams.setMaxBlockSize(protocolParamUpdate.getMaxBlockSize());
        protocolParams.setMaxTxSize(protocolParamUpdate.getMaxTxSize());
        protocolParams.setMaxBlockHeaderSize(protocolParamUpdate.getMaxBlockHeaderSize());
        protocolParams.setKeyDeposit(String.valueOf(protocolParamUpdate.getKeyDeposit()));
        protocolParams.setPoolDeposit(String.valueOf(protocolParamUpdate.getPoolDeposit()));
        protocolParams.setEMax(protocolParamUpdate.getMaxEpoch());
        protocolParams.setNOpt(protocolParamUpdate.getNOpt());
        protocolParams.setA0(protocolParamUpdate.getPoolPledgeInfluence());
        protocolParams.setRho(protocolParamUpdate.getExpansionRate());
        protocolParams.setTau(protocolParamUpdate.getTreasuryGrowthRate());
        protocolParams.setDecentralisationParam(protocolParamUpdate.getDecentralisationParam()); //Deprecated. Not there
        protocolParams.setExtraEntropy(protocolParamUpdate.getExtraEntropy());
        protocolParams.setProtocolMajorVer(protocolParamUpdate.getProtocolMajorVer());
        protocolParams.setProtocolMinorVer(protocolParamUpdate.getProtocolMinorVer());
        protocolParams.setMinUtxo(String.valueOf(protocolParamUpdate.getMinUtxo()));
        protocolParams.setMinPoolCost(String.valueOf(protocolParamUpdate.getMinPoolCost()));
//        protocolParams.setNonce(currentProtocolParameters.getProtocolParameters().getNonce()); //TODO

        Map<String, Long> plutusV1CostModel = cborToCostModel(protocolParamUpdate.getCostModels().get(0));
        Map<String, Long> plutusV2CostModel = cborToCostModel(protocolParamUpdate.getCostModels().get(1));

        Map<String, Map<String, Long>> costModels = new HashMap<>();
        costModels.put(PLUTUS_V_1, plutusV1CostModel);
        costModels.put(PLUTUS_V_2, plutusV2CostModel);
        protocolParams.setCostModels(costModels);

        protocolParams.setPriceMem(protocolParamUpdate.getPriceMem());
        protocolParams.setPriceStep(protocolParamUpdate.getPriceStep());
        protocolParams.setMaxTxExMem(String.valueOf(protocolParamUpdate.getMaxTxExMem()));
        protocolParams.setMaxTxExSteps(String.valueOf(protocolParamUpdate.getMaxTxExSteps()));
        protocolParams.setMaxBlockExMem(String.valueOf(protocolParamUpdate.getMaxBlockExMem()));
        protocolParams.setMaxBlockExSteps(String.valueOf(protocolParamUpdate.getMaxBlockExSteps()));
        protocolParams.setMaxValSize(String.valueOf(protocolParamUpdate.getMaxValSize()));
        protocolParams.setCollateralPercent(BigDecimal.valueOf(protocolParamUpdate.getCollateralPercent()));
        protocolParams.setMaxCollateralInputs(protocolParamUpdate.getMaxCollateralInputs());
        protocolParams.setCoinsPerUtxoSize(String.valueOf(protocolParamUpdate.getAdaPerUtxoByte()));
        return protocolParams;
    }

    private Map<String, Long> cborToCostModel(String costModelCbor) {
        Array array = (Array) CborSerializationUtil.deserializeOne(HexUtil.decodeHexString(costModelCbor));
        Map<String, Long> costModel = new HashMap<>();
        int index = 0;
        for (DataItem di : array.getDataItems()) {
            if (di == Special.BREAK)
                continue;
            BigInteger val = ((UnsignedInteger) di).getValue();
            costModel.put(String.format("%03d", index++), val.longValue());
        }

        return costModel;
    }
}
