package com.bloxbean.cardano.client.quicktx.blueprint.extender;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ChangeReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.Receiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ScriptReceiver;
import lombok.NonNull;

import java.util.List;

public interface LockUnlockValidatorExtender<T> extends DeployValidatorExtender {

    /**
     * Create a {@link Tx} to lock the funds at the script address. This will create an output with the given amounts and
     * datum at the script address.
     * This Tx can be composed with other Tx to create the final transaction through {@link QuickTxBuilder#compose}}
     *
     * @param fromAddress From address
     * @param amount      Amount to be locked
     * @param datum       Datum in the script output
     * @return Tx
     */
    default Tx lockTx(String fromAddress, Amount amount, Data datum) {
        return lockTx(fromAddress, List.of(amount), datum);
    }

    /**
     * Create a {@link Tx} to lock the funds at the script address. This will create an output with the given amounts and datum at the script address.
     * This Tx can be composed with other Tx to create the final transaction through {@link QuickTxBuilder#compose}}
     *
     * @param fromAddress From address
     * @param amounts     Amounts to be locked
     * @param datum       Datum in the script output
     * @return Tx
     */
    default Tx lockTx(String fromAddress, List<Amount> amounts, Data datum) {
        Tx tx = new Tx()
                .payToContract(getScriptAddress(), amounts, datum.toPlutusData())
                .from(fromAddress);
        return tx;
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to lock the funds at the script address. This will create an output with
     * the given amounts and datum at the script address. TxContext can be used to sign and submit the transaction.
     *
     * @param fromAddress From address
     * @param amount Amount to be locked
     * @param datum Datum in the script output
     * @return TxContext
     */
    default QuickTxBuilder.TxContext lock(String fromAddress, Amount amount, Data datum) {
        return lock(fromAddress, List.of(amount), datum);
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to lock the funds at the script address. This will create an output with
     * the given amounts and datum at the script address. TxContext can be used to sign and submit the transaction.
     *
     * @param fromAddress From address
     * @param amounts     Amounts to be locked
     * @param datum       Datum in the script output
     * @return TxContext
     */
    default QuickTxBuilder.TxContext lock(String fromAddress, List<Amount> amounts, Data datum) {
        requireSuppliersNullCheck();
        Tx tx = lockTx(fromAddress, amounts, datum);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(getUtxoSupplier(), getProtocolParamsSupplier(), getTransactionProcessor());

        var txContext = quickTxBuilder.compose(tx);

        return txContext;
    }


    /**
     * Create a {@link ScriptTx} to unlock the funds at the script address.
     * This will find the script utxo by the inline datum and create a tx to unlock the funds.
     * @param datum Datum in the script output
     * @param redeemer Redeemer
     * @param receivers Receivers details
     * @param changeReceiver Change receiver details if any
     * @return ScriptTx
     */
    default ScriptTx unlockTx(Data datum, Data redeemer, List<Receiver> receivers, ChangeReceiver changeReceiver) {
        try {
            return ScriptUtxoFinders.findFirstByInlineDatum(getUtxoSupplier(), getScriptAddress(), datum.toPlutusData())
                    .map(scriptUtxo -> unlockTx(scriptUtxo, redeemer, receivers, changeReceiver))
                    .orElseThrow(() -> new IllegalStateException("Script Utxo not found"));
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    /**
     * Create a {@link ScriptTx} to unlock the funds at the script address.
     * This ScriptTx can be composed with other Tx/ScriptTx to create the final transaction through {@link QuickTxBuilder#compose}}
     *
     * @param scriptUtxo     Script Utxo
     * @param receivers      Receivers details
     * @param redeemer       Redeemer
     * @param changeReceiver Change receiver details if any
     * @return ScriptTx
     */
    default ScriptTx unlockTx(@NonNull Utxo scriptUtxo, Data redeemer,  List<Receiver> receivers, ChangeReceiver changeReceiver) {
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(scriptUtxo, redeemer.toPlutusData());

        for (Receiver receiver : receivers) {
            if (receiver instanceof ScriptReceiver) {
                var scriptReceiver = (ScriptReceiver) receiver;
                scriptTx.payToContract(receiver.getAddress(), receiver.getAmounts(), scriptReceiver.getDatum());
            } else {
                scriptTx.payToAddress(receiver.getAddress(), receiver.getAmounts());
            }
        }

        if (getReferenceTxInput() != null) {
            scriptTx.readFrom(getReferenceTxInput()._1, getReferenceTxInput()._2);
        } else {
            scriptTx.attachSpendingValidator(getPlutusScript());
        }

        if (changeReceiver != null) {
            if (changeReceiver.isScript()) {
                scriptTx.withChangeAddress(changeReceiver.getAddress(), changeReceiver.getDatum());
            } else {
                scriptTx.withChangeAddress(changeReceiver.getAddress());
            }
        }
        return scriptTx;
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to unlock the funds at the script address. This will find the script utxo
     * by the inline datum and create a tx context to unlock the funds.
     *
     * @param datum          Datum in the script output
     * @param redeemer       Redeemer
     * @param receivers      Receivers details
     * @param changeReceiver Change receiver details if any
     * @return TxContext
     * @throws IllegalStateException if Script Utxo not found
     */
    default QuickTxBuilder.TxContext unlock(Data datum,
                                            Data redeemer,
                                            List<Receiver> receivers,
                                            ChangeReceiver changeReceiver) {
        try {
            return ScriptUtxoFinders.findFirstByInlineDatum(getUtxoSupplier(), getScriptAddress(), datum.toPlutusData())
                    .map(scriptUtxo -> unlock(scriptUtxo, redeemer, receivers, changeReceiver))
                    .orElseThrow(() -> new IllegalStateException("Script Utxo not found"));
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to unlock the funds at the script address.
     * TxContext can be used to sign and submit the transaction.
     *
     * @param utxo           Script Utxo
     * @param redeemer       Redeemer
     * @param receivers      Receivers details
     * @param changeReceiver Change receiver details if any
     * @return TxContext
     */
    default QuickTxBuilder.TxContext unlock(Utxo utxo,
                                            Data redeemer,
                                            List<Receiver> receivers,
                                            ChangeReceiver changeReceiver) {
        requireSuppliersNullCheck();

        ScriptTx scriptTx = unlockTx(utxo, redeemer, receivers, changeReceiver);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(getUtxoSupplier(), getProtocolParamsSupplier(), getTransactionProcessor());

        var txContext = quickTxBuilder.compose(scriptTx);

        if (getTransactionEvaluator() != null) {
            txContext.withTxEvaluator(getTransactionEvaluator());
        }

        if (getReferenceTxInput() != null) {
            txContext.withReferenceScripts(getPlutusScript());
        }

        return txContext;
    }

}
