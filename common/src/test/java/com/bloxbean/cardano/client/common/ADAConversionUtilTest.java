package com.bloxbean.cardano.client.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ADAConversionUtilTest {

    @Test
    void adaToLovelace() {
        BigInteger lovelace = ADAConversionUtil.adaToLovelace(new BigDecimal(12.5));
        assertEquals(lovelace, BigInteger.valueOf(12500000));
    }

    @Test
    void lovelaceToAda() {
        BigDecimal ada = ADAConversionUtil.lovelaceToAda(BigInteger.valueOf(2300000));
        assertEquals(ada, BigDecimal.valueOf(2.3));
    }

    @Test
    void adaToLovelace_whenDouble() {
        BigInteger lovelace = ADAConversionUtil.adaToLovelace(5.5);
        assertEquals(lovelace, BigInteger.valueOf(5500000));
    }
}
