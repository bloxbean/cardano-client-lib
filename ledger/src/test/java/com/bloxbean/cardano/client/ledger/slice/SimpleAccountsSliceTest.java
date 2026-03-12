package com.bloxbean.cardano.client.ledger.slice;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleAccountsSliceTest {

    private static final String CRED_HASH = "aabb001122334455667788990011223344556677889900112233445566";

    @Test
    void isRegistered_exists_shouldReturnTrue() {
        var slice = new SimpleAccountsSlice(
                Map.of(CRED_HASH, BigInteger.ZERO),
                Map.of(CRED_HASH, BigInteger.valueOf(2000000))
        );
        assertThat(slice.isRegistered(CRED_HASH)).isTrue();
    }

    @Test
    void isRegistered_notExists_shouldReturnFalse() {
        var slice = new SimpleAccountsSlice(Map.of(), Map.of());
        assertThat(slice.isRegistered(CRED_HASH)).isFalse();
    }

    @Test
    void getRewardBalance_exists_shouldReturnValue() {
        var slice = new SimpleAccountsSlice(
                Map.of(CRED_HASH, BigInteger.valueOf(5000000)),
                Map.of()
        );
        assertThat(slice.getRewardBalance(CRED_HASH)).contains(BigInteger.valueOf(5000000));
    }

    @Test
    void getDeposit_exists_shouldReturnValue() {
        var slice = new SimpleAccountsSlice(
                Map.of(CRED_HASH, BigInteger.ZERO),
                Map.of(CRED_HASH, BigInteger.valueOf(2000000))
        );
        assertThat(slice.getDeposit(CRED_HASH)).contains(BigInteger.valueOf(2000000));
    }

    @Test
    void getDeposit_notExists_shouldReturnEmpty() {
        var slice = new SimpleAccountsSlice(Map.of(), Map.of());
        assertThat(slice.getDeposit(CRED_HASH)).isEmpty();
    }
}
