package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

//TODO -- These tests are from JNA era. Check if these tests are still required. If not, remove.
public class TransactionExTest {

    @Test
    public void testSignPaymentTransaction() throws AddressExcepion, CborSerializationException {
        TransactionBody txnBody = new TransactionBody();

        TransactionInput txnInput = new TransactionInput();

        txnInput.setTransactionId("dcac27eed284adfa6ec02a6e8fa41f886faf267bff7a6e615df44ab8a311360d");
        txnInput.setIndex(1);

        List<TransactionInput> inputList = new ArrayList<>();
        inputList.add(txnInput);
        txnBody.setInputs(inputList);

        //Total : 994632035
        TransactionOutput txnOutput =  new TransactionOutput();
        txnOutput.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput.setValue(new Value(new BigInteger("5000000"), null));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");
        changeOutput.setValue(new Value(new BigInteger("989264070"), null));

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

        String signTxnHex = signingAccount.sign(transaction).serializeToHex();
        byte[] signedTxnBytes = HexUtil.decodeHexString(signTxnHex);

        Assertions.assertTrue(signedTxnBytes.length > 100);
    }

    @Test
    public void testSignPaymentTransactionMultiAccount() throws Exception {
        TransactionBody txnBody = new TransactionBody();

        long fee = 367965;
        int ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId("73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002"); //989264070
        txnInput.setIndex(1);
        long balance1 = 989264070;

        TransactionInput txnInput2 = new TransactionInput();
        txnInput2.setTransactionId("8e03a93578dc0acd523a4dd861793068a06a68b8a6c7358d0c965d2864067b68"); //1000000000
        txnInput2.setIndex(0);
        long balance2 = 1000000000;

        List<TransactionInput> inputList = new ArrayList<>();
        inputList.add(txnInput);
        inputList.add(txnInput2);
        txnBody.setInputs(inputList);

        //Output 1
        long amount1 = 5000000;
        long changeAmount1 = balance1 - amount1 - fee;
        TransactionOutput txnOutput =  new TransactionOutput();
        txnOutput.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput.setValue(new Value(new BigInteger(String.valueOf(amount1)), null));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");
        changeOutput.setValue(new Value(new BigInteger(String.valueOf(changeAmount1)), null));

        //Output2
        long amount2 = 8000000;
        long changeAmount2 = balance2 - amount2 - fee;
        TransactionOutput txnOutput2 =  new TransactionOutput();
        txnOutput2.setAddress("addr_test1qrynkm9vzsl7vrufzn6y4zvl2v55x0xwc02nwg00x59qlkxtsu6q93e6mrernam0k4vmkn3melezkvgtq84d608zqhnsn48axp");
        txnOutput2.setValue(new Value(new BigInteger(String.valueOf(amount2)), null));

        TransactionOutput changeOutput2 =  new TransactionOutput();
        changeOutput2.setAddress("addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82");
        changeOutput2.setValue(new Value(new BigInteger(String.valueOf(changeAmount2)), null));

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput);
        outputs.add(changeOutput);
        outputs.add(txnOutput2);
        outputs.add(changeOutput2);

        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger(String.valueOf(fee * 2)));
        txnBody.setTtl(ttl);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);
        String hexStr = transaction.serializeToHex();
        System.out.println(hexStr);

        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        Account signingAccount = new Account(Networks.testnet(), mnemonic);
        System.out.println(signingAccount.getBech32PrivateKey());

        String signTxnHex = signingAccount.sign(transaction).serializeToHex();
        byte[] signedTxnBytes = HexUtil.decodeHexString(signTxnHex);

        //Sign with account 2
        String mnemonic2 = "mixture peasant wood unhappy usage hero great elder emotion picnic talent fantasy program clean patch wheel drip disorder bullet cushion bulk infant balance address";
        Account signingAccount2 = new Account(Networks.testnet(), mnemonic2);
        System.out.println(signingAccount2.getBech32PrivateKey());

        String signTxnHex2 = signingAccount2.sign(transaction).serializeToHex();
        byte[] signedTxnBytes2 = HexUtil.decodeHexString(signTxnHex2);

        Assertions.assertTrue(signedTxnBytes.length > 200);
    }
}
