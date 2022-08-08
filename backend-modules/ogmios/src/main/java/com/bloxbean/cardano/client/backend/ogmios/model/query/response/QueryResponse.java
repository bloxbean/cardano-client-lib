package com.bloxbean.cardano.client.backend.ogmios.model.query.response;

import com.bloxbean.cardano.client.backend.ogmios.model.base.Response;
import com.bloxbean.cardano.client.backend.ogmios.model.query.request.QueryType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public class QueryResponse extends Response {

    public QueryResponse(long msgId) {
        super(msgId);
    }

    public static QueryResponse parse(QueryType queryType, long msgId, JsonNode result) {
        switch (Objects.requireNonNull(queryType)) {
            case CURRENT_PROTOCOL_PARAMETERS:
                return CurrentProtocolParameters.deserialize(msgId, result);
            default:
                return null;
        }
    }
}
