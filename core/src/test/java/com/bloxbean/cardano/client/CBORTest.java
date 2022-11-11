package com.bloxbean.cardano.client;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CBORTest {
    public static void main(String[] args) throws Exception {
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
        TransactionOutput txnOutput1 =  new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(amount1)), null));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(new Asset("0x736174636f696e", BigInteger.valueOf(4000))));

        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");
        multiAsset1.setAssets(Arrays.asList(new Asset(null, BigInteger.valueOf(9000))));
        changeOutput.setValue(new Value(new BigInteger(String.valueOf(changeAmount1)), Arrays.asList(multiAsset, multiAsset1)));

        //Output2
        long amount2 = 8000000;
        long changeAmount2 = balance2 - amount2 - fee;
        TransactionOutput txnOutput2 =  new TransactionOutput();
        txnOutput2.setAddress("addr_test1qrynkm9vzsl7vrufzn6y4zvl2v55x0xwc02nwg00x59qlkxtsu6q93e6mrernam0k4vmkn3melezkvgtq84d608zqhnsn48axp");

        Value value2 = Value.builder()
                .coin(new BigInteger(String.valueOf(amount2)))
                .build();

        txnOutput2.setValue(value2);

        TransactionOutput changeOutput2 =  new TransactionOutput();
        changeOutput2.setAddress("addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82");

        Value value3 = Value.builder()
                .coin(new BigInteger(String.valueOf(changeAmount2)))
                .build();
        changeOutput2.setValue(value3);

        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput1);
        outputs.add(changeOutput);
        outputs.add(txnOutput2);
        outputs.add(changeOutput2);

        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger(String.valueOf(fee * 2)));
        txnBody.setTtl(ttl);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);
        TransactionWitnessSet transactionWitnessSet = new TransactionWitnessSet();
        transactionWitnessSet.getNativeScripts().add(new ScriptPubkey("ad7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884"));
        transaction.setWitnessSet(transactionWitnessSet);

        String hexStr = transaction.serializeToHex();
        System.out.println(hexStr);

        System.out.println("\n*******************************");

        String mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        Account signingAccount = new Account(Networks.testnet(), mnemonic);
        System.out.println(signingAccount.getBech32PrivateKey());

        Transaction signedTxn = signingAccount.sign(transaction);
        String signTxnHex = signedTxn.serializeToHex();
        byte[] signedTxnBytes = HexUtil.decodeHexString(signTxnHex);

        //Sign with account 2
        String mnemonic2 = "mixture peasant wood unhappy usage hero great elder emotion picnic talent fantasy program clean patch wheel drip disorder bullet cushion bulk infant balance address";
        Account signingAccount2 = new Account(Networks.testnet(), mnemonic2);
        System.out.println(signingAccount2.getBech32PrivateKey());

        String signTxnHex2 = signingAccount2.sign(signedTxn).serializeToHex();
        byte[] signedTxnBytes2 = HexUtil.decodeHexString(signTxnHex2);

        File outputFile = new File("/Users/satya/tx1.raw");
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(signedTxnBytes2);
        }
    }

}
