package com.test;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;

import java.io.DataInputStream;
import java.math.BigInteger;
import java.util.*;

@Constr(alternative = 1)
public class A {
    public Long l = 0L;
    public BigInteger b = BigInteger.valueOf(0);
    public String name = "";
    public String address = "";
    public Optional<Boolean> optionalBoolean;
}

@Constr
public class SuperA {
    public A a;
    public BigInteger c;
    public String country;
    public List<String> days = new ArrayList<>();
    public List<Integer> ints = new ArrayList<>();
    public byte[] bytes;
}

@Constr(alternative = 2)
public class SuperB {
    public SuperA sa;
    public Map<String, A> map = new HashMap<>();
    public BigInteger c;
}

@Constr
public class C {
    public long l;
    public int i;
}


@Constr
public class ClassWithOptional {
    public long l;

    public int i;

    public Optional<Long> k;

    @PlutusIgnore
    public DataInputStream din;
}
