package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.hdwallet.Wallet;
import com.bloxbean.cardano.hdwallet.model.WalletUtxo;

import java.util.stream.Collectors;

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

        return (context, transaction) -> {
            Transaction outputTxn = transaction;
            for (Account signer : signers) {
                outputTxn = signer.sign(outputTxn);
            }

            return outputTxn;
        };
    }

    /**
     * Function to sign a transaction with a wallet
     *
     * @param wallet wallet to sign the transaction
     * @return <code>TxSigner</code> function which returns a <code>Transaction</code> object with witnesses when invoked
     */
    public static TxSigner signerFrom(Wallet wallet) {
        return (context, transaction) -> {
            var utxos = context.getUtxos()
                    .stream().filter(utxo -> utxo instanceof WalletUtxo)
                    .map(utxo -> (WalletUtxo) utxo)
                    .collect(Collectors.toSet());
            return wallet.sign(transaction, utxos);
        };
    }

    /**
     * Function to sign a transaction with one or more <code>SecretKey</code>
     * @param secretKeys secret keys to sign the transaction
     * @return <code>TxSigner</code> function which returns a <code>Transaction</code> object with witnesses when invoked
     */
    public static TxSigner signerFrom(SecretKey... secretKeys) {

        return (context, transaction) -> {
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

        return (context, transaction) -> {
            Transaction outputTxn = transaction;
            for (Policy policy : policies) {
                for (SecretKey sk : policy.getPolicyKeys()) {
                    outputTxn = TransactionSigner.INSTANCE.sign(outputTxn, sk);
                }
            }

            return outputTxn;
        };
    }

    /**
     * Function to sign a transaction with one or more {@link HdKeyPair}
     * @param hdKeyPairs
     * @return
     */
    public static TxSigner signerFrom(HdKeyPair... hdKeyPairs) {

        return (context, transaction) -> {
            Transaction outputTxn = transaction;
            for (HdKeyPair hdKeyPair : hdKeyPairs) {
                outputTxn = TransactionSigner.INSTANCE.sign(outputTxn, hdKeyPair);
            }

            return outputTxn;
        };
    }

    /**
     * Function to sign a transaction with one or more stake key(s) of <code>Account</code>(s)
     * @param signers account(s) to sign the transaction
     * @return <code>TxSigner</code> function which returns a <code>Transaction</code> object with witnesses when invoked
     */
    public static TxSigner stakeKeySignerFrom(Account... signers) {

        return (context, transaction) -> {
            Transaction outputTxn = transaction;
            for (Account signer : signers) {
                outputTxn = signer.signWithStakeKey(outputTxn);
            }

            return outputTxn;
        };
    }

    /**
     * Function to sign a transaction with one or more DRep key(s) of <code>Account</code>(s)
     * @param signers - account(s) to sign the transaction
     * @return <code>TxSigner</code> function which returns a <code>Transaction</code> object with witnesses when invoked
     */
    public static TxSigner drepKeySignerFrom(Account... signers) {
        return (context, transaction) -> {
            Transaction outputTxn = transaction;
            for (Account signer : signers) {
                outputTxn = signer.signWithDRepKey(outputTxn);
            }

            return outputTxn;
        };
    }

    public static TxSigner stakeKeySignerFrom(Wallet... wallets) {
        return (context, transaction) -> {
            Transaction outputTxn = transaction;
            for (Wallet wallet : wallets)
                outputTxn = wallet.signWithStakeKey(outputTxn);
            return outputTxn;
        };
    }

    //TODO -- Add Integration test
    /**
     * Function to sign a transaction with one or more Committee Cold key(s) of <code>Account</code>(s)
     * @param signers - account(s) to sign the transaction
     * @return <code>TxSigner</code> function which returns a <code>Transaction</code> object with witnesses when invoked
     */
    public static TxSigner committeeColdKeySignerFrom(Account... signers) {
        return (context, transaction) -> {
            Transaction outputTxn = transaction;
            for (Account signer : signers) {
                outputTxn = signer.signWithCommitteeColdKey(outputTxn);
            }

            return outputTxn;
        };
    }

    //TODO -- Add Integration test
    /**
     * Function to sign a transaction with one or more Committee Hot key(s) of <code>Account</code>(s)
     * @param signers - account(s) to sign the transaction
     * @return <code>TxSigner</code> function which returns a <code>Transaction</code> object with witnesses when invoked
     */
    public static TxSigner committeeHotKeySignerFrom(Account... signers) {
        return (context, transaction) -> {
            Transaction outputTxn = transaction;
            for (Account signer : signers) {
                outputTxn = signer.signWithCommitteeHotKey(outputTxn);
            }

            return outputTxn;
        };
    }
}
