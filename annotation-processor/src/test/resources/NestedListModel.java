package com.test;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;

import java.math.BigInteger;
import java.util.*;

@Constr(alternative = 1)
public class NestedListModel {
    Optional<List<String>> optionalString;
    Optional<Map<String, BigInteger>> optionalMapString;
    Optional<List<Map<String, BigInteger>>> optionalListMapString;
    Optional<List<List<Map<BigInteger, Map<String, BigInteger>>>>> optionalListOfListMapString;
    Optional<Map<String, List<BigInteger>>> optionalMapListString;
    Optional<Map<String, List<Map<byte[], BigInteger>>>> optionalMapListMapString;
    Optional<Map<String, String>> optionalMap;
    Optional<List<List<List<String>>>> optionalList;
    List<String> var1;
    List<List<List<String>>> var2;
    List<List<List<String>>> var3;
    List<Map<String, String>> var4;
    List<List<Map<String, BigInteger>>> var5;
    Map<String, BigInteger> name;
    Map<Map<String, String>, Map<Long, BigInteger>> age;
    Map<String, Map<String, BigInteger>> something;
    Map<String, List<Map<String, List<String>>>> val;
}
