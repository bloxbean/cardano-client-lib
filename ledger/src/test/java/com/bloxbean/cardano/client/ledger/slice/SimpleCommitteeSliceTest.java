package com.bloxbean.cardano.client.ledger.slice;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleCommitteeSliceTest {

    private static final String COLD_HASH = "aabb001122334455667788990011223344556677889900112233445566";
    private static final String HOT_HASH = "ccdd001122334455667788990011223344556677889900112233445566";

    @Test
    void isMember_exists_shouldReturnTrue() {
        var slice = new SimpleCommitteeSlice(Map.of(COLD_HASH, HOT_HASH), Set.of());
        assertThat(slice.isMember(COLD_HASH)).isTrue();
    }

    @Test
    void isMember_notExists_shouldReturnFalse() {
        var slice = new SimpleCommitteeSlice(Map.of(), Set.of());
        assertThat(slice.isMember(COLD_HASH)).isFalse();
    }

    @Test
    void getHotCredential_exists_shouldReturnValue() {
        var slice = new SimpleCommitteeSlice(Map.of(COLD_HASH, HOT_HASH), Set.of());
        assertThat(slice.getHotCredential(COLD_HASH)).contains(HOT_HASH);
    }

    @Test
    void hasResigned_true_shouldReturnTrue() {
        var slice = new SimpleCommitteeSlice(Map.of(COLD_HASH, HOT_HASH), Set.of(COLD_HASH));
        assertThat(slice.hasResigned(COLD_HASH)).isTrue();
    }

    @Test
    void hasResigned_false_shouldReturnFalse() {
        var slice = new SimpleCommitteeSlice(Map.of(COLD_HASH, HOT_HASH), Set.of());
        assertThat(slice.hasResigned(COLD_HASH)).isFalse();
    }
}
