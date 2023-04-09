package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.backend.api.MetadataService;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import rest.koios.client.backend.api.transactions.TransactionsService;
import rest.koios.client.backend.api.transactions.model.TxMetadata;
import rest.koios.client.backend.api.transactions.model.TxMetadataLabels;
import rest.koios.client.backend.factory.options.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Koios Metadata Service
 */
public class KoiosMetadataService implements MetadataService {

    /**
     * Object Mapper
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Transaction Service
     */
    private final TransactionsService transactionsService;

    /**
     * KoiosMetadataService Constructor
     *
     * @param transactionsService transactionsService
     */
    public KoiosMetadataService(TransactionsService transactionsService) {
        this.transactionsService = transactionsService;
    }

    @Override
    public Result<List<MetadataJSONContent>> getJSONMetadataByTxnHash(String txnHash) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<TxMetadata>> txnMetadataList = transactionsService.getTransactionMetadata(List.of(txnHash), null);
            if (!txnMetadataList.isSuccessful()) {
                return Result.error(txnMetadataList.getResponse()).code(txnMetadataList.getCode());
            }
            return convertToMetadataJSONContentList(txnMetadataList.getValue().get(0));
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<List<MetadataJSONContent>> convertToMetadataJSONContentList(TxMetadata txMetadata) {
        List<MetadataJSONContent> metadataJSONContentList = new ArrayList<>();
        JsonNode metadata = objectMapper.convertValue(txMetadata.getMetadata(), JsonNode.class);
        String txHash = txMetadata.getTxHash();
        Iterator<String> labelIterator = metadata.fieldNames();
        while (labelIterator.hasNext()) {
            String label = labelIterator.next();
            metadataJSONContentList.add(new MetadataJSONContent(txHash, label, metadata.get(label)));
        }
        return Result.success("OK").withValue(metadataJSONContentList).code(200);
    }

    @Override
    public Result<List<MetadataCBORContent>> getCBORMetadataByTxnHash(String txnHash) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Result<List<MetadataLabel>> getMetadataLabels(int count, int page, OrderEnum order) throws ApiException {
        try {
            Options options = Options.builder().option(Limit.of(count)).option(Offset.of((long) (page - 1) * count))
                    .option(Order.by("key", order == OrderEnum.desc ? SortType.DESC : SortType.ASC)).build();
            rest.koios.client.backend.api.base.Result<List<TxMetadataLabels>> txMetadataLabelsResult =
                    transactionsService.getTransactionMetadataLabels(options);
            if (!txMetadataLabelsResult.isSuccessful()) {
                return Result.error(txMetadataLabelsResult.getResponse()).code(txMetadataLabelsResult.getCode());
            }
            return convertToMetadataLabels(txMetadataLabelsResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<List<MetadataLabel>> convertToMetadataLabels(List<TxMetadataLabels> txMetadataLabels) {
        List<MetadataLabel> metadataLabels = new ArrayList<>();
        txMetadataLabels.forEach(txMetadataLabel -> metadataLabels.add(new MetadataLabel(txMetadataLabel.getKey(), null, null)));
        return Result.success("OK").withValue(metadataLabels).code(200);
    }

    @Override
    public Result<List<MetadataJSONContent>> getJSONMetadataByLabel(BigInteger label, int count, int page, OrderEnum order) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Result<List<MetadataCBORContent>> getCBORMetadataByLabel(BigInteger label, int count, int page, OrderEnum order) {
        throw new UnsupportedOperationException();
    }
}
