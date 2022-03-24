package com.bloxbean.cardano.client.backend.ogmios.model.base;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class Request extends Message {

    private static final String TYPE = "jsonwsp/request";
    private static final String VERSION = "1.0";
    private static final String SERVICE_NAME = "ogmios";

    protected Request(long msgId) {
        super(msgId);
    }

    @Override
    public String toString() {
        return "{\"type\":\"" + TYPE + "\",\"version\":\"" + VERSION + "\",\"servicename\":\"" + SERVICE_NAME + "\",\"methodname\":\"" + getMethodType() + "\",\"mirror\":{" + getMirror() + "},\"args\":{" + getArgs() + "}}";
    }

    protected abstract String getMethodType();

    public abstract String getArgs();

    public abstract String getMirror();
}
