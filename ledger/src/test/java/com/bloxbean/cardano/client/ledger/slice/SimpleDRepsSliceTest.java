package com.bloxbean.cardano.client.ledger.slice;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleDRepsSliceTest {

    private static final String DREP_HASH = "aabb001122334455667788990011223344556677889900112233445566";

    @Test
    void isRegistered_exists_shouldReturnTrue() {
        var slice = new SimpleDRepsSlice(Map.of(DREP_HASH, BigInteger.valueOf(500000000)));
        assertThat(slice.isRegistered(DREP_HASH)).isTrue();
    }

    @Test
    void isRegistered_notExists_shouldReturnFalse() {
        var slice = new SimpleDRepsSlice(Map.of());
        assertThat(slice.isRegistered(DREP_HASH)).isFalse();
    }

    @Test
    void getDeposit_exists_shouldReturnValue() {
        var slice = new SimpleDRepsSlice(Map.of(DREP_HASH, BigInteger.valueOf(500000000)));
        assertThat(slice.getDeposit(DREP_HASH)).contains(BigInteger.valueOf(500000000));
    }

    @Test
    void getDeposit_notExists_shouldReturnEmpty() {
        var slice = new SimpleDRepsSlice(Map.of());
        assertThat(slice.getDeposit(DREP_HASH)).isEmpty();
    }
}
