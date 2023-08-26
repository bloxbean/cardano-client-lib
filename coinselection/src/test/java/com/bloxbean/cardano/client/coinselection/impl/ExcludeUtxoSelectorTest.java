package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ExcludeUtxoSelectorTest {

    @Mock
    private UtxoSupplier utxoSupplier;
    private List<Utxo> allUtxos;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        allUtxos = List.of(
                Utxo.builder()
                        .txHash("10aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                        .outputIndex(0)
                        .amount(List.of(Amount.ada(3)))
                        .build(),
                Utxo.builder()
                        .txHash("20aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                        .outputIndex(1)
                        .amount(List.of(Amount.ada(2)))
                        .build(),
                Utxo.builder()
                        .txHash("30aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                        .outputIndex(0)
                        .amount(List.of(Amount.ada(5)))
                        .dataHash("datum1")
                        .build(),
                Utxo.builder()
                        .txHash("40aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                        .outputIndex(2)
                        .amount(List.of(Amount.ada(10)))
                        .dataHash("datum1")
                        .build(),
                Utxo.builder()
                        .txHash("50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                        .outputIndex(3)
                        .amount(List.of(Amount.ada(10)))
                        .dataHash("datum1")
                        .build(),
                Utxo.builder()
                        .txHash("60aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                        .outputIndex(2)
                        .amount(List.of(Amount.ada(5)))
                        .build(),
                Utxo.builder()
                        .txHash("70aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                        .outputIndex(2)
                        .amount(List.of(Amount.ada(6)))
                        .dataHash("datum1")
                        .build()

        );
    }

    @Test
    void findFirst_withExcludeList() throws ApiException {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(allUtxos);

        Set<TransactionInput> excludeList = Set.of(
                new TransactionInput("30aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 0),
                new TransactionInput("50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 3)
        );

        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);
        ExcludeUtxoSelector excludeUtxoSelector = new ExcludeUtxoSelector(utxoSelector, excludeList);

        Optional<Utxo> optionalUtxo = excludeUtxoSelector.findFirst(address, utxo -> utxo.getAmount().contains(Amount.ada(5)));
        assertThat(optionalUtxo.isPresent()).isTrue();
        assertThat(optionalUtxo.get().getTxHash()).isEqualTo("60aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e");
    }

    @Test
    void findFirst_withExcludeList_withAdditionalExclude() throws ApiException {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(allUtxos);

        Set<TransactionInput> excludeList = Set.of(
                new TransactionInput("30aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 0)
        );

        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);
        ExcludeUtxoSelector excludeUtxoSelector = new ExcludeUtxoSelector(utxoSelector, excludeList);

        Optional<Utxo> optionalUtxo = excludeUtxoSelector.findFirst(address, utxo -> "datum1".equals(utxo.getDataHash()),
                    Set.of(
                            Utxo.builder()
                                    .txHash("40aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                                    .outputIndex(2).build()
                    ));

        assertThat(optionalUtxo.isPresent()).isTrue();
        assertThat(optionalUtxo.get().getTxHash()).isEqualTo("50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e");
    }

    @Test
    void findAll_withExcludeList() throws ApiException {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(allUtxos);

        Set<TransactionInput> excludeList = Set.of(
                new TransactionInput("30aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 0),
                new TransactionInput("50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 3)
        );

        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);
        ExcludeUtxoSelector excludeUtxoSelector = new ExcludeUtxoSelector(utxoSelector, excludeList);

        List<Utxo> utxos = excludeUtxoSelector.findAll(address, utxo -> "datum1".equals(utxo.getDataHash()), Collections.emptySet());

        assertThat(utxos).hasSize(2);
        assertThat(utxos.stream().map(utxo -> utxo.getTxHash()).collect(Collectors.toList())).contains(
                "40aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                "70aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e");
    }

    @Test
    void findAll_withExcludeList_additionalExcludes() throws ApiException {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(allUtxos);

        Set<TransactionInput> excludeList = Set.of(
                new TransactionInput("30aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 0),
                new TransactionInput("50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 3)
        );

        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);
        ExcludeUtxoSelector excludeUtxoSelector = new ExcludeUtxoSelector(utxoSelector, excludeList);

        List<Utxo> utxos = excludeUtxoSelector.findAll(address, utxo -> "datum1".equals(utxo.getDataHash()),
                Set.of(
                        Utxo.builder()
                                .txHash("40aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                                .outputIndex(2).build()
                ));

        assertThat(utxos).hasSize(1);
        assertThat(utxos.stream().map(utxo -> utxo.getTxHash()).collect(Collectors.toList())).contains(
                "70aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e");
    }

}
