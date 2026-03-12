package com.bloxbean.cardano.client.ledger.slice;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimplePoolsSliceTest {

    private static final String POOL_ID = "aabb001122334455667788990011223344556677889900112233445566";

    @Test
    void isRegistered_exists_shouldReturnTrue() {
        var slice = new SimplePoolsSlice(Set.of(POOL_ID), Map.of());
        assertThat(slice.isRegistered(POOL_ID)).isTrue();
    }

    @Test
    void isRegistered_notExists_shouldReturnFalse() {
        var slice = new SimplePoolsSlice(Set.of(), Map.of());
        assertThat(slice.isRegistered(POOL_ID)).isFalse();
    }

    @Test
    void getRetirementEpoch_retiring_shouldReturnEpoch() {
        var slice = new SimplePoolsSlice(Set.of(POOL_ID), Map.of(POOL_ID, 100L));
        assertThat(slice.getRetirementEpoch(POOL_ID)).isEqualTo(100L);
    }

    @Test
    void getRetirementEpoch_notRetiring_shouldReturnNegativeOne() {
        var slice = new SimplePoolsSlice(Set.of(POOL_ID), Map.of());
        assertThat(slice.getRetirementEpoch(POOL_ID)).isEqualTo(-1L);
    }
}
