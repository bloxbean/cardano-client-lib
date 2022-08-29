package com.bloxbean.cardano.client.transaction.spec;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigInteger;

@Getter
@EqualsAndHashCode
@ToString
public class Rational extends UnitInterval {
    public Rational(BigInteger numerator, BigInteger denominator) {
        super(numerator, denominator);
    }
}
