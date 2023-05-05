package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KoiosTransactionServiceIT extends KoiosBaseTest {

    private TransactionService transactionService;

    @BeforeEach
    public void setup() {
        transactionService = backendService.getTransactionService();
    }

    @Test
    void testSubmitInvalidTransaction() throws ApiException {

        Result<String> result = transactionService.submitTransaction(new byte[0]);

        System.out.println(result);
        assertFalse(result.isSuccessful());
        assertEquals(400, result.code());
    }

    @Test
    void testGetTransaction() throws Exception {
        String txnHash = "83b9df2741b964ecd96e44f062e65fad451d22e2ac6ce70a58c56339feda525e";
        Result<TransactionContent> result = transactionService.getTransaction(txnHash);

        assertNotNull(result.getValue());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }

    @Test
    void testGetTransactions() throws Exception {
        String txnHash = "83b9df2741b964ecd96e44f062e65fad451d22e2ac6ce70a58c56339feda525e";
        Result<List<TransactionContent>> result = transactionService.getTransactions(List.of(txnHash));

        assertNotNull(result.getValue());
        assertFalse(result.getValue().isEmpty());
        assertEquals(txnHash, result.getValue().get(0).getHash());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }

    @Test
    void testInvalidTransactionsFormat() {
        List<String> txList = List.of("test", "83b9df2741b964ecd96e44f062e65fad451d22e2ac6ce70a58c56339feda525e");
        assertThrows(ApiException.class, () -> transactionService.getTransactions(txList));
    }

    @Test
    void testTransactionsNotFound() throws ApiException {
        List<String> txList = List.of("83b9");
        Result<List<TransactionContent>> result = transactionService.getTransactions(txList);

        assertFalse(result.isSuccessful());
        assertEquals(404, result.code());
    }

    @Test
    void testGetTransactionUtxos() throws Exception {
        String txnHash = "33383bfecb9e541d32857eda88b18ffc71943372fe8ae7b4792589b72a41e26e";
        Result<TxContentUtxo> result = transactionService.getTransactionUtxos(txnHash);

        assertNotNull(result.getValue());
        Optional<TxContentUtxoOutputs> optionalTxContentUtxoOutputs = result.getValue().getOutputs().stream().filter(txContentUtxoOutput -> txContentUtxoOutput.getOutputIndex() == 2).findAny();
        assertTrue(optionalTxContentUtxoOutputs.isPresent());
        TxContentUtxoOutputs txContentUtxoOutputs = optionalTxContentUtxoOutputs.get();
        assertEquals("4194bb3c4c0fd47485112d09ea85b2dd6ab44fa826b77cbf9ed0f12582b057d9", txContentUtxoOutputs.getDataHash());
        assertEquals("d87a9fd8799fd8799f581cd1707e481671d473ee5a8d561aaac4a1f4e8c937ce61e5d11fc0611fffd8799fd8799f1a000687241b00000187a8d155a2ffffffff", txContentUtxoOutputs.getInlineDatum());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }

    @Test
    void testGetTransactionUtxosNotFound() throws Exception {
        String txnHash = "ac2f821fda7b2488e9f9da05b9013134cfe2958ed210426d44e66136f1b3ca94";
        Result<TxContentUtxo> result = transactionService.getTransactionUtxos(txnHash);

        assertFalse(result.isSuccessful());
        assertEquals(404, result.code());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }
}
