package com.bloxbean.cardano.client.plutus.annotation.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import lombok.Data;

import java.util.Optional;

@Data
@Constr
public class BasicModel {
    private Integer i;
    private String s;
    private byte[] b;
    private java.util.List<java.lang.String> l;
    private java.util.Map<java.lang.String, java.math.BigInteger> m;
    private Boolean bool;
    private Optional<String> opt;
}
