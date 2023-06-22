package com.test;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;

import java.math.BigInteger;
import java.util.List;

@Constr(alternative = 1)
public class Model1 {

    @PlutusField
    long l = 0L;

    @PlutusField
    Integer a;

    @PlutusField
    BigInteger b = BigInteger.valueOf(0);

    @PlutusField
    BigInteger ccc = BigInteger.valueOf(0);

    @PlutusField
    String str;

    @PlutusField
    List<String> list;

    @PlutusField
    Model2 model2;

    @PlutusField
    List<Model2> model2List;

    String getStr() {
        return str;
    }

    BigInteger getB() {
        return b;
    }
}

