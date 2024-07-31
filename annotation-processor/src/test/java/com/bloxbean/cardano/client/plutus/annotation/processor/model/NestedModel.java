package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import lombok.Data;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Constr(alternative = 1)
@Data
public class NestedModel {
    private Student student;
    private List<Optional<String>> opt;
    private Optional<List<String>> optionalString;
    private Optional<List<String>> field1;
    private Optional<Map<String, BigInteger>> field2;
    private Optional<List<Map<String, BigInteger>>> field3;
    private Optional<List<List<Map<BigInteger, Map<String, BigInteger>>>>> field4;
    private Optional<Map<String, List<BigInteger>>> field5;
    private Optional<Map<String, List<Map<byte[], BigInteger>>>> field6;
    private Optional<Map<String, String>> field7;
    private Optional<List<List<List<String>>>> field8;
    private List<String> field9;
    private List<List<List<String>>> field10;
    private List<List<List<String>>> field11;
    private List<Map<String, String>> field12;
    private List<List<Map<String, BigInteger>>> field13;
    private Map<String, BigInteger> field14;
    private Map<Map<String, String>, Map<Long, BigInteger>> field15;
    private Map<String, Map<String, BigInteger>> field16;
    private Map<String, List<Map<String, List<String>>>> field17;
    private Optional<List<List<List<Subject>>>> field18;

    private List<Optional<String>> nestedOpt;
    private List<Optional<List<Student>>> nestedOpt1;
    private Map<Optional<String>, BigInteger> nestedOpt2;
    private Map<Optional<Student>, BigInteger> nestedOpt3;
    private Map<BigInteger, Optional<Student>> nestedOpt4;
}
