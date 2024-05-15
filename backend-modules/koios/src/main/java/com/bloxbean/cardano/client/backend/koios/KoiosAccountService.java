package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.AccountAddress;
import com.bloxbean.cardano.client.backend.model.AccountAsset;
import com.bloxbean.cardano.client.backend.model.AccountHistory;
import com.bloxbean.cardano.client.backend.model.*;
import rest.koios.client.backend.api.account.AccountService;
import rest.koios.client.backend.api.account.model.*;
import rest.koios.client.backend.factory.options.*;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Koios Account Service
 */
public class KoiosAccountService implements com.bloxbean.cardano.client.backend.api.AccountService {

    /**
     * Account Service
     */
    private final AccountService accountService;

    /**
     * KoiosAccountService Constructor
     *
     * @param accountService accountService
     */
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
            if (page != 1) {
                return Result.success("OK").withValue(Collections.emptyList()).code(200);
            }
            rest.koios.client.backend.api.base.Result<List<AccountRewards>> accountRewardsResult = accountService.getAccountRewards(List.of(stakeAddress), null, Options.EMPTY);
            if (!accountRewardsResult.isSuccessful()) {
                return Result.error(accountRewardsResult.getResponse()).code(accountRewardsResult.getCode());
            }
            if (accountRewardsResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return convertToAccountRewards(accountRewardsResult.getValue().get(0).getRewards(), order);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<List<AccountRewardsHistory>> convertToAccountRewards(List<AccountReward> accountRewardsList, OrderEnum order) {
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
        if (order == OrderEnum.asc) {
            accountRewardsHistories.sort(Comparator.comparing(AccountRewardsHistory::getEpoch));
        } else {
            accountRewardsHistories.sort(Comparator.comparing(AccountRewardsHistory::getEpoch).reversed());
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
            if (page != 1) {
                return Result.success("OK").withValue(Collections.emptyList()).code(200);
            }
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.account.model.AccountHistory>> accountHistoriesResult = accountService.getAccountHistory(List.of(stakeAddress), null, Options.EMPTY);
            if (!accountHistoriesResult.isSuccessful()) {
                return Result.error(accountHistoriesResult.getResponse()).code(accountHistoriesResult.getCode());
            }
            if (accountHistoriesResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return convertToAccountHistories(accountHistoriesResult.getValue().get(0).getHistory(), order);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<List<AccountHistory>> convertToAccountHistories(List<AccountHistoryInner> accountHistories, OrderEnum order) {
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
        if (order == OrderEnum.asc) {
            accountHistoryList.sort(Comparator.comparing(AccountHistory::getActiveEpoch));
        } else {
            accountHistoryList.sort(Comparator.comparing(AccountHistory::getActiveEpoch).reversed());
        }
        return Result.success("OK").withValue(accountHistoryList).code(200);
    }

    @Override
    public Result<List<AccountAddress>> getAllAccountAddresses(String stakeAddress) throws ApiException {
        List<AccountAddress> accountAddresses = new ArrayList<>();
        int page = 1;
        Result<List<AccountAddress>> accountAddressesResult = getAccountAddresses(stakeAddress, 1000, page);
        while (accountAddressesResult.isSuccessful()) {
            accountAddresses.addAll(accountAddressesResult.getValue());
            if (accountAddressesResult.getValue().size() != 1000) {
                break;
            } else {
                page++;
                accountAddressesResult = getAccountAddresses(stakeAddress, 1000, page);
            }
        }
        if (!accountAddressesResult.isSuccessful()) {
            return accountAddressesResult;
        } else {
            return Result.success(accountAddressesResult.toString()).withValue(accountAddresses).code(accountAddressesResult.code());
        }
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
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.account.model.AccountAddress>> accountAddressesResult = accountService.getAccountAddresses(List.of(stakeAddress), false, true, options);
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
    public Result<List<AccountAsset>> getAllAccountAssets(String stakeAddress) throws ApiException {
        List<AccountAsset> accountAssets = new ArrayList<>();
        int page = 1;
        Result<List<AccountAsset>> accountAssetsResult = getAccountAssets(stakeAddress, 1000, page);
        while (accountAssetsResult.isSuccessful()) {
            accountAssets.addAll(accountAssetsResult.getValue());
            if (accountAssetsResult.getValue().size() != 1000) {
                break;
            } else {
                page++;
                accountAssetsResult = getAccountAssets(stakeAddress, 1000, page);
            }
        }
        if (!accountAssetsResult.isSuccessful()) {
            return accountAssetsResult;
        } else {
            return Result.success(accountAssetsResult.toString()).withValue(accountAssets).code(accountAssetsResult.code());
        }
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
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.account.model.AccountAsset>> accountAssetsResult = accountService.getAccountAssets(List.of(stakeAddress), null, options);
            if (!accountAssetsResult.isSuccessful()) {
                return Result.error(accountAssetsResult.getResponse()).code(accountAssetsResult.getCode());
            }
            if (accountAssetsResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return convertToAccountAssets(accountAssetsResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<AddressTransactionContent>> getAccountTransactions(String stakeAddress, int count, int page, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight) throws ApiException {
        try {
            Options options = Options.builder()
                    .option(Limit.of(count))
                    .option(Offset.of((long) (page - 1) * count))
                    .build();
            if (order != null) {
                options.getOptionList().add(Order.by("block_height", order == OrderEnum.asc ? SortType.ASC : SortType.DESC));
            }
            if (toBlockHeight != null) {
                options.getOptionList().add(Filter.of("block_height", FilterType.LTE, toBlockHeight.toString()));
            }
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.account.model.AccountTx>> accountTxsResult =
                    accountService.getAccountTxs(stakeAddress, fromBlockHeight, options);
            if (!accountTxsResult.isSuccessful()) {
                return Result.error(accountTxsResult.getResponse()).code(accountTxsResult.getCode());
            }
            if (accountTxsResult.getValue().isEmpty()) {
                return Result.error("Not Found").code(404);
            }
            return convertToAddressTransactionContent(accountTxsResult.getValue(), order);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<AddressTransactionContent>> getAllAccountTransactions(String stakeAddress, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight) throws ApiException {
        List<AddressTransactionContent> addressTransactionContents = new ArrayList<>();
        int page = 1;
        Result<List<AddressTransactionContent>> addressTransactionsResult = getAccountTransactions(stakeAddress, 1000, page, order, fromBlockHeight, toBlockHeight);
        while (addressTransactionsResult.isSuccessful()) {
            addressTransactionContents.addAll(addressTransactionsResult.getValue());
            if (addressTransactionsResult.getValue().size() != 1000) {
                break;
            } else {
                page++;
                addressTransactionsResult = getAccountTransactions(stakeAddress, 1000, page, order, fromBlockHeight, toBlockHeight);
            }
        }
        if (!addressTransactionsResult.isSuccessful()) {
            return addressTransactionsResult;
        } else {
            return Result.success(addressTransactionsResult.toString()).withValue(addressTransactionContents).code(addressTransactionsResult.code());
        }
    }

    private Result<List<AddressTransactionContent>> convertToAddressTransactionContent(List<AccountTx> accountTxs, OrderEnum order) {
        List<AddressTransactionContent> transactionContents = new ArrayList<>();
        if (accountTxs != null) {
            accountTxs.forEach(accountTx -> {
                AddressTransactionContent transactionContent = new AddressTransactionContent();
                transactionContent.setTxHash(accountTx.getTxHash());
                transactionContent.setBlockHeight(accountTx.getBlockHeight());
                transactionContent.setBlockTime(accountTx.getBlockTime());
                transactionContents.add(transactionContent);
            });
        }
        Comparator<AddressTransactionContent> comparator = Comparator.comparing(AddressTransactionContent::getBlockHeight);
        if (order != OrderEnum.asc) {
            comparator = comparator.reversed();
        }
        transactionContents.sort(comparator);
        return Result.success("OK").withValue(transactionContents).code(200);
    }

    private Result<List<AccountAsset>> convertToAccountAssets(List<rest.koios.client.backend.api.account.model.AccountAsset> accountAssetList) {
        List<AccountAsset> accountAssets = new ArrayList<>();
        if (accountAssetList != null) {
            accountAssetList.forEach(accountAsset -> {
                AccountAsset accountAsset1 = new AccountAsset();
                accountAsset1.setUnit(accountAsset.getPolicyId() + accountAsset.getAssetName());
                accountAsset1.setQuantity(accountAsset.getQuantity());
                accountAssets.add(accountAsset1);
            });
        }
        return Result.success("OK").withValue(accountAssets).code(200);
    }
}
