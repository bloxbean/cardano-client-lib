package com.bloxbean.cardano.client.backend.gql.it;

import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.gql.GqlNetworkInfoService;
import com.bloxbean.cardano.client.backend.gql.GqlTransactionService;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GqlTransactionServiceIT extends GqlBaseTest {
    TransactionService transactionService;

    @BeforeEach
    public void setup() {
        transactionService = backendService.getTransactionService();
    }

    @Test
    public void testGetTransactionByHash() throws ApiException {
        Result<TransactionContent> result = transactionService.getTransaction("6176b7f77a756005d5afb0724df1294f68ac473e30633101872162161a7342e4");

        TransactionContent txContent = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(txContent));
        assertNotNull(txContent);
        assertEquals(txContent.getBlock(), "36b3ff7e10dba3277befb1dda0175cb7ee9f28795ff06a69bbbb19e29347a142");
        assertEquals(txContent.getBlockHeight(), 2587084);
        assertEquals(txContent.getSlot(), 26805265);

        assertTrue(txContent.getOutputAmount().size() > 0 );
    }

    @Test
    public void testGetTransactionUtxos() throws ApiException {
        Result<TxContentUtxo> result = transactionService.getTransactionUtxos("2a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c");

        TxContentUtxo txnUtxos = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(txnUtxos));
        assertNotNull(txnUtxos);

        assertTrue(txnUtxos.getInputs().size() == 1 );
        assertEquals(txnUtxos.getInputs().get(0).getAmount().get(0).getUnit(), "lovelace");
        assertEquals(txnUtxos.getInputs().get(0).getAmount().get(0).getQuantity(), "989402035");
        assertTrue(txnUtxos.getOutputs().size() == 2 );
    }

    @Test
    public void testGetTransactionUtxos2() throws ApiException {
        Result<TxContentUtxo> result = transactionService.getTransactionUtxos("0773d1c7b4ee23d421d54ba5224e501b1a1784f8101509bbaacf96f267c02cfa");

        TxContentUtxo txnUtxos = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(txnUtxos));

        assertNotNull(txnUtxos);
        assertTrue(txnUtxos.getInputs().size() == 1 );
        assertTrue(txnUtxos.getInputs().get(0).getAmount().size() == 6 );
        assertTrue(txnUtxos.getOutputs().size() == 2 );

    }
}
