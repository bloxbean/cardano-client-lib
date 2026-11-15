package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.UnitInterval;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link RawItems#reduced}: Haskell's {@code n % d} (Data.Ratio). */
class RawItemsTest {

    @Test
    void reducedIsInLowestTermsWithTheSignOnTheNumerator() {
        assertThat(reduced(2, 4)).isEqualTo(ratio(1, 2));
        assertThat(reduced(-1, -2)).isEqualTo(ratio(1, 2));
        assertThat(reduced(1, -3)).isEqualTo(ratio(-1, 3));
        assertThat(reduced(-2, 4)).isEqualTo(ratio(-1, 2));
        assertThat(reduced(0, 5)).isEqualTo(ratio(0, 1));
        assertThat(reduced(0, -5)).isEqualTo(ratio(0, 1));
        assertThat(reduced(7, 7)).isEqualTo(ratio(1, 1));
        assertThat(RawItems.reduced(BigInteger.TWO.pow(65), BigInteger.TWO.pow(66))).isEqualTo(ratio(1, 2));
    }

    /** What a lenient read may give is kept as given. */
    @Test
    void aNullPartOrAZeroDenominatorIsKept() {
        assertThat(RawItems.reduced(null, BigInteger.TWO)).isEqualTo(new UnitInterval(null, BigInteger.TWO));
        assertThat(RawItems.reduced(BigInteger.TWO, null)).isEqualTo(new UnitInterval(BigInteger.TWO, null));
        assertThat(reduced(3, 0)).isEqualTo(ratio(3, 0));
        assertThat(reduced(0, 0)).isEqualTo(ratio(0, 0));
    }

    private static UnitInterval reduced(long numerator, long denominator) {
        return RawItems.reduced(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    private static UnitInterval ratio(long numerator, long denominator) {
        return new UnitInterval(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }
}
