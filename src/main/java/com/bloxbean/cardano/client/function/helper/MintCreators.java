package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.transaction.spec.*;

import java.util.List;

//TODO -- Unit tests pending

/**
 * Provides helper method to transform a {@link Transaction} to add minting related data
 */
public class MintCreators {

    /**
     * Function to add mint related data to <code>{@link Transaction}</code>
     *
     * @param policy     Policy instance
     * @param multiAsset MultiAsset to mint
     * @return <code>{@link TxBuilder}</code> function
     */
    public static TxBuilder mintCreator(Policy policy, MultiAsset multiAsset) {

        return (context, transaction) -> {
            if (transaction.getBody().getMint() == null) {
                transaction.getBody().setMint(List.of(multiAsset));
            } else {
                transaction.getBody().getMint().add(multiAsset);
            }

            //Set native script
            AuxiliaryData auxiliaryData = transaction.getAuxiliaryData();
            if (auxiliaryData == null) {
                auxiliaryData = new AuxiliaryData();
                transaction.setAuxiliaryData(auxiliaryData);
            }

            if (auxiliaryData.getNativeScripts() == null) {
                auxiliaryData.setNativeScripts(List.of(policy.getPolicyScript()));
            } else {
                auxiliaryData.getNativeScripts().add(policy.getPolicyScript());
            }

            if (transaction.getWitnessSet() == null)
                transaction.setWitnessSet(new TransactionWitnessSet());

            if (transaction.getWitnessSet().getNativeScripts() == null)
                transaction.getWitnessSet().setNativeScripts(List.of(policy.getPolicyScript()));
            else
                transaction.getWitnessSet().getNativeScripts().add(policy.getPolicyScript());

        };
    }
}
