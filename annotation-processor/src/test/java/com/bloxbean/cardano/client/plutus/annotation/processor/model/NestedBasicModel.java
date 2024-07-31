package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import lombok.Data;

import java.util.List;
import java.util.Optional;

@Data
@Constr
public class NestedBasicModel {
    private Integer i;
    private String s;
    private byte[] b;
    private java.util.List<List<String>> l;
    private java.util.Map<String, List<Long>> m;
    private Boolean bool;
    private Optional<List<String>> opt;
    private List<List<Optional<Vote>>> votes;
}


