package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.account.model.AccountInformation;
import adlabs.nexus.client.backend.api.account.model.AccountTransaction;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusAccountServiceTest {

    private static final adlabs.nexus.client.util.Network NET = adlabs.nexus.client.util.Network.MAINNET;

    @Test
    void getAccountInformation_maps() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        AccountInformation info = AccountInformation.builder()
                .active(true)
                .controlledAmount("1000000")
                .rewardsSum("5000")
                .reservesSum("0")
                .withdrawalsSum("1000")
                .treasurySum("0")
                .withdrawableAmount("4000")
                .poolId("pool1abc")
                .build();
        when(sdkAccountSvc.getAccountInformation(eq(NET), eq("stake1xyz")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, info));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<com.bloxbean.cardano.client.backend.model.AccountInformation> r = svc.getAccountInformation("stake1xyz");

        assertThat(r.isSuccessful()).isTrue();
        com.bloxbean.cardano.client.backend.model.AccountInformation ai = r.getValue();
        assertThat(ai.getActive()).isTrue();
        assertThat(ai.getControlledAmount()).isEqualTo("1000000");
        assertThat(ai.getRewardsSum()).isEqualTo("5000");
        assertThat(ai.getReservesSum()).isEqualTo("0");
        assertThat(ai.getWithdrawalsSum()).isEqualTo("1000");
        assertThat(ai.getTreasurySum()).isEqualTo("0");
        assertThat(ai.getWithdrawableAmount()).isEqualTo("4000");
        assertThat(ai.getPool_id()).isEqualTo("pool1abc");
    }

    @Test
    void getAccountInformation_error_propagates() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountInformation(any(), any()))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<com.bloxbean.cardano.client.backend.model.AccountInformation> r = svc.getAccountInformation("stake1missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getAccountInformation_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountInformation(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAccountInformation("stake1xyz"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    // ---- getAccountRewardsHistory ----

    private static List<adlabs.nexus.client.backend.api.account.model.AccountRewardsHistory> fourRewardRows() {
        return List.of(
                adlabs.nexus.client.backend.api.account.model.AccountRewardsHistory.builder()
                        .epoch(210).amount("1000").poolId("pool1a").type("member").spendableEpoch(212).stakeAddress("stake1xyz").build(),
                adlabs.nexus.client.backend.api.account.model.AccountRewardsHistory.builder()
                        .epoch(211).amount("1100").poolId("pool1a").type("member").spendableEpoch(213).stakeAddress("stake1xyz").build(),
                adlabs.nexus.client.backend.api.account.model.AccountRewardsHistory.builder()
                        .epoch(212).amount("1200").poolId("pool1a").type("member").spendableEpoch(214).stakeAddress("stake1xyz").build(),
                adlabs.nexus.client.backend.api.account.model.AccountRewardsHistory.builder()
                        .epoch(213).amount("1300").poolId("pool1a").type("member").spendableEpoch(215).stakeAddress("stake1xyz").build()
        );
    }

    @Test
    void getAccountRewardsHistory_mapsFields_andPaginatesPage1() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountRewards(eq(NET), eq("stake1xyz")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, fourRewardRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AccountRewardsHistory>> r =
                svc.getAccountRewardsHistory("stake1xyz", 2, 1);

        assertThat(r.isSuccessful()).isTrue();
        List<com.bloxbean.cardano.client.backend.model.AccountRewardsHistory> value = r.getValue();
        assertThat(value).hasSize(2);
        assertThat(value.get(0).getEpoch()).isEqualTo(210);
        assertThat(value.get(0).getAmount()).isEqualTo("1000");
        assertThat(value.get(0).getPoolId()).isEqualTo("pool1a");
        assertThat(value.get(0).getType()).isEqualTo("member");
        assertThat(value.get(1).getEpoch()).isEqualTo(211);
    }

    @Test
    void getAccountRewardsHistory_paginatesPage2() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountRewards(eq(NET), eq("stake1xyz")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, fourRewardRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AccountRewardsHistory>> r =
                svc.getAccountRewardsHistory("stake1xyz", 2, 2);

        assertThat(r.getValue()).hasSize(2);
        assertThat(r.getValue().get(0).getEpoch()).isEqualTo(212);
        assertThat(r.getValue().get(1).getEpoch()).isEqualTo(213);
    }

    @Test
    void getAccountRewardsHistory_orderIgnored() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountRewards(eq(NET), eq("stake1xyz")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, fourRewardRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AccountRewardsHistory>> ascLike =
                svc.getAccountRewardsHistory("stake1xyz", 4, 1, OrderEnum.asc);
        Result<List<com.bloxbean.cardano.client.backend.model.AccountRewardsHistory>> descLike =
                svc.getAccountRewardsHistory("stake1xyz", 4, 1, OrderEnum.desc);

        // order has no effect: both variants return the same (source) ordering.
        assertThat(ascLike.getValue().get(0).getEpoch()).isEqualTo(210);
        assertThat(descLike.getValue().get(0).getEpoch()).isEqualTo(210);
    }

    @Test
    void getAccountRewardsHistory_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountRewards(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("rewards boom"));

        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAccountRewardsHistory("stake1xyz", 10, 1))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("rewards boom");
    }

    // ---- getAllAccountAddresses / getAccountAddresses ----

    private static List<adlabs.nexus.client.backend.api.account.model.AccountAddress> threeAddressRows() {
        return List.of(
                adlabs.nexus.client.backend.api.account.model.AccountAddress.builder().address("addr1aaa").build(),
                adlabs.nexus.client.backend.api.account.model.AccountAddress.builder().address("addr1bbb").build(),
                adlabs.nexus.client.backend.api.account.model.AccountAddress.builder().address("addr1ccc").build()
        );
    }

    @Test
    void getAllAccountAddresses_mapsFullList() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountAddresses(eq(NET), eq("stake1xyz")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, threeAddressRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AccountAddress>> r = svc.getAllAccountAddresses("stake1xyz");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).extracting(com.bloxbean.cardano.client.backend.model.AccountAddress::getAddress)
                .containsExactly("addr1aaa", "addr1bbb", "addr1ccc");
    }

    @Test
    void getAccountAddresses_paginatesPage1AndPage2() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountAddresses(eq(NET), eq("stake1xyz")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, threeAddressRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AccountAddress>> page1 =
                svc.getAccountAddresses("stake1xyz", 2, 1);
        Result<List<com.bloxbean.cardano.client.backend.model.AccountAddress>> page2 =
                svc.getAccountAddresses("stake1xyz", 2, 2);

        assertThat(page1.getValue()).extracting(com.bloxbean.cardano.client.backend.model.AccountAddress::getAddress)
                .containsExactly("addr1aaa", "addr1bbb");
        assertThat(page2.getValue()).extracting(com.bloxbean.cardano.client.backend.model.AccountAddress::getAddress)
                .containsExactly("addr1ccc");
    }

    @Test
    void getAccountAddresses_withOrder_delegatesSamePagination() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountAddresses(eq(NET), eq("stake1xyz")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, threeAddressRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AccountAddress>> r =
                svc.getAccountAddresses("stake1xyz", 2, 1, OrderEnum.desc);

        assertThat(r.getValue()).extracting(com.bloxbean.cardano.client.backend.model.AccountAddress::getAddress)
                .containsExactly("addr1aaa", "addr1bbb");
    }

    @Test
    void getAllAccountAddresses_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountAddresses(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("addresses boom"));

        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAllAccountAddresses("stake1xyz"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("addresses boom");
    }

    // ---- getAccountTransactions / getAllAccountTransactions ----

    private static List<AccountTransaction> fourTxRows() {
        return List.of(
                AccountTransaction.builder().txHash("tx1").epochNo(210).blockHeight(100).blockTime(1000L).build(),
                AccountTransaction.builder().txHash("tx2").epochNo(210).blockHeight(200).blockTime(2000L).build(),
                AccountTransaction.builder().txHash("tx3").epochNo(211).blockHeight(300).blockTime(3000L).build(),
                AccountTransaction.builder().txHash("tx4").epochNo(211).blockHeight(400).blockTime(4000L).build()
        );
    }

    @Test
    void getAccountTransactions_mapsFields_usesFromBlockHeight_andPaginates() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountTransactions(eq(NET), eq("stake1xyz"), eq(150)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, fourTxRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AddressTransactionContent>> r =
                svc.getAccountTransactions("stake1xyz", 2, 1, null, 150, null);

        assertThat(r.isSuccessful()).isTrue();
        List<com.bloxbean.cardano.client.backend.model.AddressTransactionContent> value = r.getValue();
        assertThat(value).hasSize(2);
        assertThat(value.get(0).getTxHash()).isEqualTo("tx1");
        assertThat(value.get(0).getTxIndex()).isEqualTo(0);
        assertThat(value.get(0).getBlockHeight()).isEqualTo(100L);
        assertThat(value.get(0).getBlockTime()).isEqualTo(1000L);
        assertThat(value.get(1).getTxHash()).isEqualTo("tx2");
    }

    @Test
    void getAccountTransactions_nullFromBlockHeight_defaultsToOne() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountTransactions(eq(NET), eq("stake1xyz"), eq(1)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, fourTxRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AddressTransactionContent>> r =
                svc.getAccountTransactions("stake1xyz", 10, 1, null, null, null);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(4);
    }

    @Test
    void getAccountTransactions_explicitFromBlockHeight_usedAsIs() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountTransactions(eq(NET), eq("stake1xyz"), eq(150)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, fourTxRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AddressTransactionContent>> r =
                svc.getAccountTransactions("stake1xyz", 10, 1, null, 150, null);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(4);
    }

    @Test
    void getAccountTransactions_toBlockHeightFiltersClientSide() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountTransactions(eq(NET), eq("stake1xyz"), eq(1)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, fourTxRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AddressTransactionContent>> r =
                svc.getAccountTransactions("stake1xyz", 10, 1, null, null, 300);

        assertThat(r.getValue()).extracting(com.bloxbean.cardano.client.backend.model.AddressTransactionContent::getTxHash)
                .containsExactly("tx1", "tx2", "tx3");
    }

    @Test
    void getAccountTransactions_descOrderReversesBeforePagination() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountTransactions(eq(NET), eq("stake1xyz"), eq(1)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, fourTxRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AddressTransactionContent>> r =
                svc.getAccountTransactions("stake1xyz", 2, 1, OrderEnum.desc, null, null);

        assertThat(r.getValue()).extracting(com.bloxbean.cardano.client.backend.model.AddressTransactionContent::getTxHash)
                .containsExactly("tx4", "tx3");
    }

    @Test
    void getAccountTransactions_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountTransactions(any(), any(), any(Integer.class)))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("tx boom"));

        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAccountTransactions("stake1xyz", 10, 1, null, null, null))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("tx boom");
    }

    @Test
    void getAllAccountTransactions_fullList_toBlockHeightFilter_andDescReverse() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountTransactions(eq(NET), eq("stake1xyz"), eq(1)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, fourTxRows()));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<List<com.bloxbean.cardano.client.backend.model.AddressTransactionContent>> r =
                svc.getAllAccountTransactions("stake1xyz", OrderEnum.desc, null, 300);

        // toBlockHeight=300 keeps tx1..tx3, then desc reverses to tx3,tx2,tx1
        assertThat(r.getValue()).extracting(com.bloxbean.cardano.client.backend.model.AddressTransactionContent::getTxHash)
                .containsExactly("tx3", "tx2", "tx1");
    }

    @Test
    void getAllAccountTransactions_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountTransactions(any(), any(), any(Integer.class)))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("all tx boom"));

        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAllAccountTransactions("stake1xyz", null, null, null))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("all tx boom");
    }

    // ---- still-Unsupported methods ----

    @Test
    void getAccountHistory_stillUnsupported() {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAccountHistory("stake1xyz", 10, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> svc.getAccountHistory("stake1xyz", 10, 1, OrderEnum.asc))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getAccountAssets_stillUnsupported() {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAllAccountAssets("stake1xyz"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> svc.getAccountAssets("stake1xyz", 10, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> svc.getAccountAssets("stake1xyz", 10, 1, OrderEnum.asc))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
