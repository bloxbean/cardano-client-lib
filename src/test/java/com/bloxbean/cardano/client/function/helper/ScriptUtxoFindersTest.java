package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.BaseTest;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScriptUtxoFindersTest extends BaseTest {

    @Mock
    UtxoService utxoService;

    private String LIST_1 = "list1";

    @BeforeEach
    public void setup() throws IOException, ApiException {
        MockitoAnnotations.openMocks(this);

        utxoJsonFile = "utxos-script-utxo-finders.json";
    }

    @Test
    void findFirstByDatum() throws IOException, ApiException {
        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoService.getUtxos(any(), anyInt(), eq(1), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(utxoService.getUtxos(any(), anyInt(), eq(2), any())).willReturn(Result.success(utxos.toString()).withValue(Collections.EMPTY_LIST).code(200));

        String scriptAddress = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";
        String datum = "hello";

        Optional<Utxo> utxoOptional = ScriptUtxoFinders.findFirstByDatum(utxoService, scriptAddress, datum);

        assertThat(utxoOptional.get().getTxHash()).isEqualTo("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e");
        assertThat(utxoOptional.get().getOutputIndex()).isEqualTo(1);
        assertThat(utxoOptional.get().getDataHash()).isEqualTo("6788d45960488558919fe195e3e1a51a3cde19903793e57712d682f1b7e3d0bb");
    }

    @Test
    void findFirstByDatumHash() throws IOException, ApiException {
        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoService.getUtxos(any(), anyInt(), eq(1), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(utxoService.getUtxos(any(), anyInt(), eq(2), any())).willReturn(Result.success(utxos.toString()).withValue(Collections.EMPTY_LIST).code(200));

        String scriptAddress = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";
        String datumHash = "6788d45960488558919fe195e3e1a51a3cde19903793e57712d682f1b7e3d0bb";

        Optional<Utxo> utxoOptional = ScriptUtxoFinders.findFirstByDatumHash(utxoService, scriptAddress, datumHash);

        assertThat(utxoOptional.get().getTxHash()).isEqualTo("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e");
        assertThat(utxoOptional.get().getOutputIndex()).isEqualTo(1);
        assertThat(utxoOptional.get().getDataHash()).isEqualTo("6788d45960488558919fe195e3e1a51a3cde19903793e57712d682f1b7e3d0bb");
    }

    @Test
    void findAllByDatum() throws IOException, ApiException {
        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoService.getUtxos(any(), anyInt(), eq(1), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(utxoService.getUtxos(any(), anyInt(), eq(2), any())).willReturn(Result.success(utxos.toString()).withValue(Collections.EMPTY_LIST).code(200));

        String scriptAddress = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";
        String datum = "hello";

        List<Utxo> list = ScriptUtxoFinders.findAllByDatum(utxoService, scriptAddress, datum);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getTxHash()).isEqualTo("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e");
        assertThat(list.get(0).getOutputIndex()).isEqualTo(1);
        assertThat(list.get(0).getDataHash()).isEqualTo("6788d45960488558919fe195e3e1a51a3cde19903793e57712d682f1b7e3d0bb");

        assertThat(list.get(1).getTxHash()).isEqualTo("6674e44f0f03915b1611ce58aaff5f2e52054e1911fbcd0f17dbc205f44763b6");
        assertThat(list.get(1).getOutputIndex()).isEqualTo(0);
        assertThat(list.get(1).getDataHash()).isEqualTo("6788d45960488558919fe195e3e1a51a3cde19903793e57712d682f1b7e3d0bb");
    }

    @Test
    void findAllByDatumHash() throws IOException, ApiException {
        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoService.getUtxos(any(), anyInt(), eq(1), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(utxoService.getUtxos(any(), anyInt(), eq(2), any())).willReturn(Result.success(utxos.toString()).withValue(Collections.EMPTY_LIST).code(200));

        String scriptAddress = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";
        String datumHash = "6788d45960488558919fe195e3e1a51a3cde19903793e57712d682f1b7e3d0bb";

        List<Utxo> list = ScriptUtxoFinders.findAllByDatumHash(utxoService, scriptAddress, datumHash);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getTxHash()).isEqualTo("88c014d348bf1919c78a5cb87a5beed87729ff3f8a2019be040117a41a83e82e");
        assertThat(list.get(0).getOutputIndex()).isEqualTo(1);
        assertThat(list.get(0).getDataHash()).isEqualTo("6788d45960488558919fe195e3e1a51a3cde19903793e57712d682f1b7e3d0bb");

        assertThat(list.get(1).getTxHash()).isEqualTo("6674e44f0f03915b1611ce58aaff5f2e52054e1911fbcd0f17dbc205f44763b6");
        assertThat(list.get(1).getOutputIndex()).isEqualTo(0);
        assertThat(list.get(1).getDataHash()).isEqualTo("6788d45960488558919fe195e3e1a51a3cde19903793e57712d682f1b7e3d0bb");
    }

    @Test
    void findFirstByDatum_whenNoUtxoAvailable() throws IOException, ApiException {
        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoService.getUtxos(any(), anyInt(), eq(1), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(utxoService.getUtxos(any(), anyInt(), eq(2), any())).willReturn(Result.success(utxos.toString()).withValue(Collections.EMPTY_LIST).code(200));

        String scriptAddress = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";
        String datum = "hello111";

        Optional<Utxo> utxoOptional = ScriptUtxoFinders.findFirstByDatum(utxoService, scriptAddress, datum);

        assertThat(utxoOptional.isPresent()).isEqualTo(false);
    }

    @Test
    void findAllByDatumHash_whenNoUtxoAvailable() throws IOException, ApiException {
        List<Utxo> utxos = loadUtxos(LIST_1);
        given(utxoService.getUtxos(any(), anyInt(), eq(1), any())).willReturn(Result.success(utxos.toString()).withValue(utxos).code(200));
        given(utxoService.getUtxos(any(), anyInt(), eq(2), any())).willReturn(Result.success(utxos.toString()).withValue(Collections.EMPTY_LIST).code(200));

        String scriptAddress = "addr_test1wqryj32h6d4srdj720nqxy4hew26anzx8h7lny79qlum89s5hrkh0";
        String datumHash = "6788d45960488558919fe195e3e1a51a3cde19903793e57712d682f1b7e3d011";

        List<Utxo> list = ScriptUtxoFinders.findAllByDatumHash(utxoService, scriptAddress, datumHash);

        assertThat(list).hasSize(0);
    }

}
