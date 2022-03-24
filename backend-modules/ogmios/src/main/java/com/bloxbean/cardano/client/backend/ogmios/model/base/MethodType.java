package com.bloxbean.cardano.client.backend.ogmios.model.base;

public enum MethodType {
    SUBMIT_TX("SubmitTx"),
    EVALUATE_TX("EvaluateTx");

    private final String value;

    MethodType(String value) {
        this.value = value;
    }

    public static MethodType convert(String type) {
        for (MethodType methodType : values()) {
            if (methodType.getValue().equals(type)) {
                return methodType;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }
}
