package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.MetadataService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

public class KoiosMetadataServiceIT extends KoiosBaseTest {

    private MetadataService metadataService;

    @BeforeEach
    public void setup() {
        metadataService = backendService.getMetadataService();
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
}
