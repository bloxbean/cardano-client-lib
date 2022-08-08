package com.bloxbean.cardano.client.backend.ogmios.model.query.base;

import com.bloxbean.cardano.client.backend.ogmios.model.base.MethodType;
import com.bloxbean.cardano.client.backend.ogmios.model.base.Request;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class QueryRequest extends Request {

    private static final MethodType METHOD_TYPE = MethodType.QUERY;

    protected QueryRequest(long msgId) {
        super(msgId);
    }

    public String getArgs() {
        return "\"query\":"+getQueryArgs();
    }

    @Override
    public String getMethodType() {
        return METHOD_TYPE.getValue();
    }

    public abstract String getQueryArgs();

    public abstract String getMirror();
}
