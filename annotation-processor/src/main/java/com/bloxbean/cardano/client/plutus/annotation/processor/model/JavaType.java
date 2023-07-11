package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
public class JavaType {
    public final static JavaType INT = new JavaType("int", false);
    public final static JavaType LONG = new JavaType("long", false);
    public final static JavaType BIGINTEGER = new JavaType("java.math.BigInteger", false);
    public final static JavaType STRING = new JavaType("java.lang.String", false);
    public final static JavaType BYTES = new JavaType("byte[]", false);
    public final static JavaType LIST = new JavaType("java.util.List", false);
    public final static JavaType MAP = new JavaType("java.util.Map", false);
    public final static JavaType OPTIONAL = new JavaType("java.util.Optional", false);

    private String name;
    private boolean userDefined;
    public JavaType(String name, boolean userDefined) {
        this.name = name;
        this.userDefined = userDefined;
    }

    public String getName() {
        return name;
    }
}
