package com.bloxbean.cardano.client.cip.cip102;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoyaltyFeeUtilTest {

    @ParameterizedTest(name = "{0}% -> chain fee {1}")
    @CsvSource({
            "1.6,  625",   // spec example
            "2.0,  500",
            "5.0,  200",
            "10.0, 100",
            "0.5,  2000",
            "0.1,  10000",
            "100.0, 10"
    })
    void testToChainFee(double percent, long expected) {
        assertThat(RoyaltyFeeUtil.toChainFee(percent)).isEqualTo(BigInteger.valueOf(expected));
    }

    @ParameterizedTest(name = "chain fee {0} -> {1}%")
    @CsvSource({
            "625,  1.6",
            "500,  2.0",
            "200,  5.0",
            "100,  10.0",
            "2000, 0.5",
            "10,   100.0"
    })
    void testFromChainFee(long chainFee, double expectedPercent) {
        assertThat(RoyaltyFeeUtil.fromChainFee(BigInteger.valueOf(chainFee)))
                .isEqualTo(expectedPercent);
    }

    @Test
    void testToChainFee_belowMinimum_throws() {
        assertThatThrownBy(() -> RoyaltyFeeUtil.toChainFee(0.09))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToChainFee_aboveMaximum_throws() {
        assertThatThrownBy(() -> RoyaltyFeeUtil.toChainFee(100.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFromChainFee_zero_throws() {
        assertThatThrownBy(() -> RoyaltyFeeUtil.fromChainFee(BigInteger.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCalculateRoyaltyAmount_noMinMax() {
        // 2% of 100 ADA (10_000_000_000 lovelace) = 2 ADA = 2_000_000_000 lovelace
        BigInteger salePrice = BigInteger.valueOf(100_000_000_000L);
        BigInteger chainFee = RoyaltyFeeUtil.toChainFee(2.0); // 500

        BigInteger royalty = RoyaltyFeeUtil.calculateRoyaltyAmount(
                chainFee, salePrice, Optional.empty(), Optional.empty());

        // (10 / 500) * 100_000_000_000 = 0.02 * 100_000_000_000 = 2_000_000_000
        assertThat(royalty).isEqualTo(BigInteger.valueOf(2_000_000_000L));
    }

    @Test
    void testCalculateRoyaltyAmount_cappedByMax() {
        BigInteger salePrice = BigInteger.valueOf(100_000_000_000L);
        BigInteger chainFee = RoyaltyFeeUtil.toChainFee(2.0); // 500
        BigInteger maxFee = BigInteger.valueOf(500_000_000L); // 0.5 ADA cap

        BigInteger royalty = RoyaltyFeeUtil.calculateRoyaltyAmount(
                chainFee, salePrice, Optional.empty(), Optional.of(maxFee));

        assertThat(royalty).isEqualTo(maxFee);
    }

    @Test
    void testCalculateRoyaltyAmount_flooredByMin() {
        BigInteger salePrice = BigInteger.valueOf(1_000_000L); // cheap NFT: 1 ADA
        BigInteger chainFee = RoyaltyFeeUtil.toChainFee(2.0); // 500
        BigInteger minFee = BigInteger.valueOf(500_000L); // 0.5 ADA minimum

        // pct = (10/500) * 1_000_000 = 20_000 lovelace — below minimum
        BigInteger royalty = RoyaltyFeeUtil.calculateRoyaltyAmount(
                chainFee, salePrice, Optional.of(minFee), Optional.empty());

        assertThat(royalty).isEqualTo(minFee);
    }

    @Test
    void testCalculateRoyaltyAmount_withinMinAndMax() {
        BigInteger salePrice = BigInteger.valueOf(10_000_000_000L); // 10_000 ADA
        BigInteger chainFee = RoyaltyFeeUtil.toChainFee(2.0); // 500
        BigInteger minFee = BigInteger.valueOf(1_000_000L);      // 1 ADA
        BigInteger maxFee = BigInteger.valueOf(500_000_000_000L); // 500 ADA

        // pct = (10/500) * 10_000_000_000 = 200_000_000 lovelace (200 ADA) — within range
        BigInteger royalty = RoyaltyFeeUtil.calculateRoyaltyAmount(
                chainFee, salePrice, Optional.of(minFee), Optional.of(maxFee));

        assertThat(royalty).isEqualTo(BigInteger.valueOf(200_000_000L));
    }
}
