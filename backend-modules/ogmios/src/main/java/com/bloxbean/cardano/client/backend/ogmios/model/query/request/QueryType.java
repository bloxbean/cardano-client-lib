package com.bloxbean.cardano.client.backend.ogmios.model.query.request;

public enum QueryType {

    CURRENT_PROTOCOL_PARAMETERS("currentProtocolParameters");

    private final String value;

    QueryType(String value) {
        this.value = value;
    }

    public static QueryType convert(String queryType) {
        for (QueryType type : values()) {
            if (type.getValue().equals(queryType)) {
                return type;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }
}
