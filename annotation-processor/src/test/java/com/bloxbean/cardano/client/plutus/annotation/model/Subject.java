package com.bloxbean.cardano.client.plutus.annotation.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;
import lombok.Data;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Constr
@Data
public class Subject {
    private String name;
    private int marks;
    private byte[] logo;
    private Optional<byte[]> id;
    private List<List<List<String>>> nestedList;
    private Map<String, Map<A, String>> nestedMap;
    private List<List<Map<String, List<BigInteger>>>> nestedListMap;

    @PlutusIgnore
    private String ignoreMe;
}
