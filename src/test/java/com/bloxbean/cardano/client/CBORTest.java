package com.bloxbean.cardano.client;

import com.bloxbean.cardano.client.common.Bech32;
import com.bloxbean.cardano.client.transaction.model.Transaction;
import com.bloxbean.cardano.client.transaction.model.TransactionBody;
import com.bloxbean.cardano.client.transaction.model.TransactionInput;
import com.bloxbean.cardano.client.transaction.model.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class CBORTest {

    public static void main(String[] args) throws Exception {
        TransactionBody txnBody = new TransactionBody();

        TransactionInput txnInput = new TransactionInput();

        txnInput.setTransactionId(HexUtil.decodeHexString("4123d70f66414cc921f6ffc29a899aafc7137a99a0fd453d6b200863ef5702d6"));
        txnInput.setIndex(5);

        List<TransactionInput> inputList = new ArrayList<>();
        inputList.add(txnInput);
        txnBody.setInputs(inputList);

        TransactionOutput txnOutput =  new TransactionOutput();
        txnOutput.setAddress(
                Bech32.decode("addr_test1vp3jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsg99fsju").data);
        txnOutput.setValue(new BigInteger("2000"));

        List<TransactionOutput> outputs = new ArrayList<>();
      //  outputs.add(txnOutput);
        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger("7800"));
        txnBody.setTtl(60);
        txnBody.setMetadataHash("testtesttesttesttesttesttesttest".getBytes(StandardCharsets.UTF_8));

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);
        byte[] bytes = transaction.serialize();

        String b64Str = Base64.getEncoder().encodeToString(bytes);
        System.out.println(b64Str);

    }

}
