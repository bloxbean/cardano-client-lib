package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.Result;

import java.util.List;

/**
 * Get information specific to an address
 */
public interface AddressService {

    /**
     * Get information about a specific address
     * @param address
     * @return
     */
    public Result<AddressContent> getAddressInfo(String address) throws ApiException;

    /**
     * Get transactions on the address
     * @param address
     * @param count
     * @param page
     * @return
     * @throws ApiException
     */
    public Result<List<String>> getTransactions(String address, int count, int page) throws ApiException;

    /**
     * Get transactions on the address
     * @param address
     * @param count
     * @param page
     * @param order asc or desc. Default is "asc"
     * @return
     * @throws ApiException
     */
    public Result<List<String>> getTransactions(String address, int count, int page, OrderEnum order) throws ApiException;
}
