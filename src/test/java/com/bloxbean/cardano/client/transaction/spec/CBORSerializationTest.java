package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.jna.CardanoJNAUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CBORSerializationTest {

    @Test
    public void testSerializeTransactionWithMint() throws CborException, AddressExcepion {
        TransactionBody txnBody = new TransactionBody();

        long fee = 367965;
        int ttl = 26194586;

        TransactionInput txnInput = new TransactionInput();
        txnInput.setTransactionId("73198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002"); //989264070
        txnInput.setIndex(1);

        txnBody.setInputs(Arrays.asList(txnInput));

        //Output 1
        TransactionOutput txnOutput1 =  new TransactionOutput();
        txnOutput1.setAddress("addr_test1qqy3df0763vfmygxjxu94h0kprwwaexe6cx5exjd92f9qfkry2djz2a8a7ry8nv00cudvfunxmtp5sxj9zcrdaq0amtqmflh6v");
        txnOutput1.setValue(new Value(new BigInteger(String.valueOf(40000)), null));

        TransactionOutput changeOutput =  new TransactionOutput();
        changeOutput.setAddress("addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y");

        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96");
        multiAsset.setAssets(Arrays.asList(new Asset("736174636f696e", BigInteger.valueOf(4000))));

        MultiAsset multiAsset1 = new MultiAsset();
        multiAsset1.setPolicyId("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");
        multiAsset1.setAssets(Arrays.asList(new Asset(null, BigInteger.valueOf(9000))));
        changeOutput.setValue(new Value(new BigInteger(String.valueOf(340000)), Arrays.asList(multiAsset, multiAsset1)));


        List<TransactionOutput> outputs = new ArrayList<>();
        outputs.add(txnOutput1);
        outputs.add(changeOutput);
        txnBody.setOutputs(outputs);

        txnBody.setFee(new BigInteger(String.valueOf(fee)));
        txnBody.setTtl(ttl);
        txnBody.getMint().add(multiAsset);
        txnBody.getMint().add(multiAsset1);

        Transaction transaction = new Transaction();
        transaction.setBody(txnBody);
        String hexStr = transaction.serializeToHex();
        System.out.println("********************");
        System.out.println(hexStr);

        boolean res = CardanoJNAUtil.validateTransactionCBOR(hexStr);
        System.out.println(res);

        assertTrue(res);
    }
}
