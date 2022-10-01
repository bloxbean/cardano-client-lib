package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.*;
import com.bloxbean.cardano.client.util.HexUtil;
import rest.koios.client.backend.api.account.AccountService;
import rest.koios.client.backend.api.account.model.AccountHistoryInner;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.account.model.AccountReward;
import rest.koios.client.backend.api.account.model.AccountRewards;
import rest.koios.client.backend.factory.options.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Koios Account Service
 */
public class KoiosAccountService implements com.bloxbean.cardano.client.backend.api.AccountService {

    /**
     * Account Service
     */
    private final AccountService accountService;

    public KoiosAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public Result<AccountInformation> getAccountInformation(String stakeAddress) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<AccountInfo>> accountInformationResult = accountService.getAccountInformation(List.of(stakeAddress), Options.EMPTY);
            if (!accountInformationResult.isSuccessful()) {
                return Result.error(accountInformationResult.getResponse()).code(accountInformationResult.getCode());
            }
            if (accountInformationResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return convertToAccountInformation(accountInformationResult.getValue().get(0));
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<AccountInformation> convertToAccountInformation(AccountInfo accountInfo) {
        AccountInformation accountInformation = new AccountInformation();
        accountInformation.setActive(accountInfo.getStatus().equals("registered"));
        accountInformation.setControlledAmount(accountInfo.getTotalBalance());
        accountInformation.setRewardsSum(accountInfo.getRewards());
        accountInformation.setWithdrawalsSum(accountInfo.getWithdrawals());
        accountInformation.setReservesSum(accountInfo.getReserves());
        accountInformation.setTreasurySum(accountInfo.getTreasury());
        accountInformation.setWithdrawableAmount(accountInfo.getRewardsAvailable());
        accountInformation.setPool_id(accountInfo.getDelegatedPool());
        return Result.success("OK").withValue(accountInformation).code(200);
    }

    @Override
    public Result<List<AccountRewardsHistory>> getAccountRewardsHistory(String stakeAddress, int count, int page) throws ApiException {
        return this.getAccountRewardsHistory(stakeAddress, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AccountRewardsHistory>> getAccountRewardsHistory(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        try {
            Option ordering = Order.by("earned_epoch", SortType.ASC);
            if (order == OrderEnum.desc) {
                ordering = Order.by("earned_epoch", SortType.DESC);
            }
            Options options = Options.builder()
                    .option(Limit.of(count))
                    .option(Offset.of((long) (page - 1) * count))
                    .option(ordering).build();
            rest.koios.client.backend.api.base.Result<List<AccountRewards>> accountRewardsResult = accountService.getAccountRewards(List.of(stakeAddress), null, options);
            if (!accountRewardsResult.isSuccessful()) {
                return Result.error(accountRewardsResult.getResponse()).code(accountRewardsResult.getCode());
            }
            if (accountRewardsResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return convertToAccountRewards(accountRewardsResult.getValue().get(0).getRewards());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<List<AccountRewardsHistory>> convertToAccountRewards(List<AccountReward> accountRewardsList) {
        List<AccountRewardsHistory> accountRewardsHistories = new ArrayList<>();
        if (accountRewardsList != null) {
            accountRewardsList.forEach(accountRewards -> {
                AccountRewardsHistory accountRewardsHistory = new AccountRewardsHistory();
                accountRewardsHistory.setEpoch(accountRewards.getEarnedEpoch());
                accountRewardsHistory.setPoolId(accountRewards.getPoolId());
                accountRewardsHistory.setType(accountRewards.getType());
                accountRewardsHistory.setAmount(accountRewards.getAmount());
                accountRewardsHistories.add(accountRewardsHistory);
            });
        }
        return Result.success("OK").withValue(accountRewardsHistories).code(200);
    }

    @Override
    public Result<List<AccountHistory>> getAccountHistory(String stakeAddress, int count, int page) throws ApiException {
        return this.getAccountHistory(stakeAddress, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AccountHistory>> getAccountHistory(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        try {
            Option ordering = Order.by("epoch_no", SortType.ASC);
            if (order == OrderEnum.desc) {
                ordering = Order.by("epoch_no", SortType.DESC);
            }
            Options options = Options.builder()
                    .option(Limit.of(count))
                    .option(Offset.of((long) (page - 1) * count))
                    .option(ordering).build();
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.account.model.AccountHistory>> accountHistoriesResult = accountService.getAccountHistory(List.of(stakeAddress), null, options);
            if (!accountHistoriesResult.isSuccessful()) {
                return Result.error(accountHistoriesResult.getResponse()).code(accountHistoriesResult.getCode());
            }
            if (accountHistoriesResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return convertToAccountHistories(accountHistoriesResult.getValue().get(0).getHistory());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<List<AccountHistory>> convertToAccountHistories(List<AccountHistoryInner> accountHistories) {
        List<AccountHistory> accountHistoryList = new ArrayList<>();
        if (accountHistories != null) {
            accountHistories.forEach(accountHistory -> {
                AccountHistory accountHist = new AccountHistory();
                accountHist.setAmount(accountHistory.getActiveStake());
                accountHist.setActiveEpoch(accountHistory.getEpochNo());
                accountHist.setPoolId(accountHistory.getPoolId());
                accountHistoryList.add(accountHist);
            });
        }
        return Result.success("OK").withValue(accountHistoryList).code(200);
    }

    @Override
    public Result<List<AccountAddress>> getAccountAddresses(String stakeAddress, int count, int page) throws ApiException {
        return this.getAccountAddresses(stakeAddress, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AccountAddress>> getAccountAddresses(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        try {
            Options options = Options.builder()
                    .option(Limit.of(count))
                    .option(Offset.of((long) (page - 1) * count))
                    .build();
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.account.model.AccountAddress>> accountAddressesResult = accountService.getAccountAddresses(List.of(stakeAddress), null, options);
            if (!accountAddressesResult.isSuccessful()) {
                return Result.error(accountAddressesResult.getResponse()).code(accountAddressesResult.getCode());
            }
            if (accountAddressesResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return convertToAccountAddresses(accountAddressesResult.getValue().get(0).getAddresses());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<List<AccountAddress>> convertToAccountAddresses(List<String> accountAddressList) {
        List<AccountAddress> accountAddresses = new ArrayList<>();
        if (accountAddressList != null) {
            accountAddressList.forEach(accountAddress -> accountAddresses.add(new AccountAddress(accountAddress)));
        }
        return Result.success("OK").withValue(accountAddresses).code(200);
    }

    @Override
    public Result<List<AccountAsset>> getAccountAssets(String stakeAddress, int count, int page) throws ApiException {
        return this.getAccountAssets(stakeAddress, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AccountAsset>> getAccountAssets(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        try {
            Options options = Options.builder()
                    .option(Limit.of(count))
                    .option(Offset.of((long) (page - 1) * count))
                    .build();
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.account.model.AccountAssets>> accountAssetsResult = accountService.getAccountAssets(List.of(stakeAddress), null, options);
            if (!accountAssetsResult.isSuccessful()) {
                return Result.error(accountAssetsResult.getResponse()).code(accountAssetsResult.getCode());
            }
            if (accountAssetsResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return convertToAccountAssets(accountAssetsResult.getValue().get(0).getAssets());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<List<AccountAsset>> convertToAccountAssets(List<rest.koios.client.backend.api.account.model.AccountAsset> accountAssetList) {
        List<AccountAsset> accountAssets = new ArrayList<>();
        if (accountAssetList!=null) {
            accountAssetList.forEach(accountAsset -> {
                accountAsset.getAssets().forEach(assetInner -> {
                    AccountAsset accountAsset1 = new AccountAsset();
                    accountAsset1.setUnit(accountAsset.getPolicyId() + HexUtil.encodeHexString(assetInner.getAssetName().getBytes(StandardCharsets.UTF_8)));
                    accountAsset1.setQuantity(assetInner.getBalance());
                    accountAssets.add(accountAsset1);

                });
            });
        }
        return Result.success("OK").withValue(accountAssets).code(200);
    }
}
