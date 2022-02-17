package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

/**
 * Provides helper methods to get TxSigner function to sign a <code>{@link Transaction}</code> object
 */
public class SignerProviders {

    /**
     * Function to sign a transaction using one or more <code>Account</code>
     * @param signers account(s) to sign the transaction
     * @return <code>TxSigner</code> function which returns a <code>Transaction</code> object with witnesses when invoked
     */
    public static TxSigner signerFrom(Account... signers) {

        return transaction -> {
            Transaction outputTxn = transaction;
            for (Account signer : signers) {
                outputTxn = signer.sign(outputTxn);
            }

            return outputTxn;
        };
    }

    /**
     * Function to sign a transaction with one or more <code>SecretKey</code>
     * @param secretKeys secret keys to sign the transaction
     * @return <code>TxSigner</code> function which returns a <code>Transaction</code> object with witnesses when invoked
     */
    public static TxSigner signerFrom(SecretKey... secretKeys) {

        return transaction -> {
            Transaction outputTxn = transaction;
            for (SecretKey sk : secretKeys) {
                outputTxn = TransactionSigner.INSTANCE.sign(outputTxn, sk);
            }

            return outputTxn;
        };
    }

    /**
     * Function to sign a transaction with one or more <code>Policy</code>
     * @param policies one or more policy
     * @return <code>TxSigner</code> function which returns a <code>Transaction</code> object with witnesses when invoked
     */
    public static TxSigner signerFrom(Policy... policies) {

        return transaction -> {
            Transaction outputTxn = transaction;
            for (Policy policy : policies) {
                for (SecretKey sk : policy.getPolicyKeys()) {
                    outputTxn = TransactionSigner.INSTANCE.sign(outputTxn, sk);
                }
            }

            return outputTxn;
        };
    }
}
