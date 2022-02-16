package com.bloxbean.cardano.client.cip;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.backend.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.backend.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;

public class CIPBaseTransactionITTest extends BaseITTest {
    UtxoService utxoService;
    TransactionService transactionService;
    TransactionHelperService transactionHelperService;
    BlockService blockService;
    FeeCalculationService feeCalculationService;
    EpochService epochService;

    String senderMnemonic;
    Account sender;
    String receiver;

    @BeforeEach
    public void setup() {
        BackendService backendService = getBackendService();
        utxoService = backendService.getUtxoService();
        transactionService = backendService.getTransactionService();
        transactionHelperService = backendService.getTransactionHelperService();
        blockService = backendService.getBlockService();
        feeCalculationService = backendService.getFeeCalculationService();
        epochService = backendService.getEpochService();

        senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        sender = new Account(Networks.testnet(), senderMnemonic);
        receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
    }

    protected long getTtl() throws ApiException {
        Block block = blockService.getLastestBlock().getValue();
        long slot = block.getSlot();
        return slot + 2000;
    }

    protected void waitForTransaction(Result<TransactionResult> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = transactionService.getTransaction(result.getValue().getTransactionId());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for transaction to be mined ....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
