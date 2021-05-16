package com.bloxbean.cardano.client.it.backend.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.helper.TransactionHelperService;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFBaseTest;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFTransactionService;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFUtxoService;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.backend.model.request.PaymentTransaction;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionSerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

class TransactionHelperServiceIT extends BFBaseTest {
    UtxoService utxoService;
    BFTransactionService transactionService;
    TransactionHelperService transactionHelperService;

    @BeforeEach
    public void setup() {
        utxoService = new BFUtxoService(Constants.BLOCKFROST_TESTNET_URL, projectId);
        transactionService = new BFTransactionService(Constants.BLOCKFROST_TESTNET_URL, projectId);
        transactionHelperService = new TransactionHelperService(utxoService, transactionService);
    }

    @Test
    void transfer() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {

        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(3500000))
                        .fee(BigInteger.valueOf(230000))
                        .unit("lovelace")
                        .build();

        Result<String> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(27530685).build());

        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        System.out.println(result);
    }

    @Test
    void transferMultiAsset() throws TransactionSerializationException, CborException, AddressExcepion, ApiException {
        //Sender address : addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(60))
                        .fee(BigInteger.valueOf(230000))
                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                        .build();

        Result<String> result = transactionHelperService.transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(29804565).build());

        if(result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue());
        else
            System.out.println("Transaction failed: " + result);

        System.out.println(result);
    }
}
