package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class KoiosUtxoServiceIT extends KoiosBaseTest {

    private UtxoService utxoService;

    @BeforeEach
    public void setup() {
        utxoService = backendService.getUtxoService();
    }

    @Test
    public void testGetUtxos() throws ApiException {
        String address = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";

        Result<List<Utxo>> result = utxoService.getUtxos(address, 40, 0);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertTrue(result.getValue().size() > 0);
    }
}
