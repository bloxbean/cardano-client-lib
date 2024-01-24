package com.bloxbean.cardano.client.transaction;

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
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.util.TransactionBytes;
import lombok.NonNull;

public enum TransactionSigner {
    INSTANCE();

    TransactionSigner() {

    }

    /**
     * Sign transaction with a HD key pair
     *
     * @param transaction - Transaction to sign
     * @param hdKeyPair   - HD key pair
     * @return Signed transaction
     */
    public Transaction sign(@NonNull Transaction transaction, @NonNull HdKeyPair hdKeyPair) {
        try {
            byte[] signedTxBytes = sign(transaction.serialize(), hdKeyPair);
            return Transaction.deserialize(signedTxBytes);
        } catch (CborSerializationException | CborDeserializationException e) {
            throw new CborRuntimeException(e);
        }
    }

    /**
     * Sign transaction with a secret key
     *
     * @param transaction - Transaction to sign
     * @param secretKey   - Secret key
     * @return Signed transaction
     */
    public Transaction sign(Transaction transaction, SecretKey secretKey) {
        try {
            byte[] signedTxBytes = sign(transaction.serialize(), secretKey);
            return Transaction.deserialize(signedTxBytes);
        } catch (CborSerializationException | CborDeserializationException e) {
            throw new CborRuntimeException(e);
        }
    }

    /**
     * Sign transaction bytes with a HD key pair. Use this method to sign transaction bytes from another transaction builder.
     *
     * @param txBytes   - Transaction bytes
     * @param hdKeyPair - HD key pair
     * @return Signed transaction bytes
     */
    public byte[] sign(@NonNull byte[] txBytes, @NonNull HdKeyPair hdKeyPair) {
        TransactionBytes transactionBytes = new TransactionBytes(txBytes);
        byte[] txnBodyHash = Blake2bUtil.blake2bHash256(transactionBytes.getTxBodyBytes());

        SigningProvider signingProvider = CryptoConfiguration.INSTANCE.getSigningProvider();
        byte[] signature = signingProvider.signExtended(txnBodyHash, hdKeyPair.getPrivateKey().getKeyData(), hdKeyPair.getPublicKey().getKeyData());

        byte[] signedTransaction = addWitnessToTransaction(transactionBytes, hdKeyPair.getPublicKey().getKeyData(), signature);

        return signedTransaction;
    }

    /**
     * Sign transaction bytes with a secret key. Use this method to sign transaction bytes from another
     * transaction builder.
     *
     * @param transactionBytes
     * @param secretKey
     * @return Signed transaction bytes
     */
    public byte[] sign(@NonNull byte[] transactionBytes, @NonNull SecretKey secretKey) {
        TransactionBytes txBytes = new TransactionBytes(transactionBytes);
        byte[] txnBodyHash = Blake2bUtil.blake2bHash256(txBytes.getTxBodyBytes());

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

        byte[] signedTransaction = addWitnessToTransaction(txBytes, verificationKey.getBytes(), signature);
        return signedTransaction;
    }

    private byte[] addWitnessToTransaction(TransactionBytes transactionBytes, byte[] vkey, byte[] signature) {
        try {
            DataItem witnessSetDI = CborSerializationUtil.deserialize(transactionBytes.getTxWitnessBytes());
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

            byte[] txWitnessBytes = CborSerializationUtil.serialize(witnessSetMap, false);

            return transactionBytes.withNewWitnessSetBytes(txWitnessBytes)
                    .getTxBytes();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }
    }

}
