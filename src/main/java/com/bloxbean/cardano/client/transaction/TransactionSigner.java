package com.bloxbean.cardano.client.transaction;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.VkeyWitness;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;

import java.util.ArrayList;

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

        byte[] txnBodyHash = KeyGenUtil.blake2bHash256(txnBody);

        SigningProvider signingProvider = Configuration.INSTANCE.getSigningProvider();
        byte[] signature = signingProvider.sign(txnBodyHash, hdKeyPair.getPrivateKey().getKeyData());

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

        byte[] txnBodyHash = KeyGenUtil.blake2bHash256(txnBody);

        SigningProvider signingProvider = Configuration.INSTANCE.getSigningProvider();
        byte[] signature = signingProvider.sign(txnBodyHash, secretKey.getBytes());

        VerificationKey verificationKey = null;
        try {
            verificationKey = KeyGenUtil.getPublicKeyFromPrivateKey(secretKey);
        } catch (CborSerializationException e) {
            throw new CborRuntimeException("Unable to get verification key from SecretKey", e);
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

}
