package com.test;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.annotation.Enc;

import java.math.BigInteger;
import java.util.*;
import java.util.Map.*;

@Constr(alternative = 1)
public class Model1 {
    public long l = 0L;
    public Integer a;
    private BigInteger b = BigInteger.valueOf(0);
    public BigInteger ccc = BigInteger.valueOf(0);
    public Optional<BigInteger> optBI;
    public Optional<Integer> optInt;
    public Optional<Model2> optMod2;
    public Optional<Long> optionalLong;
    public Optional<byte[]> bytes;
    public boolean bool;
    public Boolean boolObj;

    private boolean prvBool;
    private Boolean prvBoolObj;

    @Enc("hex")
    private String str;

    public List<String> list;
    public Model2 model2;
    public List<Model2> model2List;
    public ArrayList<Model2> model3List;
    private Map<String, BigInteger> modelMap;
    public Map<Model1, Model2> modelMap2;
    public HashMap<Model1, Model2> modelMap3;

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

