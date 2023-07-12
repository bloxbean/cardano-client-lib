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
}
