package com.bloxbean.cardano.client.spec;

import lombok.*;

import java.math.BigInteger;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class UnitInterval {
    private BigInteger numerator;
    private BigInteger denominator;

}
