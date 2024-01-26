package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OgmiosTransactionServiceIT extends OgmiosBaseTest {
    TransactionService transactionService;
    QuickTxBuilder quickTxBuilder;

    String sender1Addr;
    Account sender1;
    String receiver2 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

    @BeforeEach
    public void setup() {
        transactionService = ogmiosBackendService.getTransactionService();
        quickTxBuilder = new QuickTxBuilder(kupmiosBackendService);
        String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
        sender1 = new Account(Networks.testnet(), senderMnemonic);
        sender1Addr = sender1.baseAddress();
    }

    @Test
    void evaluateTx_cbor_error() throws ApiException {
        String txHex = "8484a70081825820d477e2e4de888adb9a35ecfe89b3401588280ed40015c34931e60cc006de991500018182583900a307e23e7f0e2102717aa610f0df3d5e8de80ddc9c099b6036f3aa78592eabbe8d5b88e92932a01c13a511bc04e0f5c1cfbb04ee8dfe07601a0025d4b002000d81825820d477e2e4de888adb9a35ecfe89b3401588280ed40015c34931e60cc006de9915010e81581c7d1fcb835da61dd128c9283510bd26c2d8c6d0439e938f14b5ef941e10825839007d1fcb835da61dd128c9283510bd26c2d8c6d0439e938f14b5ef941e248d073c7065dc990d6a98455f4514f4190a310b60ccda5801cfc36d1a3aaf642e111a000f4240a205818400001a5b39662882192710192710068149480100002221200101f5f6";
        byte[] cbor = HexUtil.decodeHexString(txHex);

        Result<List<EvaluationResult>> evaluationResult = transactionService.evaluateTx(cbor);
        System.out.println(evaluationResult);
        assertThat(evaluationResult.isSuccessful()).isEqualTo(false);
        assertThat(evaluationResult.getResponse()).isNotNull();
    }

    @Test
    void submitTx() {
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
