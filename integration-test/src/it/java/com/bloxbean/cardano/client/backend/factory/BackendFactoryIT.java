package com.bloxbean.cardano.client.backend.factory;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BaseITTest;
import com.bloxbean.cardano.client.backend.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants.BLOCKFROST_TESTNET_URL;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BackendFactoryIT extends BaseITTest {

    private BackendService backendService;

    @BeforeEach
    public void setup() {
        backendService = BackendFactory.getBlockfrostBackendService(BLOCKFROST_TESTNET_URL, bfProjectId);
    }

    @Test
    public void testGetLatestBlock() throws ApiException {
        Result<Block> latestBlock = backendService.getBlockService().getLastestBlock();

        System.out.println(JsonUtil.getPrettyJson(latestBlock));
        assertThat(latestBlock.getValue(), is(notNullValue()));
    }

    @Test
    public void testSendPaymentTransaction() throws ApiException, CborSerializationException, AddressExcepion, CborDeserializationException {
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        Account sender = new Account(Networks.testnet(), senderMnemonic);
        String receiver = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";

        PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(1500000))
                        .fee(BigInteger.valueOf(230000))
                        .unit("lovelace")
                        .build();

        Block block = backendService.getBlockService().getLastestBlock().getValue();
        Result<TransactionResult> result = backendService.getTransactionHelperService()
                .transfer(paymentTransaction, TransactionDetailsParams.builder().ttl(block.getSlot() + 1000).build());

        System.out.println(JsonUtil.getPrettyJson(Transaction.deserialize(result.getValue().getSignedTxn())));
        if (result.isSuccessful())
            System.out.println("Transaction Id: " + result.getValue().getTransactionId());
        else
            System.out.println("Transaction failed: " + result);

        System.out.println(result);

        assertThat(result.isSuccessful(), Matchers.is(true));
        Result<Block> latestBlock = backendService.getBlockService().getLastestBlock();

        System.out.println(JsonUtil.getPrettyJson(latestBlock));
        assertThat(latestBlock.getValue(), is(notNullValue()));
    }
}
