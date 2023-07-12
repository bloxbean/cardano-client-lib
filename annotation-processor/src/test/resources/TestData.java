package com.bloxbean.cardano.client.examples.annotation;

import com.bloxbean.cardano.client.plutus.annotation.Constr;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Constr(alternative = 1)
public class TestData {
    public Integer a;
    public String b;
    public Optional<AnotherData> opt;
    public List<String> names;
    public AnotherData anotherData;
    public Map<String, BigInteger> modelMap;
    public Optional<Integer> gg;

    public Integer getA() {
        return a;
    }

    public void setA(Integer a) {
        this.a = a;
    }

    public String getB() {
        return b;
    }

    public void setB(String b) {
        this.b = b;
    }

    public Optional<AnotherData> getOpt() {
        return opt;
    }

    public void setOpt(Optional<AnotherData> opt) {
        this.opt = opt;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    public AnotherData getAnotherData() {
        return anotherData;
    }

    public void setAnotherData(AnotherData anotherData) {
        this.anotherData = anotherData;
    }

    public Map<String, BigInteger> getModelMap() {
        return modelMap;
    }

    public void setModelMap(Map<String, BigInteger> modelMap) {
        this.modelMap = modelMap;
    }

    public Optional<Integer> getGg() {
        return gg;
    }

    public void setGg(Optional<Integer> gg) {
        this.gg = gg;
    }
}
