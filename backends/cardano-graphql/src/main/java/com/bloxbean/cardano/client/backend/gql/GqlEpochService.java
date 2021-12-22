package com.bloxbean.cardano.client.backend.gql;

import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.gql.EpochQuery;
import com.bloxbean.cardano.gql.LatestEpochQuery;
import com.bloxbean.cardano.gql.ProtocolParamQuery;
import com.bloxbean.cardano.gql.fragment.EpochFragment;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class GqlEpochService extends BaseGqlService implements EpochService {
    private final static Logger logger = LoggerFactory.getLogger(GqlEpochService.class);

    public GqlEpochService(String gqlUrl) {
        super(gqlUrl);
    }

    public GqlEpochService(String gqlUrl, Map<String, String> headers) {
        super(gqlUrl, headers);
    }

    public GqlEpochService(String gqlUrl, OkHttpClient okHttpClient) {
        super(gqlUrl, okHttpClient);
    }

    @Override
    public Result<EpochContent> getLatestEpoch() throws ApiException {
        LatestEpochQuery query = new LatestEpochQuery();
        LatestEpochQuery.Data data = execute(query);
        if(data == null)
            return Result.error("Unable to find latest epoch");

        List<LatestEpochQuery.Epoch> epochs = data.epochs();
        if(epochs == null || epochs.size() == 0)
            return Result.error("No epochs found");

        EpochFragment latestEpoch = epochs.get(0).fragments().epochFragment();
        EpochContent epochContent = new EpochContent();
        epochContent.setEpoch(latestEpoch.number());
        epochContent.setBlockCount(asInt(latestEpoch.blocksCount()));
        if(latestEpoch.fees() != null)
            epochContent.setFees(latestEpoch.fees().toString());
//        epochContent.setLastBlockTime(latestEpoch.lastBlockTime());
        //TODO set other fields.
        return processSuccessResult(epochContent);
    }

    @Override
    public Result<EpochContent> getEpoch(Integer epochNumber) throws ApiException {
        EpochQuery query = new EpochQuery(epochNumber);
        EpochQuery.Data data = execute(query);
        if(data == null)
            return Result.error("Unable to find epoch : " + epochNumber);

        List<EpochQuery.Epoch> epochs = data.epochs();
        if(epochs == null || epochs.size() == 0)
            return Result.error("No epochs found");

        EpochFragment epoch = epochs.get(0).fragments().epochFragment();
        EpochContent epochContent = new EpochContent();
        epochContent.setEpoch(epoch.number());
        epochContent.setBlockCount(asInt(epoch.blocksCount()));
        if(epoch.fees() != null)
            epochContent.setFees(epoch.fees().toString());
//        epochContent.setLastBlockTime(latestEpoch.lastBlockTime());
        //TODO set other fields.
        return processSuccessResult(epochContent);
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters(Integer epochNumber) throws ApiException {
        ProtocolParamQuery query = new ProtocolParamQuery(epochNumber);
        ProtocolParamQuery.Data data = execute(query);
        if(data == null)
            return Result.error("Unable to find protocol parameters for epoch : " + epochNumber);

        List<ProtocolParamQuery.Epoch> epochs = data.epochs();
        if(epochs == null || epochs.size() == 0)
            return Result.error("No epochs found");

        ProtocolParamQuery.Epoch epoch = epochs.get(0);
        ProtocolParamQuery.ProtocolParams epochProtocolParams = epoch.protocolParams();
        if(epochProtocolParams == null)
            return Result.error("Protocol params not found for the epoch: " + epochNumber);

        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setA0(new BigDecimal(epochProtocolParams.a0()));
        protocolParams.setDecentralisationParam(new BigDecimal(epochProtocolParams.decentralisationParam()));
        protocolParams.setEMax(epochProtocolParams.eMax());
        protocolParams.setExtraEntropy(epochProtocolParams.extraEntropy());
        protocolParams.setKeyDeposit(String.valueOf(epochProtocolParams.keyDeposit()));
        protocolParams.setMaxBlockHeaderSize(epochProtocolParams.maxBlockHeaderSize());
        protocolParams.setMaxBlockSize(epochProtocolParams.maxBlockBodySize());
        protocolParams.setMaxTxSize(epochProtocolParams.maxTxSize());
        protocolParams.setMinFeeA(epochProtocolParams.minFeeA());
        protocolParams.setMinFeeB(epochProtocolParams.minFeeB());
        protocolParams.setMinPoolCost(String.valueOf(epochProtocolParams.minPoolCost()));
        protocolParams.setMinUtxo(String.valueOf(epochProtocolParams.minUTxOValue()));
        protocolParams.setNOpt(epochProtocolParams.nOpt());
        protocolParams.setPoolDeposit(String.valueOf(epochProtocolParams.poolDeposit()));
        //TODO protocol major version and minor version and other fields
        if(epochProtocolParams.coinsPerUtxoWord() != null && epochProtocolParams.coinsPerUtxoWord() != 0)
            protocolParams.setCoinsPerUtxoWord(String.valueOf(epochProtocolParams.coinsPerUtxoWord()));
        else
            protocolParams.setCoinsPerUtxoWord("34482"); //TODO As current value returned from gql endpoint is zero. Fix later.

        if(epochProtocolParams.collateralPercent() != null) {
            protocolParams.setCollateralPercent(new BigDecimal(epochProtocolParams.collateralPercent()));
        }
        if(epochProtocolParams.maxCollateralInputs() != null) {
            protocolParams.setMaxCollateralInputs(epochProtocolParams.maxCollateralInputs());
        }

        return processSuccessResult(protocolParams);
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters() throws ApiException {
        Result<EpochContent> epochContentResult = getLatestEpoch();
        if(!epochContentResult.isSuccessful())
            return Result.error("Latest epoch not found");

        EpochContent content = epochContentResult.getValue();
        Integer latestEpoch = content.getEpoch();

        return getProtocolParameters(latestEpoch);
    }

    private Integer asInt(String blocksCount) {
        try {
            return Integer.parseInt(blocksCount);
        } catch (Exception e) {
            return 0;
        }
    }
}
