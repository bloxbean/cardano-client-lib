package com.bloxbean.cardano.client.transaction.spec;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigInteger;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class UnitInterval {
    private BigInteger numerator;
    private BigInteger denominator;

}
