package com.bloxbean.cardano.client.backend.gql.it;

import com.bloxbean.cardano.client.backend.api.MetadataService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class GqlMetadataServiceIT extends GqlBaseTest {

    @Test
    void getAsset() throws ApiException {
        MetadataService service = backendService.getMetadataService();
        Result<List<MetadataJSONContent>> result = service.getJSONMetadataByTxnHash("d55882183427330369f8e5f09ec714257a2fe2d6ffa29f158a7cb9aae056d1ee");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertThat(result.getValue(), hasSize(5));
    }
}
