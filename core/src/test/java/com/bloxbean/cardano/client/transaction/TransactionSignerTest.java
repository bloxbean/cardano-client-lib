package com.bloxbean.cardano.client.transaction;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionSignerTest {

    @Test
    void sign_withSecretKey() throws CborSerializationException, CborDeserializationException, AddressExcepion {
        String txnHex = "83a4008282582073198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002018258208e03a93578dc0acd523a4dd861793068a06a68b8a6c7358d0c965d2864067b68000184825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed61a004c4b40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f8821a3aa51029a2581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a147736174636f696e190fa0581c6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7a14019232882583900c93b6cac143fe60f8914f44a899f5329433ccec3d53721ef350a0fd8cb873402c73ad8f239f76fb559bb4e3bcff22b310b01eadd3ce205e71a007a1200825839001c1ffaf141ebbb8e3a7072bb15f50f938b994c82de2d175f358fc942441f00edfe1b8d6a84f0d19c25a9c8829442160c0b5c758094c423441a3b1b1aa3021a000b3aba031a018fb29aa0f6";
        String sk = "ede3104b2f4ff32daa3b620a9a272cd962cf504da44cf1cf0280aff43b65f807";

        SecretKey secretKey = SecretKey.create(HexUtil.decodeHexString(sk));
        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(txnHex));

        transaction = TransactionSigner.INSTANCE.sign(transaction, secretKey);

        VkeyWitness vkeyWitness = transaction.getWitnessSet().getVkeyWitnesses().get(0);
        String vkeyHex = HexUtil.encodeHexString(vkeyWitness.getVkey());
        String signatureHex = HexUtil.encodeHexString(vkeyWitness.getSignature());

        System.out.println("VKey : " + vkeyHex);
        System.out.println("Key Hash : " + KeyGenUtil.getKeyHash(VerificationKey.create(vkeyWitness.getVkey())));
        System.out.println("Signature: " + signatureHex);

        assertThat(vkeyHex).isEqualTo("60209269377f220cdecdc6d5ad42d9b04e58ce74b349efb396ee46adaeb956f3");
        assertThat(signatureHex).isEqualTo("9b14acd2799e8b97e4be5b02ee344d9620af9d5c24cdf6b50aa4e706f996e9a11747128f62219bc5a5e3b8a3c4fa6881e9d63144ec8448a413edefe018d6a706");
    }

    @Test
    public void sign_checkSerialized() throws CborDeserializationException, CborSerializationException, CborException {
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

        CBORMetadata metadata = new CBORMetadata();

        metadata.putNegative(BigInteger.valueOf(1022222222), BigInteger.valueOf(-1481433243434L));

        AuxiliaryData data = AuxiliaryData.builder()
                .metadata(metadata)
                .build();
        transaction.setAuxiliaryData(data);

        Account account = new Account();

        Transaction signedTxn1 = account.sign(transaction);
        //Sign again
        Transaction signTxn2 = account.sign(transaction);

        assertThat(signedTxn1.serializeToHex()).isEqualTo(signTxn2.serializeToHex());

        //Clear witness and check with original txn
        signTxn2.setWitnessSet(new TransactionWitnessSet());
        assertThat(signTxn2.serializeToHex()).isEqualTo(transaction.serializeToHex());
    }
}
