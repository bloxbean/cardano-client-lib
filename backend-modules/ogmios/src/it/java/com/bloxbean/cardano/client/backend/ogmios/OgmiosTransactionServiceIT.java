package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OgmiosTransactionServiceIT extends OgmiosBaseTest{
    TransactionService transactionService;
    QuickTxBuilder quickTxBuilder;

    String sender1Addr;
    Account sender1;
    String receiver1 = "addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c";
    String receiver2 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

    @BeforeEach
    public void setup() {
        transactionService = ogmiosBackendService.getTransactionService();
        kupmiosBackendService = new KupmiosBackendService("http://localhost:1337", "http://localhost:1442");
        quickTxBuilder = new QuickTxBuilder(kupmiosBackendService);
        String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
        sender1 = new Account(Networks.testnet(), senderMnemonic);
        sender1Addr = sender1.baseAddress();

    }

    @Test
    void evaluateTx() throws ApiException, CborSerializationException {
        Tx tx = new Tx()
                .payToAddress(receiver2, Amount.ada(1.5))
                .from(sender1Addr);

        Transaction transaction = quickTxBuilder.compose(tx).withSigner(SignerProviders.signerFrom(sender1)).buildAndSign();
        Result<List<EvaluationResult>> evaluationResults = transactionService.evaluateTx(transaction.serialize());

        assertThat(evaluationResults.isSuccessful()).isEqualTo(false);
        assertThat(evaluationResults.getValue()).isEmpty();
    }

    @Test
    void submitTx() throws ApiException {
        Tx tx = new Tx()
                .payToAddress(receiver2, Amount.ada(1.5))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
    }
}
