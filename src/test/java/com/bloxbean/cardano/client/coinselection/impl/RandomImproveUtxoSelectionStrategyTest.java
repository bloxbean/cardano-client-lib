package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RandomImproveUtxoSelectionStrategyTest {

    private static final String LIST_2 = "list2";
    private static final String UTXOS_JSON = "utxos.json";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private UtxoSupplier utxoSupplier;

    @BeforeEach
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
    }

    private List<Utxo> loadUtxos(String key) {
        try{
            TypeReference<HashMap<String, List<Utxo>>> typeRef = new TypeReference<>() {};
            var map = objectMapper.readValue(this.getClass().getClassLoader().getResourceAsStream(UTXOS_JSON), typeRef);
            return map.getOrDefault(key, Collections.emptyList());
        }catch(IOException e){
            throw new IllegalStateException("Failed to load utxos", e);
        }
    }

    @Test
    void coinSelectionHappyFlowTest() throws Exception {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);

        given(utxoSupplier.getAll(anyString())).willReturn(utxos);

        UtxoSelectionStrategy selectionStrategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier, true);

        var requested = new Amount(CardanoConstants.LOVELACE, ADAConversionUtil.adaToLovelace(BigDecimal.TEN));
        Set<Utxo> selectedUtxos = selectionStrategy.select(address, requested, null, null, Collections.emptySet(), 40);

        var index = 0;
        while(selectedUtxos.size() != 1){
            selectedUtxos = selectionStrategy.select(address, requested, null, null, Collections.emptySet(), 40);
            if(index > 3){
                break;
            }
            index += 1;
        }
        Assertions.assertEquals(1, selectedUtxos.size());
    }

    @Test
    void coinSelectionHighAmountSelectAll() throws Exception {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getAll(anyString())).willReturn(utxos);

        UtxoSelectionStrategy selectionStrategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier, true);

        var requested = new Amount(CardanoConstants.LOVELACE, BigInteger.valueOf(995770000).add(BigInteger.valueOf(999817955).add(BigInteger.valueOf(983172035))));

        Set<Utxo> selectedUtxos = selectionStrategy.select(address, requested, null, null, Collections.emptySet(), 40);

        Assertions.assertEquals(5, selectedUtxos.size());
    }

    @Test
    void coinSelectionHighAmountSelectRequired() throws Exception {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getAll(anyString())).willReturn(utxos);

        UtxoSelectionStrategy selectionStrategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier, true);

        var requested = new Amount(CardanoConstants.LOVELACE, BigInteger.valueOf(995770000).add(BigInteger.valueOf(999817955).add(BigInteger.valueOf(983172035))).divide(BigInteger.TWO));

        Set<Utxo> selectedUtxos = selectionStrategy.select(address, requested, null, null, Collections.emptySet(), 40);

        var index = 0;
        while(selectedUtxos.size() != 4){
            selectedUtxos = selectionStrategy.select(address, requested, null, null, Collections.emptySet(), 40);
            if(index > 3){
                break;
            }
            index += 1;
        }
        Assertions.assertEquals(4, selectedUtxos.size());

        // must contain utxo-0
        var tx = selectedUtxos.stream().map(it -> it.getTxHash()).collect(Collectors.toSet());
        index = 0;
        while(!tx.contains("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9")){
            selectedUtxos = selectionStrategy.select(address, requested, null, null, Collections.emptySet(), 40);
            tx = selectedUtxos.stream().map(it -> it.getTxHash()).collect(Collectors.toSet());
            if(index > 3){
                break;
            }
            index += 1;
        }
        Assertions.assertTrue(tx.contains("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9"));
    }

    @Test
    void coinSelectionSingleMultiAsset() throws Exception {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getAll(anyString())).willReturn(utxos);

        UtxoSelectionStrategy selectionStrategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier, true);

        var requested = new Amount(unit, BigInteger.ONE);

        Set<Utxo> selectedUtxos = selectionStrategy.select(address, requested, null, null, Collections.emptySet(), 40);

        Assertions.assertEquals(1, selectedUtxos.size());

        // must contain utxo-0
        var tx = selectedUtxos.stream().map(it -> it.getTxHash()).collect(Collectors.toSet());
        Assertions.assertTrue(tx.contains("735262c68b5fa220dee2b447d0d1dd44e0800ba6212dcea7955c561f365fb0e9"));
    }

    @Test
    void coinSelectionSingleMultiAssetWithMultipleUtxos() throws Exception {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getAll(anyString())).willReturn(utxos);

        UtxoSelectionStrategy selectionStrategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier, true);

        var requested = new Amount(unit, BigInteger.valueOf(4000000000L));

        Set<Utxo> selectedUtxos = selectionStrategy.select(address, requested, null, null, Collections.emptySet(), 40);

        Assertions.assertEquals(2, selectedUtxos.size());

        // must contain utxo-3 and utxo-4
        var tx = selectedUtxos.stream().map(it -> it.getTxHash()).collect(Collectors.toSet());
        Assertions.assertTrue(tx.contains("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c"));
        Assertions.assertTrue(tx.contains("e755448d0d17651ff308c2a1d218fbbee5f290924482d85b2bb691576aee5105"));
    }

    @Test
    void coinSelectionSingleMultiAssetWithSignleUtxos() throws Exception {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        String unit = "329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getAll(anyString())).willReturn(utxos);

        UtxoSelectionStrategy selectionStrategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier, true);

        var requested = new Amount(unit, BigInteger.valueOf(200000000L));

        Set<Utxo> selectedUtxos = selectionStrategy.select(address, requested, null, null, Collections.emptySet(), 40);

        Assertions.assertEquals(1, selectedUtxos.size());

        // must contain utxo-4 OR utxo-5
        var tx = selectedUtxos.stream().map(it -> it.getTxHash()).collect(Collectors.toSet());
        Assertions.assertTrue(tx.contains("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c")
                || tx.contains("e755448d0d17651ff308c2a1d218fbbee5f290924482d85b2bb691576aee5105"));
    }

    @Test
    void coinSelectionMustBeRandom() throws Exception {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_2);
        given(utxoSupplier.getAll(anyString())).willReturn(utxos);

        UtxoSelectionStrategy selectionStrategy = new RandomImproveUtxoSelectionStrategy(utxoSupplier, true);

        Set<Utxo> selectedUtxos1 = selectionStrategy.select(address, new Amount(CardanoConstants.LOVELACE, ADAConversionUtil.adaToLovelace(BigDecimal.TEN)), null, null, Collections.emptySet(), 40);
        Set<Utxo> selectedUtxos2 = selectionStrategy.select(address, new Amount(CardanoConstants.LOVELACE, ADAConversionUtil.adaToLovelace(BigDecimal.TEN)), null, null, Collections.emptySet(), 40);

        // since there are only 5 UTXOs we try 3 times (to avoid random failures)
        var index = 0;
        while(selectedUtxos1.equals(selectedUtxos2)){
            selectedUtxos1 = selectionStrategy.select(address, new Amount(CardanoConstants.LOVELACE, ADAConversionUtil.adaToLovelace(BigDecimal.TEN)), null, null, Collections.emptySet(), 40);
            selectedUtxos2 = selectionStrategy.select(address, new Amount(CardanoConstants.LOVELACE, ADAConversionUtil.adaToLovelace(BigDecimal.TEN)), null, null, Collections.emptySet(), 40);
            if(index > 3){
                break;
            }
            index += 1;
        }
        Assertions.assertNotEquals(selectedUtxos1, selectedUtxos2);
    }
}
