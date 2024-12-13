package com.bloxbean.cardano.hdwallet.supplier;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.hdwallet.Wallet;
import com.bloxbean.cardano.hdwallet.model.WalletUtxo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultWalletUtxoSupplierTest {

    @Mock
    private UtxoService utxoService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getAll() throws ApiException {
        Wallet wallet = new Wallet();
        var addr1 = wallet.getAccount(3).baseAddress();
        var addr2 = wallet.getAccount(7).baseAddress();
        var addr3 = wallet.getAccount(25).baseAddress();
        var addr4 = wallet.getAccount(50).baseAddress();

        DefaultWalletUtxoSupplier utxoSupplier = new DefaultWalletUtxoSupplier(utxoService, wallet);

        given(utxoService.getUtxos(addr1, 100, 1, OrderEnum.asc))
                .willReturn(Result.success("ok").withValue(List.of(
                        Utxo.builder()
                                .address(addr1)
                                .txHash("tx1")
                                .outputIndex(0)
                                .amount(List.of(Amount.builder()
                                                .quantity(BigInteger.valueOf(100))
                                                .unit(LOVELACE)
                                        .build())).build()
                )));

        given(utxoService.getUtxos(addr2, 100, 1, OrderEnum.asc))
                .willReturn(Result.success("ok").withValue(List.of(
                        Utxo.builder()
                                .address(addr2)
                                .txHash("tx2")
                                .outputIndex(0)
                                .amount(List.of(Amount.builder()
                                        .quantity(BigInteger.valueOf(200))
                                        .unit(LOVELACE)
                                        .build())).build()
                )));
        given(utxoService.getUtxos(addr3, 100, 1, OrderEnum.asc))
                .willReturn(Result.success("ok").withValue(List.of(
                        Utxo.builder()
                                .address(addr3)
                                .txHash("tx3")
                                .outputIndex(10)
                                .amount(List.of(Amount.builder()
                                        .quantity(BigInteger.valueOf(300))
                                        .unit(LOVELACE)
                                        .build())).build()
                )));
        given(utxoService.getUtxos(addr4, 40, 1, OrderEnum.asc))
                .willReturn(Result.success("ok").withValue(List.of(
                        Utxo.builder()
                                .address(addr4)
                                .txHash("tx4")
                                .outputIndex(4)
                                .amount(List.of(Amount.builder()
                                        .quantity(BigInteger.valueOf(400))
                                        .unit(LOVELACE)
                                        .build())).build()
                )));



        List<WalletUtxo> utxoList = utxoSupplier.getAll();
        assertThat(utxoList).hasSize(3);
        assertThat(utxoList.stream().map(utxo -> utxo.getAddress()).collect(Collectors.toList()))
                .contains(addr1, addr2, addr3)
                .doesNotContain(addr4);
    }

    @Test
    void getAllWhenIndexesToScan() throws ApiException {
        Wallet wallet = new Wallet();
        wallet.setIndexesToScan(new int[]{25, 50});
        var addr1 = wallet.getAccount(3).baseAddress();
        var addr2 = wallet.getAccount(7).baseAddress();
        var addr3 = wallet.getAccount(25).baseAddress();
        var addr4 = wallet.getAccount(50).baseAddress();

        DefaultWalletUtxoSupplier utxoSupplier = new DefaultWalletUtxoSupplier(utxoService, wallet);

        given(utxoService.getUtxos(addr1, 100, 1, OrderEnum.asc))
                .willReturn(Result.success("ok").withValue(List.of(
                        Utxo.builder()
                                .address(addr1)
                                .txHash("tx1")
                                .outputIndex(0)
                                .amount(List.of(Amount.builder()
                                        .quantity(BigInteger.valueOf(100))
                                        .unit(LOVELACE)
                                        .build())).build()
                )));

        given(utxoService.getUtxos(addr2, 100, 1, OrderEnum.asc))
                .willReturn(Result.success("ok").withValue(List.of(
                        Utxo.builder()
                                .address(addr2)
                                .txHash("tx2")
                                .outputIndex(0)
                                .amount(List.of(Amount.builder()
                                        .quantity(BigInteger.valueOf(200))
                                        .unit(LOVELACE)
                                        .build())).build()
                )));
        given(utxoService.getUtxos(addr3, 100, 1, OrderEnum.asc))
                .willReturn(Result.success("ok").withValue(List.of(
                        Utxo.builder()
                                .address(addr3)
                                .txHash("tx3")
                                .outputIndex(10)
                                .amount(List.of(Amount.builder()
                                        .quantity(BigInteger.valueOf(300))
                                        .unit(LOVELACE)
                                        .build())).build()
                )));
        given(utxoService.getUtxos(addr4, 100, 1, OrderEnum.asc))
                .willReturn(Result.success("ok").withValue(List.of(
                        Utxo.builder()
                                .address(addr4)
                                .txHash("tx4")
                                .outputIndex(4)
                                .amount(List.of(Amount.builder()
                                        .quantity(BigInteger.valueOf(400))
                                        .unit(LOVELACE)
                                        .build())).build()
                )));



        List<WalletUtxo> utxoList = utxoSupplier.getAll();
        assertThat(utxoList).hasSize(2);
        assertThat(utxoList.stream().map(utxo -> utxo.getAddress()).collect(Collectors.toList()))
                .contains(addr3, addr4)
                .doesNotContain(addr1, addr2);
    }

}
