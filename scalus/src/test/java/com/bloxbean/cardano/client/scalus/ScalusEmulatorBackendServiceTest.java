package com.bloxbean.cardano.client.scalus;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.scalus.bridge.SlotConfigBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ScalusEmulatorBackendServiceTest {

    private ProtocolParams protocolParams;
    private Account testAccount;
    private String testAddr;

    @BeforeEach
    void setUp() {
        protocolParams = ScalusTestFixtures.buildTestProtocolParams();
        testAccount = new Account(Networks.testnet());
        testAddr = testAccount.baseAddress();
    }

    @Test
    void shouldInitializeWithDefaultProtocolParams() {
        ScalusEmulatorBackendService backend = ScalusEmulatorBackendService.builder()
                .slotConfig(SlotConfigBridge.preview())
                .build();

        assertThat(backend).isNotNull();
        assertThat(backend.getTransactionService()).isNotNull();
        assertThat(backend.getUtxoService()).isNotNull();
        assertThat(backend.getEpochService()).isNotNull();
        assertThat(backend.getBlockService()).isNotNull();
    }

    @Test
    void shouldReturnProtocolParamsFromEpochService() throws Exception {
        ScalusEmulatorBackendService backend = ScalusEmulatorBackendService.builder()
                .protocolParams(protocolParams)
                .slotConfig(SlotConfigBridge.preview())
                .build();

        Result<ProtocolParams> result = backend.getEpochService().getProtocolParameters();
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getValue()).isEqualTo(protocolParams);
    }

    @Test
    void shouldReturnUtxosForFundedAddress() throws Exception {
        Map<String, Amount> initialFunds = new LinkedHashMap<>();
        initialFunds.put(testAddr, new Amount("lovelace", BigInteger.valueOf(100_000_000_000L)));

        ScalusEmulatorBackendService backend = ScalusEmulatorBackendService.builder()
                .protocolParams(protocolParams)
                .slotConfig(SlotConfigBridge.preview())
                .initialFunds(initialFunds)
                .build();

        Result<List<Utxo>> result = backend.getUtxoService().getUtxos(testAddr, 40, 0);
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getValue()).isNotEmpty();
        assertThat(result.getValue().get(0).getAddress()).isEqualTo(testAddr);
    }

    @Test
    void shouldReturnEmptyUtxosForUnfundedAddress() throws Exception {
        Account unfunded = new Account(Networks.testnet());
        String unfundedAddr = unfunded.baseAddress();

        Map<String, Amount> initialFunds = new LinkedHashMap<>();
        initialFunds.put(testAddr, new Amount("lovelace", BigInteger.valueOf(100_000_000_000L)));

        ScalusEmulatorBackendService backend = ScalusEmulatorBackendService.builder()
                .protocolParams(protocolParams)
                .slotConfig(SlotConfigBridge.preview())
                .initialFunds(initialFunds)
                .build();

        Result<List<Utxo>> result = backend.getUtxoService().getUtxos(unfundedAddr, 40, 0);
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    void shouldThrowForUnsupportedServices() {
        ScalusEmulatorBackendService backend = ScalusEmulatorBackendService.builder()
                .slotConfig(SlotConfigBridge.preview())
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                backend::getAssetService);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                backend::getNetworkInfoService);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                backend::getPoolService);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                backend::getAccountService);
    }

    @Test
    void shouldGetLatestBlock() throws Exception {
        ScalusEmulatorBackendService backend = ScalusEmulatorBackendService.builder()
                .slotConfig(SlotConfigBridge.preview())
                .build();

        var result = backend.getBlockService().getLatestBlock();
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getValue()).isNotNull();
    }

}
