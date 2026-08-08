package com.bloxbean.cardano.client.backend.nexus;

import com.bloxbean.cardano.client.api.model.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexusResultMapperTest {
    @Test
    void map_success_convertsValueAndSetsOk() {
        adlabs.nexus.client.backend.api.base.Result<String> sdk =
                adlabs.nexus.client.backend.api.base.Result.success(200, "hi");
        Result<Integer> r = NexusResultMapper.map(sdk, String::length);
        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).isEqualTo(2);
        assertThat(r.code()).isEqualTo(200);
    }

    @Test
    void map_error_propagatesCodeAndResponse() {
        adlabs.nexus.client.backend.api.base.Result<String> sdk =
                adlabs.nexus.client.backend.api.base.Result.error(503, "down");
        Result<Integer> r = NexusResultMapper.map(sdk, String::length);
        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(503);
    }

    @Test
    void map_successWithNullValue_returnsSuccessfulResultWithNullValue() {
        adlabs.nexus.client.backend.api.base.Result<String> sdk =
                adlabs.nexus.client.backend.api.base.Result.success(200, null);
        Result<Integer> r = NexusResultMapper.map(sdk, String::length);
        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).isNull();
    }
}
