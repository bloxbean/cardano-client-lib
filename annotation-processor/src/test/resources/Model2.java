package com.test;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;

import java.math.BigInteger;

@Constr(alternative = 2)
public class Model2 {

    @PlutusField
    Long l;

    @PlutusField
    BigInteger b;

    @PlutusField
    BigInteger c;

    @PlutusField
    BigInteger a;

    public Long getL() {
        return l;
    }

    public void setL(Long l) {
        this.l = l;
    }

    public BigInteger getB() {
        return b;
    }

    public void setB(BigInteger b) {
        this.b = b;
    }

    public BigInteger getC() {
        return c;
    }

    public void setC(BigInteger c) {
        this.c = c;
    }

    public BigInteger getA() {
        return a;
    }

    public void setA(BigInteger a) {
        this.a = a;
    }
}
