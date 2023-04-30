package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.transaction.spec.AuxiliaryData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuickTxBuilderIT extends QuickTxBaseIT {
    BackendService backendService;
    Account sender;

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
        sender = new Account(Networks.testnet(), senderMnemonic);
    }

    @Test
    void simplePayment() {
        String receiver = "addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c";
        String receiver2 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        Result<String> result = QuickTxBuilder.newTx(backendService)
                .payToAddress(receiver, new Amount(LOVELACE, adaToLovelace(1.5)))
                .payToAddress(receiver2, new Amount(LOVELACE, adaToLovelace(2.5)))
                .attachMetadata(MessageMetadata.create().add("This is a test message 2"))
                .withSender(sender)
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    @Test
    void simplePayment_withPreAndPostBalanceBuilder() {
        String receiver = "addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c";
        String receiver2 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        Result<String> result = QuickTxBuilder.newTx(backendService)
                .payToAddress(receiver, new Amount(LOVELACE, adaToLovelace(1.5)))
                .payToAddress(receiver2, new Amount(LOVELACE, adaToLovelace(2.5)))
                .attachMetadata(MessageMetadata.create().add("This is a test message 2"))
                .withSender(sender)
                .preBalance((context, txn) -> {
                    System.out.println("Pre balance");
                    AuxiliaryData auxiliaryData = new AuxiliaryData();
                    auxiliaryData.setMetadata(MessageMetadata.create().add("This is a test message in pre balance"));
                    txn.setAuxiliaryData(auxiliaryData);
                }).postBalance((context, txn) -> {
                    System.out.println("Post balance");
                })
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

}
