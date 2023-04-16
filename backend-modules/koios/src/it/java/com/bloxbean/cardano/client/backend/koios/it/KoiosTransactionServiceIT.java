package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void testGetTransactionUtxos() throws Exception {
        String txnHash = "83b9df2741b964ecd96e44f062e65fad451d22e2ac6ce70a58c56339feda525e";
        Result<TxContentUtxo> result = transactionService.getTransactionUtxos(txnHash);

        assertNotNull(result.getValue());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }
}
