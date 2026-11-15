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

    /**
     * The value of a rational as cardano-ledger holds one it reads ({@code n % d} in its {@code decodeRational}): in
     * lowest terms, with the sign on the numerator.
     *
     * @param numerator   the numerator as read
     * @param denominator the denominator as read
     * @return the reduced rational; a null part or a zero denominator is kept as given
     */
    public static UnitInterval reduced(BigInteger numerator, BigInteger denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0)
            return new UnitInterval(numerator, denominator);
        BigInteger gcd = numerator.gcd(denominator);
        BigInteger divisor = denominator.signum() < 0 ? gcd.negate() : gcd;
        return new UnitInterval(numerator.divide(divisor), denominator.divide(divisor));
    }
}
