package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.account.model.AccountTransaction;
import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.AccountService;
import com.bloxbean.cardano.client.backend.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Nexus Account Service. {@link #getAccountInformation(String)}, rewards history, addresses and
 * transactions are backed by the SDK; history and assets have no Nexus SDK equivalent yet.
 */
public class NexusAccountService implements AccountService {

    private final adlabs.nexus.client.backend.api.account.AccountService accountService;
    private final Network network;

    public NexusAccountService(adlabs.nexus.client.backend.api.account.AccountService accountService, Network network) {
        this.accountService = accountService;
        this.network = network;
    }

    @Override
    public Result<AccountInformation> getAccountInformation(String stakeAddress) throws ApiException {
        try {
            return NexusResultMapper.map(accountService.getAccountInformation(network, stakeAddress), this::toAccountInformation);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private AccountInformation toAccountInformation(adlabs.nexus.client.backend.api.account.model.AccountInformation info) {
        AccountInformation accountInformation = new AccountInformation();
        accountInformation.setActive(info.getActive());
        accountInformation.setControlledAmount(info.getControlledAmount());
        accountInformation.setRewardsSum(info.getRewardsSum());
        accountInformation.setReservesSum(info.getReservesSum());
        accountInformation.setWithdrawalsSum(info.getWithdrawalsSum());
        accountInformation.setTreasurySum(info.getTreasurySum());
        accountInformation.setWithdrawableAmount(info.getWithdrawableAmount());
        accountInformation.setPool_id(info.getPoolId());
        return accountInformation;
    }

    @Override
    public Result<List<AccountRewardsHistory>> getAccountRewardsHistory(String stakeAddress, int count, int page) throws ApiException {
        return getAccountRewardsHistory(stakeAddress, count, page, null);
    }

    // Nexus has no order param for rewards; order is ignored.
    @Override
    public Result<List<AccountRewardsHistory>> getAccountRewardsHistory(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        try {
            return NexusResultMapper.map(accountService.getAccountRewards(network, stakeAddress),
                    list -> NexusPagination.subList(toAccountRewardsHistories(list), count, page));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<AccountHistory>> getAccountHistory(String stakeAddress, int count, int page) throws ApiException {
        throw new UnsupportedOperationException("getAccountHistory not supported by Nexus");
    }

    @Override
    public Result<List<AccountHistory>> getAccountHistory(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getAccountHistory not supported by Nexus");
    }

    @Override
    public Result<List<AccountAddress>> getAllAccountAddresses(String stakeAddress) throws ApiException {
        try {
            return NexusResultMapper.map(accountService.getAccountAddresses(network, stakeAddress), this::toAccountAddresses);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<AccountAddress>> getAccountAddresses(String stakeAddress, int count, int page) throws ApiException {
        return getAccountAddresses(stakeAddress, count, page, null);
    }

    // Nexus has no order param for addresses; order is ignored.
    @Override
    public Result<List<AccountAddress>> getAccountAddresses(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        try {
            return NexusResultMapper.map(accountService.getAccountAddresses(network, stakeAddress),
                    list -> NexusPagination.subList(toAccountAddresses(list), count, page));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<AccountAsset>> getAllAccountAssets(String stakeAddress) throws ApiException {
        throw new UnsupportedOperationException("getAllAccountAssets not supported by Nexus");
    }

    @Override
    public Result<List<AccountAsset>> getAccountAssets(String stakeAddress, int count, int page) throws ApiException {
        throw new UnsupportedOperationException("getAccountAssets not supported by Nexus");
    }

    @Override
    public Result<List<AccountAsset>> getAccountAssets(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getAccountAssets not supported by Nexus");
    }

    @Override
    public Result<List<AddressTransactionContent>> getAccountTransactions(String stakeAddress, int count, int page, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight) throws ApiException {
        try {
            // Nexus requires fromBlockHeight; SDK javadoc says pass 1 for full history.
            int fromBH = fromBlockHeight == null ? 1 : fromBlockHeight;
            return NexusResultMapper.map(accountService.getAccountTransactions(network, stakeAddress, fromBH),
                    list -> NexusPagination.subList(toAddressTransactionContents(list, toBlockHeight, order), count, page));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<AddressTransactionContent>> getAllAccountTransactions(String stakeAddress, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight) throws ApiException {
        try {
            // Nexus requires fromBlockHeight; SDK javadoc says pass 1 for full history.
            int fromBH = fromBlockHeight == null ? 1 : fromBlockHeight;
            return NexusResultMapper.map(accountService.getAccountTransactions(network, stakeAddress, fromBH),
                    list -> toAddressTransactionContents(list, toBlockHeight, order));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private List<AccountRewardsHistory> toAccountRewardsHistories(List<adlabs.nexus.client.backend.api.account.model.AccountRewardsHistory> rewards) {
        List<AccountRewardsHistory> result = new ArrayList<>();
        for (adlabs.nexus.client.backend.api.account.model.AccountRewardsHistory r : rewards) {
            result.add(AccountRewardsHistory.builder()
                    .epoch(r.getEpoch())
                    .amount(r.getAmount())
                    .poolId(r.getPoolId())
                    .type(r.getType())
                    .build());
        }
        return result;
    }

    private List<AccountAddress> toAccountAddresses(List<adlabs.nexus.client.backend.api.account.model.AccountAddress> addresses) {
        List<AccountAddress> result = new ArrayList<>();
        for (adlabs.nexus.client.backend.api.account.model.AccountAddress a : addresses) {
            result.add(AccountAddress.builder().address(a.getAddress()).build());
        }
        return result;
    }

    // toBlockHeight is a client-side filter (SDK only takes fromBlockHeight); txIndex has no SDK equivalent, defaults to 0.
    private List<AddressTransactionContent> toAddressTransactionContents(List<AccountTransaction> txs, Integer toBlockHeight, OrderEnum order) {
        List<AddressTransactionContent> result = new ArrayList<>();
        for (AccountTransaction tx : txs) {
            long blockHeight = tx.getBlockHeight() == null ? 0L : tx.getBlockHeight();
            if (toBlockHeight != null && blockHeight > toBlockHeight) continue;
            result.add(AddressTransactionContent.builder()
                    .txHash(tx.getTxHash())
                    .txIndex(0)
                    .blockHeight(blockHeight)
                    .blockTime(tx.getBlockTime() == null ? 0L : tx.getBlockTime())
                    .build());
        }
        if (order == OrderEnum.desc) {
            Collections.reverse(result);
        }
        return result;
    }
}
