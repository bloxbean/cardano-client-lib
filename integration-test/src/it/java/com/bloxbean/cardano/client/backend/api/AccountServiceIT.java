package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.*;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceIT extends BaseITTest {

    BackendService backendService;
    AccountService accountService;

    @BeforeEach
    public void setup() {
        backendService = getBackendService();
        accountService = backendService.getAccountService();
    }

    @Test
    void testGetAccountInfo() throws ApiException {
        Result<AccountInformation> result = accountService.getAccountInformation("stake_test1up340au593kkqx8tdvwgg367dvydxc8laxuhujxwwwq78sgjpw2sm");
        assertTrue(result.isSuccessful());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }

    @Test
    void testGetAccountRewardsHistory() throws ApiException {
        String stakeAddress = "stake_test1up340au593kkqx8tdvwgg367dvydxc8laxuhujxwwwq78sgjpw2sm";
        Result<List<AccountRewardsHistory>> result = accountService.getAccountRewardsHistory(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountRewardsHistory> accountRewardsHistories = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountRewardsHistories));
    }

    @Test
    void testGetAccountHistory() throws ApiException {
        String stakeAddress = "stake_test1up340au593kkqx8tdvwgg367dvydxc8laxuhujxwwwq78sgjpw2sm";
        Result<List<AccountHistory>> result = accountService.getAccountHistory(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountHistory> accountRewardsHistories = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountRewardsHistories));
    }

    @Test
    void testGetAccountAddresses() throws ApiException {
        String stakeAddress = "stake_test1up340au593kkqx8tdvwgg367dvydxc8laxuhujxwwwq78sgjpw2sm";
        Result<List<AccountAddress>> result = accountService.getAccountAddresses(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountAddress> accountAddresses = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountAddresses));
    }

    @Test
    void testGetAllAccountAddresses() throws ApiException {
        String stakeAddress = "stake_test1up340au593kkqx8tdvwgg367dvydxc8laxuhujxwwwq78sgjpw2sm";
        Result<List<AccountAddress>> result = accountService.getAllAccountAddresses(stakeAddress);
        assertTrue(result.isSuccessful());
        List<AccountAddress> accountAddresses = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountAddresses));
        //TODO Find account with more than 100 addresses
    }

    @Test
    void testGetAccountAssets() throws ApiException {
        String stakeAddress = "stake_test1upxeg0r67z4wca682l28ghg69jxaxgswdmpvnher7at697qmhymyp";
        Result<List<AccountAsset>> result = accountService.getAccountAssets(stakeAddress, 50, 1);
        assertTrue(result.isSuccessful());
        List<AccountAsset> accountAssets = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountAssets));
    }

    @Test
    void testGetAllAccountAssets() throws ApiException {
        String stakeAddress = "stake_test1upxeg0r67z4wca682l28ghg69jxaxgswdmpvnher7at697qmhymyp";
        Result<List<AccountAsset>> result = accountService.getAllAccountAssets(stakeAddress);
        assertTrue(result.isSuccessful());
        List<AccountAsset> accountAssets = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(accountAssets));
        //TODO Find account with more than 100 assets
    }

    @Test
    void testGetAllAccountTransactions() throws ApiException {
        String stakeAddress = "stake_test1upxeg0r67z4wca682l28ghg69jxaxgswdmpvnher7at697qmhymyp";
        Result<List<AddressTransactionContent>> result = accountService.getAllAccountTransactions(stakeAddress, OrderEnum.asc, 605746, 615700);
        assertTrue(result.isSuccessful());
        List<AddressTransactionContent> addressTransactionContents = result.getValue();
        assertEquals(22, addressTransactionContents.size());
        assertEquals("bef3e28ad884c3e50d40465726da389c4c288d486a47bc700c5d273d0516ea01", addressTransactionContents.get(0).getTxHash());
        System.out.println(JsonUtil.getPrettyJson(addressTransactionContents));
    }
}

