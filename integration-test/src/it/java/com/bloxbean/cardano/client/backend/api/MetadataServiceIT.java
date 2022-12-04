package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class MetadataServiceIT extends BaseITTest {

    MetadataService metadataService;

    @BeforeEach
    public void setup() {
        metadataService = getBackendService().getMetadataService();
    }

    @Test
    public void testGetCBORMetadataByTxnHash() throws ApiException {
        String txHash = "8607a5f2744eca46cfc2d93edf595ac9a8a1243a9d960ca8b78643cc0c78a6ce";

        if (backendType.equals(KOIOS)) {
            Assertions.assertThrows(UnsupportedOperationException.class, ()-> {
                metadataService.getCBORMetadataByTxnHash(txHash);
            });
            return;
        }

        Result<List<MetadataCBORContent>> result = metadataService.getCBORMetadataByTxnHash(txHash);

        List<MetadataCBORContent> value = result.getValue();

        System.out.println(JsonUtil.getPrettyJson(value));

        List<String> labels = value.stream().map(v -> v.getLabel()).collect(Collectors.toList());
        List<String> cborMetadata = value.stream().map(v -> v.getCborMetadata()).collect(Collectors.toList());

        assertThat(value, Matchers.notNullValue());
        assertThat(value, hasSize(4));
        assertThat(labels, hasItem("700"));
        assertThat(cborMetadata, hasItem("\\xa11902586f5465737420537472696e6720363030"));
    }


    @Test
    public void testGetJSONMetadataByTxnHash() throws ApiException {
        String txHash = "8607a5f2744eca46cfc2d93edf595ac9a8a1243a9d960ca8b78643cc0c78a6ce";

        Result<List<MetadataJSONContent>> result = metadataService.getJSONMetadataByTxnHash(txHash);

        List<MetadataJSONContent> value = result.getValue();

        System.out.println(JsonUtil.getPrettyJson(value));

        List<String> labels = value.stream().map(v -> v.getLabel()).collect(Collectors.toList());

        assertThat(value, Matchers.notNullValue());
        assertThat(value, hasSize(4));
        assertThat(labels, hasItem("600"));
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
        if (backendType.equals(KOIOS)) {
            Assertions.assertThrows(UnsupportedOperationException.class, ()-> {
                metadataService.getJSONMetadataByLabel(new BigInteger("1"), 10, 1, OrderEnum.asc);
            });
            return;
        }

        Result<List<MetadataJSONContent>> result = metadataService.getJSONMetadataByLabel(new BigInteger("1"), 10, 1, OrderEnum.asc);

        List<MetadataJSONContent> value = result.getValue();

        System.out.println(JsonUtil.getPrettyJson(value));

        assertThat(value, Matchers.notNullValue());
        assertThat(value, hasSize(10));
        assertThat(value.get(0).getTxHash(), is("77cb8608db0a84f512e277ba923341775013241401c768ba5214ad2ac004b153"));
    }

    @Test
    public void testGetCBORMetadataByLabel() throws ApiException {
        if (backendType.equals(KOIOS)) {
            Assertions.assertThrows(UnsupportedOperationException.class, ()-> {
                metadataService.getCBORMetadataByLabel(new BigInteger("1985"), 10, 1, OrderEnum.asc);
            });
            return;
        }

        Result<List<MetadataCBORContent>> result = metadataService.getCBORMetadataByLabel(new BigInteger("1985"), 10, 1, OrderEnum.asc);

        List<MetadataCBORContent> value = result.getValue();

        System.out.println(JsonUtil.getPrettyJson(value));

        assertThat(value, Matchers.notNullValue());
        assertThat(value, hasSize(2));
        assertThat(value.get(0).getTxHash(), is("2b8e9615dc496128803f69047eabe9c3244e576bd236aff49dcb5177abef3e3f"));
    }

}
