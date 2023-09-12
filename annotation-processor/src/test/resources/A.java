package com.test;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;

import java.io.DataInputStream;
import java.math.BigInteger;
import java.util.*;

@Constr(alternative = 1)
public class A {
    Long l = 0L;
    BigInteger b = BigInteger.valueOf(0);
    String name = "";
    String address = "";
    Optional<Boolean> optionalBoolean;
}

@Constr
class SuperA {
    A a;
    BigInteger c;
    String country;
    List<String> days = new ArrayList<>();
    List<Integer> ints = new ArrayList<>();
    byte[] bytes;
}

@Constr(alternative = 2)
class SuperB {
    SuperA sa;
    Map<String, A> map = new HashMap<>();
    BigInteger c;
}

@Constr
class C {
    long l;
    int i;
}


@Constr
class ClassWithOptional {
    long l;

    int i;

    Optional<Long> k;

    @PlutusIgnore
    DataInputStream din;
}
