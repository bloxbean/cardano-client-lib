package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFBaseTest;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFTransactionService;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFUtxoService;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class UtxoTransactionBuilderIT extends BFBaseTest {

    UtxoService utxoService;
    BFTransactionService transactionService;
    UtxoTransactionBuilder utxoTransactionBuilder;

    @BeforeEach
    public void setup() {
        utxoService = new BFUtxoService(Constants.BLOCKFROST_TESTNET_URL, projectId);
        transactionService = new BFTransactionService(Constants.BLOCKFROST_TESTNET_URL, projectId);
        utxoTransactionBuilder = new UtxoTransactionBuilder(utxoService, transactionService);
    }

    @Test
    public void testBuildTransaction() throws AddressExcepion, ApiException {
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        List<PaymentTransaction> paymentTransactionList = Arrays.asList(
                PaymentTransaction.builder()
                .sender(sender)
                .receiver(receiver)
                .amount(BigInteger.valueOf(3000000))
                .fee(BigInteger.valueOf(230000))
                .unit("lovelace")
                .build()
        );

        Transaction transaction
                = utxoTransactionBuilder.buildTransaction(paymentTransactionList, TransactionDetailsParams.builder().ttl(1000).build(), null);

        System.out.println(transaction);

        assertThat(transaction.getBody().getInputs().size(), greaterThan(0));

    }
}
