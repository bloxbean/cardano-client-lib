package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import lombok.Data;

import java.math.BigInteger;
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
    private Pair<BigInteger, String> pair1;
}
