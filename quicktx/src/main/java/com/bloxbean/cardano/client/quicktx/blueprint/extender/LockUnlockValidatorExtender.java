package com.bloxbean.cardano.client.quicktx.blueprint.extender;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ChangeReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.Receiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.ScriptReceiver;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.common.TxResult;
import lombok.NonNull;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class LockUnlockValidatorExtender<T> extends AbstractValidatorExtender<T> {

    /**
     * Create a Tx to deploy the script. This will create an output with 1 lovelace and reference script bytes at script address.
     * This Tx can be composed with other Tx to create the final transaction through {@link QuickTxBuilder#compose}}
     *
     * @param feePayerAddr Fee and min lovelace value payer address
     * @return Tx
     */
    public Tx deployTx(String feePayerAddr) {
        try {
            Tx tx = new Tx()
                    .payToAddress(getScriptAddress(), List.of(Amount.lovelace(BigInteger.valueOf(1))), getPlutusScript().scriptRefBytes())
                    .from(feePayerAddr);
            return tx;
        } catch (CborSerializationException e) {
            throw new CborRuntimeException(e.getMessage());
        }
    }

    /**
     * Create a {@link com.bloxbean.cardano.client.quicktx.QuickTxBuilder.TxContext} to deploy the script. This will create an output with 1 lovelace and reference script bytes at script address.
     * TxContext can be used to sign and submit the transaction.
     *
     * @param feePayerAddr
     * @return TxContext
     */
    public QuickTxBuilder.TxContext deployTxContext(String feePayerAddr) {
        requireSuppliersNullCheck();

        Tx tx = deployTx(feePayerAddr);

        QuickTxBuilder quickTxBuilder =
                new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

        return quickTxBuilder.compose(tx);
    }

    /**
     * Deploy the script. This will create an output with 1 lovelace and reference script bytes at script address.
     *
     * @param feePayerAddr Fee and min lovelace value payer address
     * @param txSigner     TxSigner
     * @param outputWriter Output writer
     * @return TxResult
     */
    public TxResult deploy(String feePayerAddr, TxSigner txSigner,
                           Consumer<String> outputWriter) {
        var txContext = deployTxContext(feePayerAddr);

        var result = txContext
                .withSigner(txSigner)
                .completeAndWait(outputWriter);

        if (result.isSuccessful()) {
            return TxResult.success(result.getValue());
        } else {
            return TxResult.error(result.getResponse());
        }
    }

    /**
     * Create a {@link Tx} to lock the funds at the script address. This will create an output with the given amounts and datum at the script address.
     * This Tx can be composed with other Tx to create the final transaction through {@link QuickTxBuilder#compose}}
     *
     * @param fromAddress From address
     * @param amount      Amount to be locked
     * @param datum       Datum in the script output
     * @return Tx
     */
    public Tx lockTx(String fromAddress, Amount amount, Data datum) {
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
    public Tx lockTx(String fromAddress, List<Amount> amounts, Data datum) {
        Tx tx = new Tx()
                .payToContract(getScriptAddress(), amounts, datum.toPlutusData())
                .from(fromAddress);
        return tx;
    }

    /**
     * Create a {@link QuickTxBuilder.TxContext} to lock the funds at the script address. This will create an output with the given amounts and datum at the script address.
     * TxContext can be used to sign and submit the transaction.
     *
     * @param fromAddress From address
     * @param amounts     Amounts to be locked
     * @param datum       Datum in the script output
     * @return TxContext
     */
    public QuickTxBuilder.TxContext lockTxContext(String fromAddress, List<Amount> amounts, Data datum) {
        requireSuppliersNullCheck();
        Tx tx = lockTx(fromAddress, amounts, datum);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

        var txContext = quickTxBuilder.compose(tx);

        return txContext;
    }

    /**
     * Lock the funds at the script address. This will create an output with the given amounts and datum at the script address.
     * This method will sign and submit the transaction.
     *
     * @param fromAddress  From address
     * @param amounts      Amounts to be locked
     * @param datum        Datum in the script output
     * @param txSigner     TxSigner
     * @param outputWriter Writer for output messages
     * @return TxResult
     */
    public TxResult lock(String fromAddress, List<Amount> amounts, Data datum, TxSigner txSigner, Consumer<String> outputWriter) {
        var txContext = lockTxContext(fromAddress, amounts, datum);

        var result = txContext
                .withSigner(txSigner)
                .completeAndWait(outputWriter);

        if (result.isSuccessful()) {
            return TxResult.success(result.getValue());
        } else {
            return TxResult.error(result.getResponse());
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
    public ScriptTx unlockTx(@NonNull Utxo scriptUtxo, List<Receiver> receivers, Data redeemer, ChangeReceiver changeReceiver) {
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

        if (referenceTxInput != null) {
            scriptTx.readFrom(referenceTxInput._1, referenceTxInput._2);
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
     * Create a {@link QuickTxBuilder.TxContext} to unlock the funds at the script address.
     * TxContext can be used to sign and submit the transaction.
     *
     * @param receivers      Receivers details
     * @param feePayer       Fee payer address
     * @param datum          Datum in the script output
     * @param redeemer       Redeemer
     * @param changeReceiver Change receiver details if any
     * @return TxContext
     */
    public QuickTxBuilder.TxContext unlockTxContext(List<Receiver> receivers, String feePayer, Data datum, Data redeemer, ChangeReceiver changeReceiver) {
        requireSuppliersNullCheck();

        try {
            Optional<Utxo> scriptUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, getScriptAddress(), datum.toPlutusData());
            if (!scriptUtxo.isPresent()) {
                throw new IllegalStateException("Script Utxo not found");
            }

            ScriptTx scriptTx = unlockTx(scriptUtxo.get(), receivers, redeemer, changeReceiver);

            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);

            var txContext = quickTxBuilder.compose(scriptTx);

            if (feePayer != null)
                txContext.feePayer(feePayer);

            if (txEvaluator != null) {
                txContext.withTxEvaluator(txEvaluator);
            }

            if (referenceTxInput != null) {
                txContext.withReferenceScripts(getPlutusScript());
            }

            return txContext;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    /**
     * Unlock the funds at the script address.
     * This method will sign and submit the transaction.
     *
     * @param receivers       Receivers details
     * @param feePayer        Fee payer address
     * @param datum           Datum in the script output
     * @param redeemer        Redeemer
     * @param txSigner        TxSigner
     * @param changeReceiver  Change receiver details if any
     * @param requiredSigners Required signers
     * @return
     */
    public TxResult unlock(List<Receiver> receivers, String feePayer, Data datum, Data redeemer, TxSigner txSigner,
                           ChangeReceiver changeReceiver, Consumer<String> outputWriter, Address... requiredSigners) {

        var txContext = unlockTxContext(receivers, feePayer, datum, redeemer, changeReceiver);

        if (requiredSigners != null && requiredSigners.length > 0) {
            txContext.withRequiredSigners(requiredSigners);
        }

        txContext.withTxInspector(transaction -> {
            System.out.println("Transaction: " + transaction);
        });

        var result = txContext
                .withSigner(txSigner)
                .completeAndWait(outputWriter);

        if (result.isSuccessful()) {
            return TxResult.success(result.getValue());
        } else {
            return TxResult.error(result.getResponse());
        }
    }

    private void requireSuppliersNullCheck() {
        Objects.requireNonNull(utxoSupplier, "UtxoSupplier is required");
        Objects.requireNonNull(protocolParamsSupplier, "ProtocolParamsSupplier is required");
        Objects.requireNonNull(transactionProcessor, "TransactionProcessor is required");
    }

}
