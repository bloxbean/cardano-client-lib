package com.bloxbean.cardano.client.jna;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.transaction.model.Transaction;
import com.bloxbean.cardano.client.transaction.model.TransactionBody;
import com.bloxbean.cardano.client.transaction.model.TransactionInput;
import com.bloxbean.cardano.client.transaction.model.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class TransactionJNATest {

    @Test
    public void testSignPaymentTransaction() throws CborException, AddressExcepion {
        TransactionBody txnBody = new TransactionBody();

        TransactionInput txnInput = new TransactionInput();

        txnInput.setTransactionId(HexUtil.decodeHexString("dcac27eed284adfa6ec02a6e8fa41f886faf267bff7a6e615df44ab8a311360d"));
        txnInput.setIndex(1);

        List<TransactionInput> inputList = new ArrayList<>();
        inputList.add(txnInput);
        txnBody.setInputs(inputList);

        //Total : 994632035
        TransactionOutput txnOutput =  new TransactionOutput();
        txnOutput.setAddress(Account.toBytes("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v"));
        txnOutput.setValue(new BigInteger("5000000"));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress(Account.toBytes("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y"));
        changeOutput.setValue(new BigInteger("989264070"));

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput);
        outputs.add(changeOutput);

        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger("367965"));
        txnBody.setTtl(26194586);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);
        String hexStr = transaction.serializeToHex();
        System.out.println(hexStr);

        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        Account signingAccount = new Account(Networks.testnet(), mnemonic);
        System.out.println(signingAccount.getBech32PrivateKey());

        String signTxnHex = CardanoJNA.INSTANCE.signPaymentTransaction(hexStr, signingAccount.getBech32PrivateKey());
        byte[] signedTxnBytes = HexUtil.decodeHexString(signTxnHex);

        Assertions.assertTrue(signedTxnBytes.length > 0);
    }
}
