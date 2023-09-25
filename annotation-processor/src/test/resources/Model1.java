package com.test;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.annotation.Enc;

import java.math.BigInteger;
import java.util.*;
import java.util.Map.*;

@Constr(alternative = 1)
public class Model1 {
    long l = 0L;
    Integer a;
    private BigInteger b = BigInteger.valueOf(0);
    BigInteger ccc = BigInteger.valueOf(0);
    Optional<BigInteger> optBI;
    Optional<Integer> optInt;
    Optional<Model2> optMod2;
    Optional<Long> optionalLong;
    Optional<byte[]> bytes;
    boolean bool;
    Boolean boolObj;

    private boolean prvBool;
    private Boolean prvBoolObj;

    @Enc("hex")
    private String str;

    List<String> list;
    Model2 model2;
    List<Model2> model2List;
    ArrayList<Model2> model3List;
    private Map<String, BigInteger> modelMap;
    Map<Model1, Model2> modelMap2;
    HashMap<Model1, Model2> modelMap3;

    public String getStr() {
        return str;
    }

    public void setStr(String str) {
        this.str = str;
    }

    public BigInteger getB() {
        return b;
    }

    public void setB(BigInteger B) {
        this.b = B;
    }

    public List<String> getList() {
        return list;
    }

    public List<Model2> getModel2List() {
        return model2List;
    }

    public Map<String, BigInteger> getModelMap() {
        return modelMap;
    }

    public void setModelMap(Map<String, BigInteger> modelMap) {
        this.modelMap = modelMap;
    }

    public boolean isPrvBool() {
        return prvBool;
    }

    public void setPrvBool(boolean prvBool) {
        this.prvBool = prvBool;
    }

    public Boolean getPrvBoolObj() {
        return prvBoolObj;
    }

    public void setPrvBoolObj(Boolean prvBoolObj) {
        this.prvBoolObj = prvBoolObj;
    }
}

