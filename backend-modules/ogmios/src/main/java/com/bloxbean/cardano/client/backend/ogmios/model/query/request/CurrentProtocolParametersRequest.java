package com.bloxbean.cardano.client.backend.ogmios.model.query.request;

import com.bloxbean.cardano.client.backend.ogmios.model.query.base.QueryRequest;

public class CurrentProtocolParametersRequest extends QueryRequest {
    private static final QueryType QUERY_TYPE = QueryType.CURRENT_PROTOCOL_PARAMETERS;

    public String getQueryArgs() {
        return "\""+QUERY_TYPE.getValue()+"\"";
    }

    @Override
    public String getMirror() {
        return "\"object\":\""+QUERY_TYPE.getValue()+"\",\"msg_id\":"+getMsgId();
    }
}
