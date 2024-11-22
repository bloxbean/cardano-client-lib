package com.test;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;
import lombok.Data;

import java.math.BigInteger;
import java.util.*;

@Constr(alternative = 1)
public class NestedListModel {
    List<Optional<String>> opt;
    Map<Optional<String>, BigInteger> optMap1;

    Map<Optional<Map<String, List<Optional<String>>>>, Optional<List<BigInteger>>> optMap;
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

    public List<Optional<String>> getOpt() {
        return opt;
    }

    public void setOpt(List<Optional<String>> opt) {
        this.opt = opt;
    }

    public Map<Optional<String>, BigInteger> getOptMap1() {
        return optMap1;
    }

    public void setOptMap1(Map<Optional<String>, BigInteger> optMap1) {
        this.optMap1 = optMap1;
    }

    public Map<Optional<Map<String, List<Optional<String>>>>, Optional<List<BigInteger>>> getOptMap() {
        return optMap;
    }

    public void setOptMap(Map<Optional<Map<String, List<Optional<String>>>>, Optional<List<BigInteger>>> optMap) {
        this.optMap = optMap;
    }

    public Optional<List<String>> getOptionalString() {
        return optionalString;
    }

    public void setOptionalString(Optional<List<String>> optionalString) {
        this.optionalString = optionalString;
    }

    public Optional<Map<String, BigInteger>> getOptionalMapString() {
        return optionalMapString;
    }

    public void setOptionalMapString(Optional<Map<String, BigInteger>> optionalMapString) {
        this.optionalMapString = optionalMapString;
    }

    public Optional<List<Map<String, BigInteger>>> getOptionalListMapString() {
        return optionalListMapString;
    }

    public void setOptionalListMapString(Optional<List<Map<String, BigInteger>>> optionalListMapString) {
        this.optionalListMapString = optionalListMapString;
    }

    public Optional<List<List<Map<BigInteger, Map<String, BigInteger>>>>> getOptionalListOfListMapString() {
        return optionalListOfListMapString;
    }

    public void setOptionalListOfListMapString(Optional<List<List<Map<BigInteger, Map<String, BigInteger>>>>> optionalListOfListMapString) {
        this.optionalListOfListMapString = optionalListOfListMapString;
    }

    public Optional<Map<String, List<BigInteger>>> getOptionalMapListString() {
        return optionalMapListString;
    }

    public void setOptionalMapListString(Optional<Map<String, List<BigInteger>>> optionalMapListString) {
        this.optionalMapListString = optionalMapListString;
    }

    public Optional<Map<String, List<Map<byte[], BigInteger>>>> getOptionalMapListMapString() {
        return optionalMapListMapString;
    }

    public void setOptionalMapListMapString(Optional<Map<String, List<Map<byte[], BigInteger>>>> optionalMapListMapString) {
        this.optionalMapListMapString = optionalMapListMapString;
    }

    public Optional<Map<String, String>> getOptionalMap() {
        return optionalMap;
    }

    public void setOptionalMap(Optional<Map<String, String>> optionalMap) {
        this.optionalMap = optionalMap;
    }

    public Optional<List<List<List<String>>>> getOptionalList() {
        return optionalList;
    }

    public void setOptionalList(Optional<List<List<List<String>>>> optionalList) {
        this.optionalList = optionalList;
    }

    public List<String> getVar1() {
        return var1;
    }

    public void setVar1(List<String> var1) {
        this.var1 = var1;
    }

    public List<List<List<String>>> getVar2() {
        return var2;
    }

    public void setVar2(List<List<List<String>>> var2) {
        this.var2 = var2;
    }

    public List<List<List<String>>> getVar3() {
        return var3;
    }

    public void setVar3(List<List<List<String>>> var3) {
        this.var3 = var3;
    }

    public List<Map<String, String>> getVar4() {
        return var4;
    }

    public void setVar4(List<Map<String, String>> var4) {
        this.var4 = var4;
    }

    public List<List<Map<String, BigInteger>>> getVar5() {
        return var5;
    }

    public void setVar5(List<List<Map<String, BigInteger>>> var5) {
        this.var5 = var5;
    }

    public Map<String, BigInteger> getName() {
        return name;
    }

    public void setName(Map<String, BigInteger> name) {
        this.name = name;
    }

    public Map<Map<String, String>, Map<Long, BigInteger>> getAge() {
        return age;
    }

    public void setAge(Map<Map<String, String>, Map<Long, BigInteger>> age) {
        this.age = age;
    }

    public Map<String, Map<String, BigInteger>> getSomething() {
        return something;
    }

    public void setSomething(Map<String, Map<String, BigInteger>> something) {
        this.something = something;
    }

    public Map<String, List<Map<String, List<String>>>> getVal() {
        return val;
    }

    public void setVal(Map<String, List<Map<String, List<String>>>> val) {
        this.val = val;
    }
}
