package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.Script;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Provides helper methods to transform a {@link Transaction} to add minting related data
 */
public class MintCreators {

    /**
     * Function to add mint related data to <code>{@link Transaction}</code>
     * <br>Adds <code>MultiAsset</code> to mint field of <code>TransactionBody</code>.
     * <br>Adds policy script to <code>TransactionWitnessSet</code>
     * <br>Adds policy script to <code>AuxiliaryData</code>
     * @param script     Script instance
     * @param multiAsset MultiAsset to mint
     * @return <code>{@link TxBuilder}</code> function
     */
    public static TxBuilder mintCreator(Script script, MultiAsset multiAsset) {
        return mintCreator(script, multiAsset, false);
    }

    /**
     * Function to add mint related data to <code>{@link Transaction}</code>
     * <br>Adds <code>MultiAsset</code> to mint field of <code>TransactionBody</code>.
     * <br>Adds policy script to <code>TransactionWitnessSet</code>
     * <br>Adds policy script to <code>AuxiliaryData</code>
     * @param script     Script instance
     * @param multiAsset MultiAsset to mint
     * @param inclScriptInAuxData Add policy's native script to <code>AuxiliaryData</code> field
     * @return <code>{@link TxBuilder}</code> function
     */
    public static TxBuilder mintCreator(Script script, MultiAsset multiAsset, boolean inclScriptInAuxData) {
        Objects.requireNonNull(script);
        Objects.requireNonNull(multiAsset);

        return (context, transaction) -> {
            checkTransactionForNullValuesAndInitializeIfRequired(transaction, inclScriptInAuxData);

            if (transaction.getBody().getMint() == null) {
                transaction.getBody().setMint(List.of(multiAsset));
            } else {
                transaction.getBody().getMint().add(multiAsset);
            }

            if (inclScriptInAuxData) {
                if (script instanceof NativeScript) {
                    if (!transaction.getAuxiliaryData().getNativeScripts().contains(script))
                        transaction.getAuxiliaryData().getNativeScripts().add((NativeScript) script);
                } else if (script instanceof PlutusScript) {
                    if (!transaction.getAuxiliaryData().getPlutusScripts().contains(script))
                        transaction.getAuxiliaryData().getPlutusScripts().add((PlutusScript) script);
                }
            }

            if (script instanceof NativeScript) {
                if (!transaction.getWitnessSet().getNativeScripts().contains(script))
                    transaction.getWitnessSet().getNativeScripts().add((NativeScript) script);
            } else if (script instanceof PlutusScript) {
                if (!transaction.getWitnessSet().getPlutusScripts().contains(script))
                    transaction.getWitnessSet().getPlutusScripts().add((PlutusScript) script);
            }
        };
    }

    private static void checkTransactionForNullValuesAndInitializeIfRequired(Transaction transaction, boolean inclScriptToAuxData) {
        if (inclScriptToAuxData) {
            AuxiliaryData auxiliaryData = transaction.getAuxiliaryData();
            if (auxiliaryData == null) {
                transaction.setAuxiliaryData(new AuxiliaryData());
                auxiliaryData = transaction.getAuxiliaryData();
            }

            if (auxiliaryData.getNativeScripts() == null) {
                auxiliaryData.setNativeScripts(new ArrayList<>());
            }
        }

        if (transaction.getWitnessSet() == null)
            transaction.setWitnessSet(new TransactionWitnessSet());

        if (transaction.getWitnessSet().getNativeScripts() == null) {
            transaction.getWitnessSet().setNativeScripts(new ArrayList<>());
        }
    }
}
