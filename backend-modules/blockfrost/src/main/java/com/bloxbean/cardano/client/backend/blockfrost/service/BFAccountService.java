package com.bloxbean.cardano.client.backend.blockfrost.service;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.AccountService;
import com.bloxbean.cardano.client.backend.blockfrost.service.http.AccountApi;
import com.bloxbean.cardano.client.backend.model.*;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

public class BFAccountService extends BFBaseService implements AccountService {

    private final AccountApi accountApi;

    public BFAccountService(String baseUrl, String projectId) {
        super(baseUrl, projectId);
        this.accountApi = getRetrofit().create(AccountApi.class);
    }

    @Override
    public Result<AccountInformation> getAccountInformation(String stakeAddress) throws ApiException {
        Call<AccountInformation> call = accountApi.getAccountInformation(getProjectId(), stakeAddress);
        try {
            Response<AccountInformation> response = call.execute();
            return processResponse(response);
        } catch (IOException e) {
            throw new ApiException("Error getting accountInformation", e);
        }
    }

    @Override
    public Result<List<AccountRewardsHistory>> getAccountRewardsHistory(String stakeAddress, int count, int page) throws ApiException {
        return this.getAccountRewardsHistory(stakeAddress, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AccountRewardsHistory>> getAccountRewardsHistory(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        Call<List<AccountRewardsHistory>> call = accountApi.getAccountRewardsHistory(getProjectId(), stakeAddress, count, page, order.toString());
        try {
            Response<List<AccountRewardsHistory>> response = call.execute();
            return processResponse(response);
        } catch (IOException e) {
            throw new ApiException("Error getting accountInformation", e);
        }
    }

    @Override
    public Result<List<AccountHistory>> getAccountHistory(String stakeAddress, int count, int page) throws ApiException {
        return this.getAccountHistory(stakeAddress, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AccountHistory>> getAccountHistory(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        Call<List<AccountHistory>> call = accountApi.getAccountHistory(getProjectId(), stakeAddress, count, page, order.toString());
        try {
            Response<List<AccountHistory>> response = call.execute();
            return processResponse(response);
        } catch (IOException e) {
            throw new ApiException("Error getting accountInformation", e);
        }
    }

    @Override
    public Result<List<AccountAddress>> getAccountAddresses(String stakeAddress, int count, int page) throws ApiException {
        return this.getAccountAddresses(stakeAddress, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AccountAddress>> getAccountAddresses(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        Call<List<AccountAddress>> call = accountApi.getAccountAddresses(getProjectId(), stakeAddress, count, page, order.toString());
        try {
            Response<List<AccountAddress>> response = call.execute();
            return processResponse(response);
        } catch (IOException e) {
            throw new ApiException("Error getting accountInformation", e);
        }
    }

    @Override
    public Result<List<AccountAsset>> getAccountAssets(String stakeAddress, int count, int page) throws ApiException {
        return this.getAccountAssets(stakeAddress, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AccountAsset>> getAccountAssets(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        Call<List<AccountAsset>> call = accountApi.getAccountAssets(getProjectId(), stakeAddress, count, page, order.toString());
        try {
            Response<List<AccountAsset>> response = call.execute();
            return processResponse(response);
        } catch (IOException e) {
            throw new ApiException("Error getting accountInformation", e);
        }
    }
}
