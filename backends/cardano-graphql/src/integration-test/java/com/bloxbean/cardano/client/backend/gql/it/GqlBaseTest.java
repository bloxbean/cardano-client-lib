package com.bloxbean.cardano.client.backend.gql.it;

import com.bloxbean.cardano.client.backend.gql.GqlBackendService;

import java.util.HashMap;
import java.util.Map;

public class GqlBaseTest {
    protected String authKey;
    protected GqlBackendService backendService;

    public GqlBaseTest() {
        authKey = System.getProperty("CARDANO_GRAPHQL_AUTH_KEY");
        if(authKey == null || authKey.isEmpty()) {
            authKey = System.getenv("CARDANO_GRAPHQL_AUTH_KEY");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("AuthKey", "Some Auth key");
        headers.put("CustomHeader", "Some header");

        backendService = new GqlBackendService(Constant.GQL_URL, headers);
    }
}
