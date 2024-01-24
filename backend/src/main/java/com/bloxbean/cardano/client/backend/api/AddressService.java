package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressDetails;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;

import java.util.List;

/**
 * Get information specific to an address
 */
public interface AddressService {

    /**
     * Get information about a specific address
     *
     * @param address
     * @return
     */
    Result<AddressContent> getAddressInfo(String address) throws ApiException;

    /**
     * Address details
     * Obtain details about an address.
     *
     * @param address Bech32 address.
     * @return {@link AddressDetails}
     */
    Result<AddressDetails> getAddressDetails(String address) throws ApiException;

    /**
     * Get transactions on the address
     *
     * @param address
     * @param count
     * @param page
     * @return
     * @throws ApiException
     */
    Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page) throws ApiException;

    /**
     * Get transactions on the address
     *
     * @param address
     * @param count
     * @param page
     * @param order   asc or desc. Default is "asc"
     * @return
     * @throws ApiException
     */
    Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order) throws ApiException;

    /**
     * Get transactions on the address
     * Default implementation ignores from and to argument. But a backend implementation can override this behavior.
     *
     * @param address
     * @param count
     * @param page
     * @param order   order asc or desc. Default is "asc"
     * @param from    from block number
     * @param to      to block number
     * @return
     * @throws ApiException
     */
    default Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order, String from, String to) throws ApiException {
        return getTransactions(address, count, page, order);
    }
}
