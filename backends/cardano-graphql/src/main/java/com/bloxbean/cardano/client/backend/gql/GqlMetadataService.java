package com.bloxbean.cardano.client.backend.gql;

import com.bloxbean.cardano.client.backend.api.MetadataService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel;
import com.bloxbean.cardano.gql.MetadataQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import okhttp3.OkHttpClient;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GqlMetadataService extends BaseGqlService implements MetadataService {
    ObjectMapper objectMapper;
    public GqlMetadataService(String gqlUrl) {
        super(gqlUrl);
        this.objectMapper = new ObjectMapper();
    }

    public GqlMetadataService(String gqlUrl, Map<String, String > headers) {
        super(gqlUrl, headers);
        this.objectMapper = new ObjectMapper();
    }

    public GqlMetadataService(String gqlUrl, OkHttpClient okHttpClient) {
        super(gqlUrl, okHttpClient);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Result<List<MetadataJSONContent>> getJSONMetadataByTxnHash(String txnHash) throws ApiException {
        MetadataQuery query = new MetadataQuery(txnHash);
        MetadataQuery.Data data = execute(query);
        if(data == null)
            return Result.error("Unable to get transaction with txnhash: " + txnHash);

        List<MetadataQuery.Transaction> transactions = data.transactions();
        if(transactions == null || transactions.size() == 0)
            return Result.error("Unable to get transaction with txnhash: " + txnHash);

        MetadataQuery.Transaction transaction = transactions.get(0);
        List<MetadataQuery.Metadatum> metadatumList = transaction.metadata();
        List<MetadataJSONContent> metadataJSONContentList = new ArrayList<>();
        for(MetadataQuery.Metadatum metadatum: metadatumList) {
            MetadataJSONContent metadataJSONContent = new MetadataJSONContent();
            metadataJSONContent.setTxHash(txnHash);
            metadataJSONContent.setLabel(metadatum.key());

            JsonNode jsonNode = convertToJsonNode(metadatum);
            metadataJSONContent.setJsonMetadata(jsonNode);
            metadataJSONContentList.add(metadataJSONContent);
        }


        return processSuccessResult(metadataJSONContentList);
    }

    private JsonNode convertToJsonNode(MetadataQuery.Metadatum metadatum) {
        try {
            return metadatum.value();
//            return objectMapper.readTree(metadatum.value().toString());
        } catch (Exception e) {
            return new TextNode(metadatum.value().toString());
        }
    }

    @Override
    public Result<List<MetadataCBORContent>> getCBORMetadataByTxnHash(String txnHash) throws ApiException {
        return null;
    }

    @Override
    public Result<List<MetadataLabel>> getMetadataLabels(int count, int page, OrderEnum order) throws ApiException {
        return null;
    }

    @Override
    public Result<List<MetadataJSONContent>> getJSONMetadataByLabel(BigInteger label, int count, int page, OrderEnum order) throws ApiException {
        return null;
    }

    @Override
    public Result<List<MetadataCBORContent>> getCBORMetadataByLabel(BigInteger label, int count, int page, OrderEnum order) throws ApiException {
        return null;
    }
}
