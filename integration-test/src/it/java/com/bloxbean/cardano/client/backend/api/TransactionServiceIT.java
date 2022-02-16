package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFTransactionService;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionServiceIT extends BaseITTest {

    BFTransactionService service;

    @BeforeEach
    public void setup() {
        service = new BFTransactionService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
    }

    @Test
    public void testSubmitInvalidTransaction() throws ApiException {

        Result<String> result = service.submitTransaction(new byte[0]);

        System.out.println(result);
        assertFalse(result.isSuccessful());
        assertEquals(400, result.code());
    }

    @Test
    public void testGetTransaction() throws Exception {
        String txnHash = "6176b7f77a756005d5afb0724df1294f68ac473e30633101872162161a7342e4";
        Result<TransactionContent> result = service.getTransaction(txnHash);

        assertNotNull(result.getValue());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }

    @Test
    public void testGetTransactionUtxos() throws Exception {
        String txnHash = "2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c";
        Result<TxContentUtxo> result = service.getTransactionUtxos(txnHash);

        assertNotNull(result.getValue());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
    }

}
