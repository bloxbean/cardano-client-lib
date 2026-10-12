package com.bloxbean.cardano.client.transaction;

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
            var signedTx = Transaction.deserialize(signedTxBytes);
            signedTx.setEra(transaction.getEra());
            return signedTx;
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
            var signedTx = Transaction.deserialize(signedTxBytes);
            signedTx.setEra(transaction.getEra());
            return signedTx;
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

    /**
     * Adds a witness to the given transaction by updating the transaction's witness set with
     * the provided verification key and signature.
     * <p>
     * The witness is spliced into the original bytes: it is appended to the vkey witnesses (witness set field 0) and
     * every other byte of the transaction, including the other witness fields, is kept as received. If the transaction
     * already has a vkey witness for {@code vkey}, it is returned unchanged.
     * See {@link TransactionBytes#withVkeyWitness(byte[], byte[])}.
     *
     * @param transactionBytes The transaction bytes containing the current transaction and witness data.
     * @param vkey The verification key to be added to the witness set.
     * @param signature The signature associated with the verification key to be added to the witness set.
     * @return The updated transaction bytes including the new witness.
     * @throws CborRuntimeException If the witness set is not well-formed or field 0 is not an array.
     */
    public byte[] addWitnessToTransaction(TransactionBytes transactionBytes, byte[] vkey, byte[] signature) {
        return transactionBytes.withVkeyWitness(vkey, signature).getTxBytes();
    }

}
