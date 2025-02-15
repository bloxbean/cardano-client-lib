package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.junit.jupiter.api.Assertions.*;

public class UtxoServiceIT extends BaseITTest {

    @Test
    public void getUtxos() throws ApiException {
        String address = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";

        UtxoService utxoService = getBackendService().getUtxoService();
        Result<List<Utxo>> result = utxoService.getUtxos(address, 100, 1);

        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertNotNull(result.getValue().size() > 0);
    }

    @Test
    public void getUtxosByAsset() throws ApiException {
        String address = "addr_test1qz4jhpa5vv7n4fj3sscqqvesmjwzcvdf79jy8qkfnzusz5sqkhyz5nsz2zfwfr45wp5q2t73rug3m4r8seq2cvhgakpqx679vy";
        String unit = "0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9ef54657374546f6b656e";

        UtxoService utxoService = getBackendService().getUtxoService();
        Result<List<Utxo>> result = utxoService.getUtxos(address, unit, 100, 1);

        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertNotNull(result.getValue().size() == 2);
    }

    @Test
    public void getUtxosByAsset_notExists() throws ApiException {
        String address = "addr_test1qz4jhpa5vv7n4fj3sscqqvesmjwzcvdf79jy8qkfnzusz5sqkhyz5nsz2zfwfr45wp5q2t73rug3m4r8seq2cvhgakpqx679vy";
        String unit = "1df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9ef54657374546f6b656e";

        UtxoService utxoService = getBackendService().getUtxoService();
        Result<List<Utxo>> result = utxoService.getUtxos(address, unit, 100, 1);

        assertTrue(!result.isSuccessful());
        assertTrue(result.code() == 404);
    }

    @Test
    public void getTxOutput_whenOutput_exists_with_datum() throws ApiException {
        String txHash = "33383bfecb9e541d32857eda88b18ffc71943372fe8ae7b4792589b72a41e26e";
        int outputIndex = 2;

        UtxoService utxoService = getBackendService().getUtxoService();
        Result<Utxo> result = utxoService.getTxOutput(txHash, outputIndex);

        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertEquals("addr_test1wpu365zw7petuyvhng78y2l3534g4ammuuexldu5nkjf0sgz5xu88", result.getValue().getAddress());
        assertEquals("4194bb3c4c0fd47485112d09ea85b2dd6ab44fa826b77cbf9ed0f12582b057d9", result.getValue().getDataHash());
        assertEquals("d87a9fd8799fd8799f581cd1707e481671d473ee5a8d561aaac4a1f4e8c937ce61e5d11fc0611fffd8799fd8799f1a000687241b00000187a8d155a2ffffffff", result.getValue().getInlineDatum());
        assertEquals(3, result.getValue().getAmount().size());
        assertEquals(new Amount(LOVELACE, adaToLovelace(2)), result.getValue().getAmount().get(0));
    }

    @Test
    public void getTxOutput_whenOutput_exists_with_referenceInput() throws ApiException {
        String txHash = "d8109586a0dfb1bdc62bec0e6b41f3825994380f32ecad609792587ed3080d10";
        int outputIndex = 0;

        UtxoService utxoService = getBackendService().getUtxoService();
        Result<Utxo> result = utxoService.getTxOutput(txHash, outputIndex);

        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertEquals("addr_test1wzcppsyg36f65jydjsd6fqu3xm7whxu6nmp3pftn9xfgd4ckah4da", result.getValue().getAddress());
        assertEquals("b010c0888e93aa488d941ba4839136fceb9b9a9ec310a573299286d7", result.getValue().getReferenceScriptHash());
        assertEquals(1, result.getValue().getAmount().size());
        assertEquals(new Amount(LOVELACE, adaToLovelace(9.34408)), result.getValue().getAmount().get(0));
    }

    @Test
    public void getTxOutput_whenOutput_invalidIndex() throws ApiException {
        String txHash = "d8109586a0dfb1bdc62bec0e6b41f3825994380f32ecad609792587ed3080d10";
        int outputIndex = 5;

        UtxoService utxoService = getBackendService().getUtxoService();
        Result<Utxo> result = utxoService.getTxOutput(txHash, outputIndex);

        assertFalse(result.isSuccessful());
        assertEquals(404, result.code());
    }

    @Test
    public void getTxOutput_whenOutput_invalidTxHash() throws ApiException {
        String txHash = "e8109586a0dfb1bdc62bec0e6b41f3825994380f32ecad609792587ed3080d10";
        int outputIndex = 5;

        UtxoService utxoService = getBackendService().getUtxoService();
        Result<Utxo> result = utxoService.getTxOutput(txHash, outputIndex);

        assertFalse(result.isSuccessful());
        assertEquals(404, result.code());
    }

    @Test
    void isUsedAddress_whenTxsAvailable() throws ApiException {
        String address = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";

        var hasTxs = getBackendService().getUtxoService().isUsedAddress(address);
        assertTrue(hasTxs);
    }

    @Test
    void isUsedAddress_noTx() throws ApiException {
        String address = "addr_test1qz740lxy55phhat0g6f38kz9d74enw8rmh270ulptqzhu524nndmx9g6y38tql3cx8ydsv7x2et2cvf2tml46qzwjxrslrskyj";

        var hasTxs = getBackendService().getUtxoService().isUsedAddress(address);
        assertFalse(hasTxs);
    }
}
