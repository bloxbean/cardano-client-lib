package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DefaultUtxoSelectionStrategyImplTest {

    private static final String LIST_1 = "list1";
    @Mock
    UtxoSupplier utxoSupplier;

    ObjectMapper objectMapper = new ObjectMapper();

    String dataFile = "utxos-selection-strategy.json";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private List<Utxo> loadUtxos(String key) throws IOException {
        TypeReference<HashMap<String, List<Utxo>>> typeRef
                = new TypeReference<HashMap<String, List<Utxo>>>() {};
        Map<String, List<Utxo>> map = objectMapper.readValue(this.getClass().getClassLoader().getResourceAsStream(dataFile), typeRef);
        return map.getOrDefault(key, Collections.emptyList());
    }

    @Test
    void selectUtxos_returnUtxosWithoutDataHashWhenIgnoreUtxoDataHashIsTrue() throws IOException, ApiException {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        DefaultUtxoSelectionStrategyImpl selectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        List<Utxo> selectedUtxos = selectionStrategy.selectUtxos(address, LOVELACE, new BigInteger("5000"), Collections.EMPTY_SET);

        List<String> txnHashList = selectedUtxos.stream().map(utxo -> utxo.getTxHash()).collect(Collectors.toList());

        assertThat(txnHashList).doesNotContain("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e");
        assertThat(txnHashList).hasSize(3);
    }

    @Test
    void selectUtxos_returnUtxosWithoutDataHashWhenIgnoreUtxoDataHashIsFalse() throws IOException, ApiException {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).willReturn(utxos);

        DefaultUtxoSelectionStrategyImpl selectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        selectionStrategy.setIgnoreUtxosWithDatumHash(false);

        List<Utxo> selectedUtxos = selectionStrategy.selectUtxos(address, LOVELACE, new BigInteger("5000"), Collections.EMPTY_SET);

        List<String> txnHashList = selectedUtxos.stream().map(utxo -> utxo.getTxHash()).collect(Collectors.toList());

        assertThat(txnHashList).contains("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e");
        assertThat(txnHashList).hasSize(3);
    }
}
