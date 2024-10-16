package com.bloxbean.cardano.client.plutus.annotation.processor.model;

public enum Type {
    INTEGER("integer"),
    STRING("string"),
    BYTES("bytes"),
    LIST("list"),
    MAP("map"),
    BOOL("bool"),
    CONSTRUCTOR("constructor"),
    PLUTUSDATA("plutusdata"),
    OPTIONAL("optional"),
    PAIR("pair");

    private String type;
    Type(String type) {
        this.type = type;
    }
}
