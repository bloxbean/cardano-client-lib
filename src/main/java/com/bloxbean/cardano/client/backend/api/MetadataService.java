package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel;

import java.math.BigInteger;
import java.util.List;

public interface MetadataService {

    /**
     * Get metadata for a transaction in JSON format
     * @param txnHash
     * @return
     * @throws ApiException
     */
    public Result<List<MetadataJSONContent>> getJSONMetadataByTxnHash(String txnHash) throws ApiException;

    /**
     * Get metadata for a txn in CBOR format
     * @param txnHash
     * @return
     * @throws ApiException
     */
    public Result<List<MetadataCBORContent>> getCBORMetadataByTxnHash(String txnHash) throws ApiException;

    /**
     * Get all metadata labels
     * @param count
     * @param page
     * @param order
     * @return
     * @throws ApiException
     */
    public Result<List<MetadataLabel>> getMetadataLabels(int count, int page, OrderEnum order) throws ApiException;

    /**
     * Get list of {@link MetadataJSONContent} by label
     * @param label
     * @param count
     * @param page
     * @param order
     * @return
     * @throws ApiException
     */
    public Result<List<MetadataJSONContent>> getJSONMetadataByLabel(BigInteger label, int count, int page, OrderEnum order) throws ApiException;

    /**
     * Get list of {@link MetadataCBORContent} by label
     * @param label
     * @param count
     * @param page
     * @param order
     * @return
     * @throws ApiException
     */
    public Result<List<MetadataCBORContent>> getCBORMetadataByLabel(BigInteger label, int count, int page, OrderEnum order) throws ApiException;

}
