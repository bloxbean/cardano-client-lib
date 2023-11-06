package com.bloxbean.cardano.client.transaction;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyGenerator;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.VkeyWitness;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.transaction.util.TransactionUtil.createCopy;

public enum TransactionSigner {
    INSTANCE();

    TransactionSigner() {

    }

    public Transaction sign(Transaction transaction, HdKeyPair hdKeyPair) {
        Transaction cloneTxn = createCopy(transaction);
        TransactionBody transactionBody = cloneTxn.getBody();

        byte[] txnBody = null;
        try {
            txnBody = CborSerializationUtil.serialize(transactionBody.serialize());
        } catch (CborException e) {
            throw new CborRuntimeException("Error in Cbor serialization", e);
        } catch (AddressExcepion e) {
            throw new CborRuntimeException("Error in Cbor serialization", e);
        } catch (CborSerializationException e) {
            throw new CborRuntimeException("Error in Cbor serialization", e);
        }

        byte[] txnBodyHash = Blake2bUtil.blake2bHash256(txnBody);

        SigningProvider signingProvider = CryptoConfiguration.INSTANCE.getSigningProvider();
        byte[] signature = signingProvider.signExtended(txnBodyHash, hdKeyPair.getPrivateKey().getKeyData(), hdKeyPair.getPublicKey().getKeyData());

        VkeyWitness vkeyWitness = VkeyWitness.builder()
                .vkey(hdKeyPair.getPublicKey().getKeyData())
                .signature(signature)
                .build();

        if (cloneTxn.getWitnessSet() == null)
            cloneTxn.setWitnessSet(new TransactionWitnessSet());

        if (cloneTxn.getWitnessSet().getVkeyWitnesses() == null)
            cloneTxn.getWitnessSet().setVkeyWitnesses(new ArrayList<>());

        cloneTxn.getWitnessSet().getVkeyWitnesses().add(vkeyWitness);

        return cloneTxn;
    }

    public Transaction sign(Transaction transaction, SecretKey secretKey) {
        Transaction cloneTxn = createCopy(transaction);
        TransactionBody transactionBody = cloneTxn.getBody();

        byte[] txnBody = null;
        try {
            txnBody = CborSerializationUtil.serialize(transactionBody.serialize());
        } catch (CborException e) {
            throw new CborRuntimeException("Error in Cbor serialization", e);
        } catch (AddressExcepion e) {
            throw new CborRuntimeException("Error in Cbor serialization", e);
        } catch (CborSerializationException e) {
            throw new CborRuntimeException("Error in Cbor serialization", e);
        }

        byte[] txnBodyHash = Blake2bUtil.blake2bHash256(txnBody);

        SigningProvider signingProvider = CryptoConfiguration.INSTANCE.getSigningProvider();
        VerificationKey verificationKey = null;
        byte[] signature = null;

        if (secretKey.getBytes().length == 64) { //extended pvt key (most prob for regular account)
            //check for public key
            byte[] vBytes = HdKeyGenerator.getPublicKey(secretKey.getBytes());
            signature = signingProvider.signExtended(txnBodyHash, secretKey.getBytes(), vBytes);

            try {
                verificationKey = VerificationKey.create(vBytes);
            } catch (CborSerializationException e) {
                throw new CborRuntimeException("Unable to get verification key from secret key", e);
            }
        } else {
            signature = signingProvider.sign(txnBodyHash, secretKey.getBytes());
            try {
                verificationKey = KeyGenUtil.getPublicKeyFromPrivateKey(secretKey);
            } catch (CborSerializationException e) {
                throw new CborRuntimeException("Unable to get verification key from SecretKey", e);
            }
        }

        VkeyWitness vkeyWitness = VkeyWitness.builder()
                .vkey(verificationKey.getBytes())
                .signature(signature)
                .build();

        if (cloneTxn.getWitnessSet() == null)
            cloneTxn.setWitnessSet(new TransactionWitnessSet());

        if (cloneTxn.getWitnessSet().getVkeyWitnesses() == null)
            cloneTxn.getWitnessSet().setVkeyWitnesses(new ArrayList<>());

        cloneTxn.getWitnessSet().getVkeyWitnesses().add(vkeyWitness);

        return cloneTxn;
    }

    /**
     * Sign transaction bytes with a secret key. Use this method to sign transaction bytes from another
     * transaction builder.
     *
     * @param transactionBytes
     * @param secretKey
     * @return Signed transaction bytes
     */
    public byte[] sign(byte[] transactionBytes, SecretKey secretKey) {
        byte[] txnBody = extractTransactionBody(transactionBytes);
        byte[] txnBodyHash = Blake2bUtil.blake2bHash256(txnBody);

        SigningProvider signingProvider = CryptoConfiguration.INSTANCE.getSigningProvider();
        VerificationKey verificationKey;
        byte[] signature;

        if (secretKey.getBytes().length == 64) { //extended pvt key (most prob for regular account)
            //check for public key
            byte[] vBytes = HdKeyGenerator.getPublicKey(secretKey.getBytes());
            signature = signingProvider.signExtended(txnBodyHash, secretKey.getBytes(), vBytes);

            try {
                verificationKey = VerificationKey.create(vBytes);
            } catch (CborSerializationException e) {
                throw new CborRuntimeException("Unable to get verification key from secret key", e);
            }
        } else {
            signature = signingProvider.sign(txnBodyHash, secretKey.getBytes());
            try {
                verificationKey = KeyGenUtil.getPublicKeyFromPrivateKey(secretKey);
            } catch (CborSerializationException e) {
                throw new CborRuntimeException("Unable to get verification key from SecretKey", e);
            }
        }

        byte[] signedTransaction = addWitnessToTransaction(transactionBytes, verificationKey.getBytes(), signature);
        return signedTransaction;
    }

    /**
     * Extract transaction body from transaction bytes
     *
     * @param txBytes
     * @return transaction body bytes
     */
    private byte[] extractTransactionBody(byte[] txBytes) {
        ByteArrayInputStream bais = new ByteArrayInputStream(txBytes);
        CborDecoder decoder = new CborDecoder(bais);
        try {
            Array txArray = (Array) decoder.decodeNext();
            DataItem txBodyDI = txArray.getDataItems().get(0);
            return CborSerializationUtil.serialize(txBodyDI, false);
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        } finally {
            try {
                bais.close();
            } catch (Exception e) {
            }
        }
    }

    private byte[] addWitnessToTransaction(byte[] txBytes, byte[] vkey, byte[] signature) {
        Array txDIArray = (Array) CborSerializationUtil.deserialize(txBytes);
        List<DataItem> txDIList = txDIArray.getDataItems();

        try {
            DataItem witnessSetDI = txDIList.get(1);
            Map witnessSetMap = (Map) witnessSetDI;

            DataItem vkWitnessArrayDI = witnessSetMap.get(new UnsignedInteger(0));
            Array vkWitnessArray;
            if (vkWitnessArrayDI != null) {
                vkWitnessArray = (Array) vkWitnessArrayDI;
            } else {
                vkWitnessArray = new Array();
                witnessSetMap.put(new UnsignedInteger(0), vkWitnessArray);
            }

            //Add witness
            Array vkeyWitness = new Array();
            vkeyWitness.add(new ByteString(vkey));
            vkeyWitness.add(new ByteString(signature));

            vkWitnessArray.add(vkeyWitness);

            return CborSerializationUtil.serialize(txDIArray, false);
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }
    }

}
