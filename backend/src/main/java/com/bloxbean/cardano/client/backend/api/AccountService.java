package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.*;

import java.util.List;

/**
 * Get information specific to an account
 */
public interface AccountService {

    /**
     * Obtain information about a specific stake account.
     * @param stakeAddress Bech32 stake address.
     * @return account info of any staking address
     * @throws ApiException upon API Error
     */
    Result<AccountInformation> getAccountInformation(String stakeAddress) throws ApiException;

    /**
     * Obtain information about the reward history of a specific account.
     * @param stakeAddress Bech32 stake address.
     * @param count The number of results displayed on one page.
     * @param page The page number for listing the results.
     * @return staking history of an account
     * @throws ApiException upon API Error
     */
    Result<List<AccountRewardsHistory>> getAccountRewardsHistory(String stakeAddress, int count, int page) throws ApiException;

    /**
     * Obtain information about the reward history of a specific account.
     * @param stakeAddress Bech32 stake address.
     * @param count The number of results displayed on one page.
     * @param page The page number for listing the results.
     * @param order The ordering of items from the point of view of the blockchain, not the page listing itself.
     *              By default, we return oldest first, newest last.
     * @return staking history of an account
     * @throws ApiException upon API Error
     */
    Result<List<AccountRewardsHistory>> getAccountRewardsHistory(String stakeAddress, int count, int page, OrderEnum order) throws ApiException;

    /**
     * Obtain information about the history of a specific account.
     * @param stakeAddress Bech32 stake address.
     * @param count The number of results displayed on one page.
     * @param page The page number for listing the results.
     * @return staking history of an account
     * @throws ApiException upon API Error
     */
    Result<List<AccountHistory>> getAccountHistory(String stakeAddress, int count, int page) throws ApiException;

    /**
     * Obtain information about the history of a specific account.
     * @param stakeAddress Bech32 stake address.
     * @param count The number of results displayed on one page.
     * @param page The page number for listing the results.
     * @param order The ordering of items from the point of view of the blockchain, not the page listing itself.
     *              By default, we return oldest first, newest last.
     * @return staking history of an account
     * @throws ApiException upon API Error
     */
    Result<List<AccountHistory>> getAccountHistory(String stakeAddress, int count, int page, OrderEnum order) throws ApiException;

    /**
     * Obtain information about the addresses of a specific account.
     * @param stakeAddress Bech32 stake address.
     * @return List of {@link AccountAddress}
     */
    Result<List<AccountAddress>> getAllAccountAddresses(String stakeAddress) throws ApiException;

    /**
     * Obtain information about the addresses of a specific account.
     * @param stakeAddress Bech32 stake address.
     * @param count The number of results displayed on one page.
     * @param page The page number for listing the results.
     * @return List of {@link AccountAddress}
     */
    Result<List<AccountAddress>> getAccountAddresses(String stakeAddress, int count, int page) throws ApiException;

    /**
     * Obtain information about the addresses of a specific account.
     * @param stakeAddress Bech32 stake address.
     * @param count The number of results displayed on one page.
     * @param page The page number for listing the results.
     * @param order The ordering of items from the point of view of the blockchain, not the page listing itself.
     *              By default, we return oldest first, newest last.
     * @return List of {@link AccountAddress}
     */
    Result<List<AccountAddress>> getAccountAddresses(String stakeAddress, int count, int page, OrderEnum order) throws ApiException;

    /**
     * Obtain information about assets associated with addresses of a specific account.
     * Be careful, as an account could be part of a mangled address and does not necessarily mean the addresses are owned by user as the account.
     * @param stakeAddress Bech32 stake address.
     * @return List of {@link AccountAsset}
     */
    Result<List<AccountAsset>> getAllAccountAssets(String stakeAddress) throws ApiException;

    /**
     * Obtain information about assets associated with addresses of a specific account.
     * Be careful, as an account could be part of a mangled address and does not necessarily mean the addresses are owned by user as the account.
     * @param stakeAddress Bech32 stake address.
     * @param count The number of results displayed on one page.
     * @param page The page number for listing the results.
     * @return List of {@link AccountAsset}
     */
    Result<List<AccountAsset>> getAccountAssets(String stakeAddress, int count, int page) throws ApiException;

    /**
     * Obtain information about assets associated with addresses of a specific account.
     * Be careful, as an account could be part of a mangled address and does not necessarily mean the addresses are owned by user as the account.
     * @param stakeAddress Bech32 stake address.
     * @param count The number of results displayed on one page.
     * @param page The page number for listing the results.
     * @param order The ordering of items from the point of view of the blockchain, not the page listing itself.
     *              By default, we return oldest first, newest last.
     * @return List of Used Addresses
     */
    Result<List<AccountAsset>> getAccountAssets(String stakeAddress, int count, int page, OrderEnum order) throws ApiException;
}
