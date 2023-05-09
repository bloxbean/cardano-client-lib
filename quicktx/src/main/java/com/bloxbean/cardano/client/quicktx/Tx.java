package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.List;
import java.util.Set;

public class Tx extends AbstractTx<Tx> {

    private String sender;
    protected boolean senderAdded = false;

    /**
     * Create Tx
     */
    public Tx() {

    }

    /**
     * Create Tx with a sender address. The application needs to provide the signer for this sender address.
     * A Tx object can have only one sender. This method should be called after all outputs are defined.
     *
     * @param sender
     * @return Tx
     */
    public Tx from(String sender) {
        verifySenderNotExists();
        this.sender = sender;
        this.senderAdded = true;
        return this;
    }

    public Tx collectFrom(List<Utxo> utxos) {
        this.inputUtxos = utxos;
        return this;
    }

    public Tx collectFrom(Set<Utxo> utxos) {
        this.inputUtxos = List.copyOf(utxos);
        return this;
    }

    /**
     * Sender address
     *
     * @return String
     */
    String sender() {
        if (sender != null)
            return sender;
        else
            return null;
    }

    @Override
    protected String getChangeAddress() {
        if (changeAddress != null)
            return changeAddress;
        else if (sender != null)
            return sender;
        else
            throw new TxBuildException("No change address. " +
                    "Please define at least one of sender address or sender account or change address");
    }

    @Override
    protected String getFromAddress() {
        if (sender != null)
            return sender;
        else
            throw new TxBuildException("No sender address or sender account defined");
    }

    @Override
    protected void postBalanceTx(Transaction transaction) {

    }

    @Override
    protected void verifyData() {

    }

    @Override
    protected String getFeePayer() {
        if (sender != null)
            return sender;
        else
            return null;
    }

    private void verifySenderNotExists() {
        if (senderAdded)
            throw new TxBuildException("Sender already added. Cannot add additional sender.");
    }
}
