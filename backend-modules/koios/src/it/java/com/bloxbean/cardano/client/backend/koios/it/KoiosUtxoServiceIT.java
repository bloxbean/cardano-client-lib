package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KoiosUtxoServiceIT extends KoiosBaseTest {

    private UtxoService utxoService;

    @BeforeEach
    public void setup() {
        utxoService = backendService.getUtxoService();
    }

    @Test
    void testGetUtxos() throws ApiException {
        String address = "addr_test1vzpwq95z3xyum8vqndgdd9mdnmafh3djcxnc6jemlgdmswcve6tkw";

        Result<List<Utxo>> result = utxoService.getUtxos(address, 40, 1);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertTrue(result.getValue().size() > 0);
    }

    @Test
    void testGetUtxos_emptyResultIfPageIsNotOne() throws ApiException {
        String address = "addr_test1vzpwq95z3xyum8vqndgdd9mdnmafh3djcxnc6jemlgdmswcve6tkw";

        Result<List<Utxo>> result = utxoService.getUtxos(address, 40, 2);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertEquals(0, result.getValue().size());
    }
}
