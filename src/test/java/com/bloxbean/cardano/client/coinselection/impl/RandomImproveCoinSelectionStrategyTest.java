package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.coinselection.impl.model.SelectionResult;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

//TODO -- Not complete
@ExtendWith(MockitoExtension.class)
class RandomImproveCoinSelectionStrategyTest {

    private static final String LIST_2 = "list2";
    private static final String UTXOS_JSON = "utxos.json";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private UtxoService utxoService;
    private ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        protocolParams = objectMapper.readValue(this.getClass().getClassLoader().getResourceAsStream("protocol-params.json"), ProtocolParams.class);
    }

    //TODO -- Remove comment
//    @Test
    void coinSelection_HappyFlowTest() throws Exception {
        String address = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
        Map<String, List<Utxo>> map = objectMapper.readValue(this.getClass().getClassLoader().getResourceAsStream(UTXOS_JSON), new TypeReference<Map<String, List<Utxo>>>() {
        });
        List<Utxo> utxos = map.getOrDefault(RandomImproveCoinSelectionStrategyTest.LIST_2, Collections.emptyList());
        given(utxoService.getUtxos(anyString(), anyInt(), eq(1))).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(utxoService.getUtxos(anyString(), anyInt(), eq(2))).willReturn(Result.success(utxos.toString()).withValue(Collections.emptyList()).code(200));
        RandomImproveCoinSelectionStrategy randomImproveCoinSelectionStrategy = new RandomImproveCoinSelectionStrategy(utxoService,protocolParams);
        TransactionOutput transactionOutput = TransactionOutput.builder().address(address).value(Value.builder().coin(ADAConversionUtil.adaToLovelace(BigDecimal.TEN)).build()).build();
        Result<SelectionResult> selectionResult = randomImproveCoinSelectionStrategy.randomImprove(address, new HashSet<>(Arrays.asList(transactionOutput)), 40);
        assertTrue(selectionResult.isSuccessful());
        List<String> txnHashList = selectionResult.getValue().getSelection().stream().map(Utxo::getTxHash).collect(Collectors.toList());
        assertThat(txnHashList).hasSize(1);
    }
}
