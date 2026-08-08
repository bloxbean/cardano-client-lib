package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.util.Network;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NexusBackendServiceTest {

    @Test
    void allElevenGetters_returnNonNull() {
        var backendService = new NexusBackendService(Constants.DEFAULT_URL, "key", Network.PREPROD);

        assertThat(backendService.getAssetService()).isNotNull();
        assertThat(backendService.getBlockService()).isNotNull();
        assertThat(backendService.getNetworkInfoService()).isNotNull();
        assertThat(backendService.getPoolService()).isNotNull();
        assertThat(backendService.getTransactionService()).isNotNull();
        assertThat(backendService.getUtxoService()).isNotNull();
        assertThat(backendService.getAddressService()).isNotNull();
        assertThat(backendService.getAccountService()).isNotNull();
        assertThat(backendService.getEpochService()).isNotNull();
        assertThat(backendService.getMetadataService()).isNotNull();
        assertThat(backendService.getScriptService()).isNotNull();
    }

    @Test
    void convenienceCtor_usesDefaultUrl_andReturnsNonNullServices() {
        var backendService = new NexusBackendService(Network.MAINNET);

        assertThat(backendService.getAssetService()).isNotNull();
        assertThat(backendService.getTransactionService()).isNotNull();
        assertThat(backendService.getUtxoService()).isNotNull();
    }
}
