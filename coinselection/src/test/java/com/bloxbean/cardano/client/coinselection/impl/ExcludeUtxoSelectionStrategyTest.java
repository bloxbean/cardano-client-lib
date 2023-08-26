package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExcludeUtxoSelectionStrategyTest {

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
                        .build(),
                Utxo.builder()
                        .txHash("40aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                        .outputIndex(2)
                        .amount(List.of(Amount.ada(10)))
                        .build(),
                Utxo.builder()
                        .txHash("50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                        .outputIndex(3)
                        .amount(List.of(Amount.ada(10)))
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
                        .build()

        );
    }

    @Test
    void select_withExcludeList() {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(allUtxos);

        Set<TransactionInput> excludeList = Set.of(
                new TransactionInput("30aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 0),
                new TransactionInput("50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 3)
        );

        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        ExcludeUtxoSelectionStrategy exUtxoStrategy = new ExcludeUtxoSelectionStrategy(utxoSelectionStrategy, excludeList);

        Set<Utxo> utxos =  exUtxoStrategy.select(address, Amount.ada(26), Collections.emptySet());

        assertThat(utxos).hasSize(5);
        assertThat(utxos.stream().map(u -> u.getTxHash()).collect(Collectors.toList())).doesNotContain("30aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                "50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e");

        assertThat(utxos.stream().map(u -> u.getTxHash()).collect(Collectors.toList()))
                .contains(
                        "10aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "20aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "40aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "60aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "70aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e"
                );
    }

    @Test
    void select_excludeList_withAdditionaExcludes() {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(allUtxos);

        Set<TransactionInput> excludeList = Set.of(
                new TransactionInput("30aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 0),
                new TransactionInput("50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", 3)
        );

        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        ExcludeUtxoSelectionStrategy exUtxoStrategy = new ExcludeUtxoSelectionStrategy(utxoSelectionStrategy, excludeList);

        Set<Utxo> additionalUtxoToExclude = Set.of(
                Utxo.builder()
                        .txHash("20aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e")
                        .outputIndex(1).build());

        Set<Utxo> utxos =  exUtxoStrategy.select(address, Amount.ada(23), additionalUtxoToExclude);

        assertThat(utxos).hasSize(4);
        assertThat(utxos.stream().map(u -> u.getTxHash()).collect(Collectors.toList())).doesNotContain("30aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                "50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e", "20aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e");

        assertThat(utxos.stream().map(u -> u.getTxHash()).collect(Collectors.toList()))
                .contains(
                        "10aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "40aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "60aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "70aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e"
                );

    }

    @Test
    void select_emptyExcludeList() {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(allUtxos);

        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        ExcludeUtxoSelectionStrategy exUtxoStrategy = new ExcludeUtxoSelectionStrategy(utxoSelectionStrategy, Collections.emptySet());

        Set<Utxo> utxos =  exUtxoStrategy.select(address, Amount.ada(41), Collections.emptySet());

        assertThat(utxos).hasSize(7);
        assertThat(utxos.stream().map(u -> u.getTxHash()).collect(Collectors.toList()))
                .contains(
                        "10aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "40aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "60aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "70aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "30aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "50aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e",
                        "20aeba3c30d23f07d202fd8c19386cef84543698bf52081b47408cba9277ea0e"
                );
    }
}
