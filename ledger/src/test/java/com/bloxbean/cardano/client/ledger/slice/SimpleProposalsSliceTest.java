package com.bloxbean.cardano.client.ledger.slice;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleProposalsSliceTest {

    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";

    @Test
    void exists_present_shouldReturnTrue() {
        var slice = new SimpleProposalsSlice(Map.of(TX_HASH + "#0", "PARAMETER_CHANGE_ACTION"));
        assertThat(slice.exists(TX_HASH, 0)).isTrue();
    }

    @Test
    void exists_notPresent_shouldReturnFalse() {
        var slice = new SimpleProposalsSlice(Map.of());
        assertThat(slice.exists(TX_HASH, 0)).isFalse();
    }

    @Test
    void getActionType_present_shouldReturnType() {
        var slice = new SimpleProposalsSlice(Map.of(TX_HASH + "#0", "PARAMETER_CHANGE_ACTION"));
        assertThat(slice.getActionType(TX_HASH, 0)).isEqualTo("PARAMETER_CHANGE_ACTION");
    }

    @Test
    void getActionType_notPresent_shouldReturnNull() {
        var slice = new SimpleProposalsSlice(Map.of());
        assertThat(slice.getActionType(TX_HASH, 0)).isNull();
    }

    @Test
    void addProposal_shouldMakeItExist() {
        var slice = new SimpleProposalsSlice(Map.of());
        slice.addProposal(TX_HASH, 1, "INFO_ACTION");
        assertThat(slice.exists(TX_HASH, 1)).isTrue();
        assertThat(slice.getActionType(TX_HASH, 1)).isEqualTo("INFO_ACTION");
    }
}
