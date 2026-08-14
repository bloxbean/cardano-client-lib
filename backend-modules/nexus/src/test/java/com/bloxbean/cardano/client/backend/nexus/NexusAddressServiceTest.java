package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.address.model.AddressInfo;
import adlabs.nexus.client.backend.api.address.model.AddressTransaction;
import adlabs.nexus.client.backend.api.address.model.AssetBalance;
import adlabs.nexus.client.backend.api.address.model.Pagination;
import adlabs.nexus.client.backend.api.address.model.TransactionHistoryItem;
import adlabs.nexus.client.backend.api.address.model.TransactionHistoryResponse;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NexusAddressServiceTest {

    private static final adlabs.nexus.client.util.Network NET = adlabs.nexus.client.util.Network.MAINNET;

    @Test
    void getAddressInfo_maps() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        AddressInfo info = AddressInfo.builder()
                .address("addr1")
                .stakeAddress("stake1")
                .scriptAddress(true)
                .addressType("shelley")
                .balance("5000000")
                .assets(List.of(AssetBalance.builder().unit("policy1.asset1").quantity("10").build()))
                .build();
        when(sdkAddressSvc.getAddressInformation(eq(NET), eq("addr1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, info));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<AddressContent> r = svc.getAddressInfo("addr1");

        assertThat(r.isSuccessful()).isTrue();
        AddressContent ac = r.getValue();
        assertThat(ac.getStakeAddress()).isEqualTo("stake1");
        assertThat(ac.getScript()).isTrue();
        assertThat(ac.getType()).isEqualTo(AddressContent.TypeEnum.SHELLEY);
        assertThat(ac.getAmount()).extracting("unit", "quantity")
                .containsExactlyInAnyOrder(
                        tuple("lovelace", "5000000"),
                        tuple("policy1.asset1", "10"));
    }

    @Test
    void getAddressInfo_error_propagates() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        when(sdkAddressSvc.getAddressInformation(any(), any()))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<AddressContent> r = svc.getAddressInfo("missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getAddressInfo_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        when(sdkAddressSvc.getAddressInformation(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusAddressService(sdkAddressSvc, NET);

        assertThatThrownBy(() -> svc.getAddressInfo("addr1"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getAddressDetails_unsupported() {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var svc = new NexusAddressService(sdkAddressSvc, NET);

        assertThatThrownBy(() -> svc.getAddressDetails("addr1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getTransactions_mapsAndCallsSdkWithPageAndPageSizeInOrder() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        AddressTransaction t1 = AddressTransaction.builder()
                .txHash("txh1").txIndex(0).blockHeight(100L).blockTime(1000L).build();
        AddressTransaction t2 = AddressTransaction.builder()
                .txHash("txh2").txIndex(1).blockHeight(101L).blockTime(1001L).build();
        when(sdkAddressSvc.getAddressTransactions(eq(NET), eq("addr1"), eq(2), eq(10)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(t1, t2)));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<List<AddressTransactionContent>> r = svc.getTransactions("addr1", 10, 2);

        assertThat(r.isSuccessful()).isTrue();
        List<AddressTransactionContent> txs = r.getValue();
        assertThat(txs).hasSize(2);
        assertThat(txs.get(0).getTxHash()).isEqualTo("txh1");
        assertThat(txs.get(0).getTxIndex()).isEqualTo(0);
        assertThat(txs.get(0).getBlockHeight()).isEqualTo(100L);
        assertThat(txs.get(0).getBlockTime()).isEqualTo(1000L);
        verify(sdkAddressSvc, times(1)).getAddressTransactions(NET, "addr1", 2, 10);
    }

    @Test
    void getTransactions_withOrder_ignoresOrder() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        AddressTransaction t1 = AddressTransaction.builder()
                .txHash("txh1").txIndex(0).blockHeight(100L).blockTime(1000L).build();
        when(sdkAddressSvc.getAddressTransactions(eq(NET), eq("addr1"), eq(1), eq(5)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(t1)));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<List<AddressTransactionContent>> r = svc.getTransactions("addr1", 5, 1, OrderEnum.desc);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
    }

    @Test
    void getTransactions_error_propagates() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        when(sdkAddressSvc.getAddressTransactions(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<List<AddressTransactionContent>> r = svc.getTransactions("addr1", 10, 1);

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getAllTransactions_pagesUntilHasNextFalse_accumulatesAndMaps() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        TransactionHistoryItem i1 = TransactionHistoryItem.builder().txHash("tx1").txTimestamp(1000L).blockHeight(100L).build();
        TransactionHistoryItem i2 = TransactionHistoryItem.builder().txHash("tx2").txTimestamp(1001L).blockHeight(101L).build();
        TransactionHistoryItem i3 = TransactionHistoryItem.builder().txHash("tx3").txTimestamp(1002L).blockHeight(102L).build();
        TransactionHistoryResponse page1 = TransactionHistoryResponse.builder()
                .transactions(List.of(i1))
                .pagination(Pagination.builder().hasNext(true).build())
                .build();
        TransactionHistoryResponse page2 = TransactionHistoryResponse.builder()
                .transactions(List.of(i2, i3))
                .pagination(Pagination.builder().hasNext(false).build())
                .build();
        when(sdkAddressSvc.getAddressTransactionHistory(eq(NET), eq("addr1"), eq(1), any(Integer.class)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, page1));
        when(sdkAddressSvc.getAddressTransactionHistory(eq(NET), eq("addr1"), eq(2), any(Integer.class)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, page2));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<List<AddressTransactionContent>> r = svc.getAllTransactions("addr1", OrderEnum.asc, null, null);

        assertThat(r.isSuccessful()).isTrue();
        List<AddressTransactionContent> txs = r.getValue();
        assertThat(txs).extracting(AddressTransactionContent::getTxHash).containsExactly("tx1", "tx2", "tx3");
        assertThat(txs.get(0).getTxIndex()).isEqualTo(0);
        assertThat(txs.get(0).getBlockHeight()).isEqualTo(100L);
        assertThat(txs.get(0).getBlockTime()).isEqualTo(1000L);
        verify(sdkAddressSvc, times(1)).getAddressTransactionHistory(eq(NET), eq("addr1"), eq(1), any(Integer.class));
        verify(sdkAddressSvc, times(1)).getAddressTransactionHistory(eq(NET), eq("addr1"), eq(2), any(Integer.class));
        verify(sdkAddressSvc, times(0)).getAddressTransactionHistory(eq(NET), eq("addr1"), eq(3), any(Integer.class));
    }

    @Test
    void getAllTransactions_filtersByBlockHeightRange() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        TransactionHistoryItem inRange = TransactionHistoryItem.builder().txHash("tx-in").txTimestamp(1000L).blockHeight(150L).build();
        TransactionHistoryItem belowRange = TransactionHistoryItem.builder().txHash("tx-below").txTimestamp(999L).blockHeight(50L).build();
        TransactionHistoryItem aboveRange = TransactionHistoryItem.builder().txHash("tx-above").txTimestamp(1002L).blockHeight(500L).build();
        TransactionHistoryResponse page1 = TransactionHistoryResponse.builder()
                .transactions(List.of(inRange, belowRange, aboveRange))
                .pagination(Pagination.builder().hasNext(false).build())
                .build();
        when(sdkAddressSvc.getAddressTransactionHistory(eq(NET), eq("addr1"), eq(1), any(Integer.class)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, page1));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<List<AddressTransactionContent>> r = svc.getAllTransactions("addr1", OrderEnum.asc, 100, 200);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).extracting(AddressTransactionContent::getTxHash).containsExactly("tx-in");
    }

    @Test
    void getAllTransactions_descOrder_reversesResult() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        TransactionHistoryItem i1 = TransactionHistoryItem.builder().txHash("tx1").txTimestamp(1000L).blockHeight(100L).build();
        TransactionHistoryItem i2 = TransactionHistoryItem.builder().txHash("tx2").txTimestamp(1001L).blockHeight(101L).build();
        TransactionHistoryResponse page1 = TransactionHistoryResponse.builder()
                .transactions(List.of(i1, i2))
                .pagination(Pagination.builder().hasNext(false).build())
                .build();
        when(sdkAddressSvc.getAddressTransactionHistory(eq(NET), eq("addr1"), eq(1), any(Integer.class)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, page1));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<List<AddressTransactionContent>> r = svc.getAllTransactions("addr1", OrderEnum.desc, null, null);

        assertThat(r.getValue()).extracting(AddressTransactionContent::getTxHash).containsExactly("tx2", "tx1");
    }

    @Test
    void getAllTransactions_unsuccessfulPage_returnsErrorResult() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        when(sdkAddressSvc.getAddressTransactionHistory(eq(NET), eq("addr1"), eq(1), any(Integer.class)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(500, "boom"));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<List<AddressTransactionContent>> r = svc.getAllTransactions("addr1", OrderEnum.asc, null, null);

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(500);
    }

    @Test
    void getAllTransactions_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        when(sdkAddressSvc.getAddressTransactionHistory(any(), any(), any(Integer.class), any(Integer.class)))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusAddressService(sdkAddressSvc, NET);

        assertThatThrownBy(() -> svc.getAllTransactions("addr1", OrderEnum.asc, null, null))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
