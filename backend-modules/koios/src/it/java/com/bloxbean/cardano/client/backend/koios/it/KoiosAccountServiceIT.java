package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.AccountService;
import com.bloxbean.cardano.client.backend.model.*;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KoiosAccountServiceIT extends KoiosBaseTest {

    private AccountService accountService;

    @BeforeEach
    public void setup() {
        accountService = backendService.getAccountService();
    }

    @Test
    void testGetAccountInfo() throws ApiException {
        Result<AccountInformation> result = accountService.getAccountInformation("stake_test1uzcmuv8c6pj3ld9mrvml3jhxl7j4hvh4xskr6ce37dvpfdqjmdvh8");
        assertTrue(result.isSuccessful());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }

    @Test
    void testGetAccountRewardsHistory() throws ApiException {
        String stakeAddress = "stake_test1uzcmuv8c6pj3ld9mrvml3jhxl7j4hvh4xskr6ce37dvpfdqjmdvh8";
        Result<List<AccountRewardsHistory>> result = accountService.getAccountRewardsHistory(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountRewardsHistory> accountRewardsHistories = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountRewardsHistories));
    }

    @Test
    void testGetAccountHistory() throws ApiException {
        String stakeAddress = "stake_test1uzcmuv8c6pj3ld9mrvml3jhxl7j4hvh4xskr6ce37dvpfdqjmdvh8";
        Result<List<AccountHistory>> result = accountService.getAccountHistory(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountHistory> accountRewardsHistories = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountRewardsHistories));
    }

    @Test
    void testGetAccountAddresses() throws ApiException {
        String stakeAddress = "stake_test1uzcmuv8c6pj3ld9mrvml3jhxl7j4hvh4xskr6ce37dvpfdqjmdvh8";
        Result<List<AccountAddress>> result = accountService.getAccountAddresses(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountAddress> accountAddresses = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountAddresses));
    }

    @Test
    void testGetAllAccountAddresses() throws ApiException {
        String stakeAddress = "stake_test1uzcmuv8c6pj3ld9mrvml3jhxl7j4hvh4xskr6ce37dvpfdqjmdvh8";
        Result<List<AccountAddress>> result = accountService.getAllAccountAddresses(stakeAddress);
        assertTrue(result.isSuccessful());
        List<AccountAddress> accountAddresses = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountAddresses));
        //TODO Find account with more than 1000 addresses
    }

    @Test
    void testGetAccountAssets() throws ApiException {
        String stakeAddress = "stake_test1uzcmuv8c6pj3ld9mrvml3jhxl7j4hvh4xskr6ce37dvpfdqjmdvh8";
        Result<List<AccountAsset>> result = accountService.getAccountAssets(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountAsset> accountAssetList = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountAssetList));
    }

    @Test
    void testGetAllAccountAssets() throws ApiException {
        String stakeAddress = "stake_test1upxeg0r67z4wca682l28ghg69jxaxgswdmpvnher7at697qmhymyp";
        Result<List<AccountAsset>> result = accountService.getAllAccountAssets(stakeAddress);
        assertTrue(result.isSuccessful());
        List<AccountAsset> accountAssets = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountAssets));
        //TODO Find account with more than 1000 assets
    }
}
