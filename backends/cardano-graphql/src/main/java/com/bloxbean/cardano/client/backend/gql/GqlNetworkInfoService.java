package com.bloxbean.cardano.client.backend.gql;

import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.gql.NetworkInfoQuery;
import okhttp3.OkHttpClient;

import java.math.BigDecimal;
import java.util.Map;

public class GqlNetworkInfoService extends BaseGqlService implements NetworkInfoService {

    public GqlNetworkInfoService(String gqlUrl) {
        super(gqlUrl);
    }

    public GqlNetworkInfoService(String gqlUrl, Map<String, String> headers) {
        super(gqlUrl, headers);
    }

    public GqlNetworkInfoService(String gqlUrl, OkHttpClient okHttpClient) {
        super(gqlUrl, okHttpClient);
    }

    @Override
    public Result<Genesis> getNetworkInfo() throws ApiException {
        NetworkInfoQuery query = new NetworkInfoQuery();
        NetworkInfoQuery.Data data = execute(query);
        if(data == null)
            return Result.error("Unable to find network info");

        NetworkInfoQuery.Genesis genesisData = data.genesis();
        if(genesisData == null)
            return Result.error("Genesis data not found");
        NetworkInfoQuery.Shelley shelley = genesisData.shelley();

        Genesis genesis = new Genesis();
        genesis.setActiveSlotsCoefficient(new BigDecimal(shelley.activeSlotsCoeff()));
        genesis.setUpdateQuorum(shelley.updateQuorum());
        genesis.setMaxLovelaceSupply(shelley.maxLovelaceSupply());
        genesis.setNetworkMagic(shelley.networkMagic());
        genesis.setEpochLength(shelley.epochLength());
        genesis.setSlotsPerKesPeriod(shelley.slotsPerKESPeriod());
        genesis.setSlotLength(shelley.slotLength());
        genesis.setMaxKesEvolutions(shelley.maxKESEvolutions());
        genesis.setSecurityParam(shelley.securityParam());

        return processSuccessResult(genesis);
    }
}
