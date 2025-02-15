package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultUtxoSelectorTest extends BaseTest {

    @Mock
    UtxoSupplier utxoSupplier;

    String LIST_1 = "list1";

    String address = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";

    @BeforeEach
    public void setup() throws IOException, ApiException {
        MockitoAnnotations.openMocks(this);

        utxoJsonFile = "utxos-script-utxo-finders.json";

        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(utxos);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(Collections.emptyList());
    }

    @Test
    void findFirst() throws Exception{
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);

        Optional<Utxo> optional = utxoSelector.findFirst(address, utxo -> utxo.getAmount().size() == 1
                && LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                && utxo.getAmount().get(0).getQuantity().compareTo(BigInteger.valueOf(1500)) == 1);

        assertThat(optional.isPresent()).isTrue();
        assertThat(optional.get().getTxHash()).isEqualTo("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e");
        assertThat(optional.get().getOutputIndex()).isEqualTo(1);
    }

    @Test
    void findFirst_whenExcludeSet() throws Exception {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);

        Set<Utxo> excludeSet = Set.of(Utxo.builder()
            .txHash("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e")
                .outputIndex(1)
                .build()
        );

        Optional<Utxo> optional = utxoSelector.findFirst(address, utxo -> utxo.getAmount().size() == 1
                && LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                && utxo.getAmount().get(0).getQuantity().compareTo(BigInteger.valueOf(1500)) == 1, excludeSet);

        assertThat(optional.isPresent()).isTrue();
        assertThat(optional.get().getTxHash()).isEqualTo("6674e44f0f03915b1611ce58aaff5f2e52054e1911fbcd0f17dbc205f44763b6");
        assertThat(optional.get().getOutputIndex()).isEqualTo(0);
    }

    @Test
    void findFirst_whenAmountNotAvaiable_returnEmpty() throws Exception {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);

        Optional<Utxo> optional = utxoSelector.findFirst(address, utxo -> utxo.getAmount().size() == 1
                && LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                && utxo.getAmount().get(0).getQuantity().compareTo(BigInteger.valueOf(8000)) == 1);

        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    void findAll() throws Exception {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);

        List<Utxo> utxos = utxoSelector.findAll(address, utxo -> utxo.getAmount().size() == 1
                && LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                && utxo.getAmount().get(0).getQuantity().compareTo(BigInteger.valueOf(1500)) == 1);

        assertThat(utxos).hasSize(3);
        assertThat(utxos.get(0).getTxHash()).isEqualTo("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e");
        assertThat(utxos.get(1).getTxHash()).isEqualTo("6674e44f0f03915b1611ce58aaff5f2e52054e1911fbcd0f17dbc205f44763b6");
        assertThat(utxos.get(2).getTxHash()).isEqualTo("a712906ae823ecefe6cab76e5cfd427bd0b6144df10c6f89a56fbdf30fa807f4");
    }

    @Test
    void findAll_whenExcludeSet() throws Exception {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);

        Set<Utxo> excludeSet = Set.of(Utxo.builder()
                .txHash("6674e44f0f03915b1611ce58aaff5f2e52054e1911fbcd0f17dbc205f44763b6")
                .outputIndex(0)
                .build()
        );

        List<Utxo> utxos = utxoSelector.findAll(address, utxo -> utxo.getAmount().size() == 1
                && LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                && utxo.getAmount().get(0).getQuantity().compareTo(BigInteger.valueOf(1500)) == 1, excludeSet);

        assertThat(utxos).hasSize(2);
        assertThat(utxos.get(0).getTxHash()).isEqualTo("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e");
        assertThat(utxos.get(1).getTxHash()).isEqualTo("a712906ae823ecefe6cab76e5cfd427bd0b6144df10c6f89a56fbdf30fa807f4");
    }

    @Test
    void findAll_whenAmountNotAvaiable_returnEmpty() throws Exception {
        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier);

        Set<Utxo> excludeSet = Set.of(Utxo.builder()
                .txHash("6674e44f0f03915b1611ce58aaff5f2e52054e1911fbcd0f17dbc205f44763b6")
                .outputIndex(0)
                .build()
        );

        List<Utxo> utxos = utxoSelector.findAll(address, utxo -> utxo.getAmount().size() == 1
                && LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                && utxo.getAmount().get(0).getQuantity().compareTo(BigInteger.valueOf(8000)) == 1, excludeSet);

        assertThat(utxos).hasSize(0);
    }

    @Test
    void findAll_withPagination() throws Exception {
        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(List.of(utxos.get(0), utxos.get(1)));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(List.of(utxos.get(2), utxos.get(3)));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(2), any())).willReturn(Collections.EMPTY_LIST);

        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier) {
            @Override
            protected int getUtxoFetchSize() {
                return 2;
            }
        };

        List<Utxo> result = utxoSelector.findAll(address, utxo -> utxo.getAmount().size() == 1
                && LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                && utxo.getAmount().get(0).getQuantity().compareTo(BigInteger.valueOf(1500)) == 1);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getTxHash()).isEqualTo("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e");
        assertThat(result.get(1).getTxHash()).isEqualTo("6674e44f0f03915b1611ce58aaff5f2e52054e1911fbcd0f17dbc205f44763b6");
        assertThat(result.get(2).getTxHash()).isEqualTo("a712906ae823ecefe6cab76e5cfd427bd0b6144df10c6f89a56fbdf30fa807f4");
    }

    @Test
    void findAll_withPagination_noMatchingUtxoAvailable() throws Exception {
        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(0), any())).willReturn(List.of(utxos.get(0), utxos.get(1)));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(1), any())).willReturn(List.of(utxos.get(2), utxos.get(3)));
        given(utxoSupplier.getPage(anyString(), anyInt(), eq(2), any())).willReturn(Collections.EMPTY_LIST);

        UtxoSelector utxoSelector = new DefaultUtxoSelector(utxoSupplier) {
            @Override
            protected int getUtxoFetchSize() {
                return 2;
            }
        };

        List<Utxo> result = utxoSelector.findAll(address, utxo -> utxo.getAmount().size() == 1
                && LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                && utxo.getAmount().get(0).getQuantity().compareTo(BigInteger.valueOf(8000)) == 1);

        assertThat(result).hasSize(0);
    }
}
