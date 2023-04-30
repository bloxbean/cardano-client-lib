package com.bloxbean.cardano.client.spec;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigInteger;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class Rational extends UnitInterval {
    public Rational(BigInteger numerator, BigInteger denominator) {
        super(numerator, denominator);
    }
}
