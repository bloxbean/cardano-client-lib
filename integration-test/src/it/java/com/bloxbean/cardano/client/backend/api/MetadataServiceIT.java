package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class MetadataServiceIT extends BaseITTest {

    BackendService backendService;
    MetadataService metadataService;

    @BeforeEach
    public void setup() {
        backendService = BackendFactory.getBlockfrostBackendService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        metadataService = backendService.getMetadataService();
    }

    @Test
    public void testGetCBORMetadataByTxnHash() throws ApiException {
        String txHash = "d55882183427330369f8e5f09ec714257a2fe2d6ffa29f158a7cb9aae056d1ee";

        Result<List<MetadataCBORContent>> result = metadataService.getCBORMetadataByTxnHash(txHash);

        List<MetadataCBORContent> value = result.getValue();

        System.out.println(JsonUtil.getPrettyJson(value));

        List<String> labels = value.stream().map(v -> v.getLabel()).collect(Collectors.toList());
        List<String> cborMetadata = value.stream().map(v -> v.getCborMetadata()).collect(Collectors.toList());

        assertThat(value, Matchers.notNullValue());
        assertThat(value, hasSize(5));
        assertThat(labels, hasItem("197819781978"));
        assertThat(cborMetadata, hasItem("\\xa11b0000002e0efa535a644a6f686e"));
    }


    @Test
    public void testGetJSONMetadataByTxnHash() throws ApiException {
        String txHash = "d55882183427330369f8e5f09ec714257a2fe2d6ffa29f158a7cb9aae056d1ee";

        Result<List<MetadataJSONContent>> result = metadataService.getJSONMetadataByTxnHash(txHash);

        List<MetadataJSONContent> value = result.getValue();

        System.out.println(JsonUtil.getPrettyJson(value));

        List<String> labels = value.stream().map(v -> v.getLabel()).collect(Collectors.toList());

        assertThat(value, Matchers.notNullValue());
        assertThat(value, hasSize(5));
        assertThat(labels, hasItem("197819781978"));
    }

    @Test
    public void testGetMetadataLabels() throws ApiException {
        Result<List<MetadataLabel>> result = metadataService.getMetadataLabels(20, 1, OrderEnum.asc);

        List<MetadataLabel> value = result.getValue();

        System.out.println(JsonUtil.getPrettyJson(value));

        assertThat(value, Matchers.notNullValue());
        assertThat(value, hasSize(20));
    }

    @Test
    public void testGetJSONMetadataByLabel() throws ApiException {
        Result<List<MetadataJSONContent>> result = metadataService.getJSONMetadataByLabel(new BigInteger("1"), 10, 1, OrderEnum.asc);

        List<MetadataJSONContent> value = result.getValue();

        System.out.println(JsonUtil.getPrettyJson(value));

        assertThat(value, Matchers.notNullValue());
        assertThat(value, hasSize(10));
        assertThat(value.get(0).getTxHash(), is("1c8997f9f0debde5b15fe29f0f18839a64e51c19ccdbe89e2811930d777c9b68"));
    }

    @Test
    public void testGetCBORMetadataByLabel() throws ApiException {
        Result<List<MetadataCBORContent>> result = metadataService.getCBORMetadataByLabel(new BigInteger("1985"), 10, 1, OrderEnum.asc);

        List<MetadataCBORContent> value = result.getValue();

        System.out.println(JsonUtil.getPrettyJson(value));

        assertThat(value, Matchers.notNullValue());
        assertThat(value, hasSize(10));
        assertThat(value.get(0).getTxHash(), is("c13c61a6e4da17482d1e79cb2eeed3e8c54a3edd23aad8722ad003be2d51d6e3"));
    }

}
