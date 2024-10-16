package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import lombok.Data;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Data
@Constr
public class PairModel {
    private Pair<Map<BigInteger, String>, String> pair1;
    private Pair<String, Map<BigInteger, String>> pair2;
    private Pair<Map<String, byte[]>, Map<BigInteger, String>> pair3;
    private Pair<Map<String, byte[]>, List<BigInteger>> pair4;
    private Pair<Map<List<String>, byte[]>, List<BigInteger>> pair5;
}
