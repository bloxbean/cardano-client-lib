package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.address.model.AddressUtxo;
import adlabs.nexus.client.backend.api.address.model.AssetBalance;
import adlabs.nexus.client.backend.api.address.model.InlineDatumValue;
import adlabs.nexus.client.backend.api.address.model.ReferenceScriptValue;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NexusUtxoServiceTest {

    private static final adlabs.nexus.client.util.Network NET = adlabs.nexus.client.util.Network.MAINNET;

    @Test
    void getUtxos_mapsAndCallsSdkWithPageAndPageSizeInOrder() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var txSvc = new NexusTransactionService(
                mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class), NET);

        AddressUtxo plain = AddressUtxo.builder()
                .txHash("txh1").txIndex(0).address("addr1")
                .value("1000000")
                .datumHash("dh1")
                .inlineDatum(InlineDatumValue.builder().bytes("d8799f00ff").build())
                .referenceScript(ReferenceScriptValue.builder().hash("rsh1").build())
                .assets(List.of())
                .build();
        AddressUtxo withAsset = AddressUtxo.builder()
                .txHash("txh2").txIndex(1).address("addr1")
                .value("2000000")
                .assets(List.of(AssetBalance.builder()
                        .unit("policy123.4173736574")
                        .policyId("policy123").assetName("4173736574")
                        .quantity("42").build()))
                .build();

        when(sdkAddressSvc.getAddressUtxos(eq(NET), eq("addr1"), eq(2), eq(10)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(plain, withAsset)));

        var svc = new NexusUtxoService(sdkAddressSvc, txSvc, NET);
        Result<List<Utxo>> r = svc.getUtxos("addr1", 10, 2);

        assertThat(r.isSuccessful()).isTrue();
        List<Utxo> utxos = r.getValue();
        assertThat(utxos).hasSize(2);

        Utxo u1 = utxos.get(0);
        assertThat(u1.getTxHash()).isEqualTo("txh1");
        assertThat(u1.getOutputIndex()).isEqualTo(0);
        assertThat(u1.getAddress()).isEqualTo("addr1");
        assertThat(u1.getDataHash()).isEqualTo("dh1");
        assertThat(u1.getInlineDatum()).isEqualTo("d8799f00ff");
        assertThat(u1.getReferenceScriptHash()).isEqualTo("rsh1");
        assertThat(u1.getAmount()).containsExactly(new Amount(LOVELACE, new BigInteger("1000000")));

        Utxo u2 = utxos.get(1);
        assertThat(u2.getTxHash()).isEqualTo("txh2");
        assertThat(u2.getOutputIndex()).isEqualTo(1);
        assertThat(u2.getAmount()).containsExactly(
                new Amount(LOVELACE, new BigInteger("2000000")),
                new Amount("policy123.4173736574", new BigInteger("42")));

        verify(sdkAddressSvc, times(1)).getAddressUtxos(NET, "addr1", 2, 10);
    }

    @Test
    void getUtxos_error_propagates() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var txSvc = new NexusTransactionService(
                mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class), NET);
        when(sdkAddressSvc.getAddressUtxos(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusUtxoService(sdkAddressSvc, txSvc, NET);
        Result<List<Utxo>> r = svc.getUtxos("addr1", 10, 1);

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getUtxos_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var txSvc = new NexusTransactionService(
                mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class), NET);
        when(sdkAddressSvc.getAddressUtxos(any(), any(), any(Integer.class), any(Integer.class)))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusUtxoService(sdkAddressSvc, txSvc, NET);

        assertThatThrownBy(() -> svc.getUtxos("addr1", 10, 1))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getUtxos_withOrder_ignoresOrderAndStillReturnsMapped() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var txSvc = new NexusTransactionService(
                mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class), NET);

        AddressUtxo u = AddressUtxo.builder()
                .txHash("txh1").txIndex(0).address("addr1").value("500000").assets(List.of()).build();
        when(sdkAddressSvc.getAddressUtxos(eq(NET), eq("addr1"), eq(1), eq(5)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(u)));

        var svc = new NexusUtxoService(sdkAddressSvc, txSvc, NET);
        Result<List<Utxo>> r = svc.getUtxos("addr1", 5, 1, OrderEnum.desc);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
        assertThat(r.getValue().get(0).getTxHash()).isEqualTo("txh1");
    }

    @Test
    void getUtxosByAsset_callsSdkGetAddressUtxosByAsset() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var txSvc = new NexusTransactionService(
                mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class), NET);

        AddressUtxo u = AddressUtxo.builder()
                .txHash("txh1").txIndex(0).address("addr1").value("500000")
                .assets(List.of(AssetBalance.builder().unit("unit1").quantity("7").build()))
                .build();
        when(sdkAddressSvc.getAddressUtxosByAsset(eq(NET), eq("addr1"), eq("unit1"), eq(3), eq(20)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(u)));

        var svc = new NexusUtxoService(sdkAddressSvc, txSvc, NET);
        Result<List<Utxo>> r = svc.getUtxos("addr1", "unit1", 20, 3);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
        assertThat(r.getValue().get(0).getAmount()).containsExactly(
                new Amount(LOVELACE, new BigInteger("500000")),
                new Amount("unit1", new BigInteger("7")));
        verify(sdkAddressSvc, times(1)).getAddressUtxosByAsset(NET, "addr1", "unit1", 3, 20);
    }

    @Test
    void getUtxosByAsset_withOrder_ignoresOrder() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var txSvc = new NexusTransactionService(
                mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class), NET);

        AddressUtxo u = AddressUtxo.builder()
                .txHash("txh1").txIndex(0).address("addr1").value("500000").assets(List.of()).build();
        when(sdkAddressSvc.getAddressUtxosByAsset(eq(NET), eq("addr1"), eq("unit1"), eq(1), eq(20)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(u)));

        var svc = new NexusUtxoService(sdkAddressSvc, txSvc, NET);
        Result<List<Utxo>> r = svc.getUtxos("addr1", "unit1", 20, 1, OrderEnum.asc);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
    }

    @Test
    void getUtxosByAsset_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var txSvc = new NexusTransactionService(
                mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class), NET);
        when(sdkAddressSvc.getAddressUtxosByAsset(any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusUtxoService(sdkAddressSvc, txSvc, NET);

        assertThatThrownBy(() -> svc.getUtxos("addr1", "unit1", 20, 1))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getTxOutput_delegatesToInjectedTransactionService() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var sdkTxSvc = mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class);
        var txSvc = new NexusTransactionService(sdkTxSvc, NET);

        adlabs.nexus.client.backend.api.transaction.model.Utxo output =
                adlabs.nexus.client.backend.api.transaction.model.Utxo.builder()
                        .txHash("txh1").outputIndex(0).address("addr_out")
                        .amount(List.of(adlabs.nexus.client.backend.api.transaction.model.Amount.builder()
                                .unit("lovelace").quantity("900000").build()))
                        .build();
        adlabs.nexus.client.backend.api.transaction.model.TransactionUtxos txUtxos =
                adlabs.nexus.client.backend.api.transaction.model.TransactionUtxos.builder()
                        .hash("txh1")
                        .inputs(List.of())
                        .outputs(List.of(output))
                        .build();
        when(sdkTxSvc.getTransactionUtxos(eq(NET), eq("txh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, txUtxos));

        var svc = new NexusUtxoService(sdkAddressSvc, txSvc, NET);
        Result<Utxo> r = svc.getTxOutput("txh1", 0);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue().getAddress()).isEqualTo("addr_out");
        verify(sdkTxSvc, times(1)).getTransactionUtxos(NET, "txh1");
    }

    @Test
    void isUsedAddress_inheritedDefault_throwsUnsupported() {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var txSvc = new NexusTransactionService(
                mock(adlabs.nexus.client.backend.api.transaction.TransactionService.class), NET);
        var svc = new NexusUtxoService(sdkAddressSvc, txSvc, NET);

        assertThatThrownBy(() -> svc.isUsedAddress("addr1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
