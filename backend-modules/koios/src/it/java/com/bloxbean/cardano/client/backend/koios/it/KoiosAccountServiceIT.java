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

public class KoiosAccountServiceIT extends KoiosBaseTest {

    private AccountService accountService;

    @BeforeEach
    public void setup() {
        accountService = backendService.getAccountService();
    }

    @Test
    public void testGetAccountInfo() throws ApiException {
        Result<AccountInformation> result = accountService.getAccountInformation("stake_test1uq02x8kk9kcee2uhlw69srl78s2rdu83z6tgjcxceufd7asvp5p2z");
        assertTrue(result.isSuccessful());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }

    @Test
    public void testGetAccountRewardsHistory() throws ApiException {
        String stakeAddress = "stake_test1uq02x8kk9kcee2uhlw69srl78s2rdu83z6tgjcxceufd7asvp5p2z";
        Result<List<AccountRewardsHistory>> result = accountService.getAccountRewardsHistory(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountRewardsHistory> accountRewardsHistories = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountRewardsHistories));
    }

    @Test
    public void testGetAccountHistory() throws ApiException {
        String stakeAddress = "stake_test1uq02x8kk9kcee2uhlw69srl78s2rdu83z6tgjcxceufd7asvp5p2z";
        Result<List<AccountHistory>> result = accountService.getAccountHistory(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountHistory> accountRewardsHistories = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountRewardsHistories));
    }

    @Test
    public void testGetAccountAddresses() throws ApiException {
        String stakeAddress = "stake_test1uq02x8kk9kcee2uhlw69srl78s2rdu83z6tgjcxceufd7asvp5p2z";
        Result<List<AccountAddress>> result = accountService.getAccountAddresses(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountAddress> accountRewardsHistories = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountRewardsHistories));
    }

    @Test
    public void testGetAccountAssets() throws ApiException {
        String stakeAddress = "stake_test1uq02x8kk9kcee2uhlw69srl78s2rdu83z6tgjcxceufd7asvp5p2z";
        Result<List<AccountAsset>> result = accountService.getAccountAssets(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountAsset> accountAssetList = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountAssetList));
    }
}
