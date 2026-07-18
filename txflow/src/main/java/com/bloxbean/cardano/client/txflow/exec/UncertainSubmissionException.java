package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;

import java.util.Arrays;

/**
 * Captures a signed transaction that may have been accepted even though the
 * submission call returned no conclusive answer.
 *
 * <p>The constructor snapshots both the transaction and its serialized bytes.
 * Reconciliation can therefore query by the stable hash and, when permitted,
 * resubmit the identical payload instead of rebuilding a transaction that might
 * double-spend the same inputs. Accessors return defensive copies.</p>
 */
final class UncertainSubmissionException extends FlowExecutionException {
    private final String transactionHash;
    private final byte[] signedTransaction;
    private final Transaction transaction;

    UncertainSubmissionException(Transaction transaction, Throwable cause) {
        super("Transaction submission outcome is uncertain", cause);
        this.transaction = TransactionUtil.createCopy(transaction);
        this.signedTransaction = serialize(transaction);
        this.transactionHash = TransactionUtil.getTxHash(signedTransaction);
    }

    String getTransactionHash() {
        return transactionHash;
    }

    byte[] getSignedTransaction() {
        return Arrays.copyOf(signedTransaction, signedTransaction.length);
    }

    Transaction getTransaction() {
        return TransactionUtil.createCopy(transaction);
    }

    private static byte[] serialize(Transaction transaction) {
        try {
            return transaction.serialize();
        } catch (Exception e) {
            throw new FlowExecutionException("Unable to preserve signed transaction for reconciliation", e);
        }
    }
}
