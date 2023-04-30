package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.api.util.UtxoUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BalanceTxBuilders {

    /**
     * Function to balance an unbalanced transaction.
     * This is a wrapper function which invokes the following functions to create a balanced transaction
     * <ul>
     *  <li>{@link FeeCalculators#feeCalculator(String, int)} </li>
     *  <li>{@link ChangeOutputAdjustments#adjustChangeOutput(String, int)} </li>
     *  <li>{@link CollateralBuilders#balanceCollateralOutputs()} (For transaction with collateral return)</li>
     * </ul>
     * @param changeAddress Change output address
     * @param nSigners No of signers. This is required for accurate fee calculation.
     * @return TxBuilder
     */
    public static TxBuilder balanceTx(String changeAddress, int nSigners) {
        return (context, txn) -> {
            FeeCalculators.feeCalculator(changeAddress, nSigners).apply(context, txn);

            //Incase change output goes below min ada after fee deduction
            ChangeOutputAdjustments.adjustChangeOutput(changeAddress, nSigners).apply(context, txn);

            //If collateral return found, balance collateral outputs
            if (txn.getBody().getCollateralReturn() != null)
                CollateralBuilders.balanceCollateralOutputs().apply(context,txn);
        };
    }

    /**
     * Function to balance an unbalanced transaction using Automatic Utxo Discovery.
     * This is a wrapper function which invokes the following functions to create a balanced transaction
     * <ul>
     *  <li>{@link FeeCalculators#feeCalculator(String, int)} </li>
     *  <li>{@link CollateralBuilders#balanceCollateralOutputs()} (For transaction with collateral return)</li>
     *  <li>{@link ChangeOutputAdjustments#adjustChangeOutput(String, int)} </li>
     * </ul>
     * @param changeAddress Change output address
     * @return TxBuilder
     */
    public static TxBuilder balanceTx(String changeAddress) {
        return balanceTxWithAdditionalSigners(changeAddress, 0);
    }

    /**
     * Function to balance an unbalanced transaction using Automatic Utxo Discovery with Additional Signers.
     * This is a wrapper function which invokes the following functions to create a balanced transaction
     * <ul>
     *  <li>{@link FeeCalculators#feeCalculator(String, int)} </li>
     *  <li>{@link ChangeOutputAdjustments#adjustChangeOutput(String, int)} </li>
     *  <li>{@link CollateralBuilders#balanceCollateralOutputs()} (For transaction with collateral return)</li>
     * </ul>
     * @param changeAddress Change output address
     * @param additionalSigners No of Additional signers. This is required for accurate fee calculation.
     * @return TxBuilder
     */
    public static TxBuilder balanceTxWithAdditionalSigners(String changeAddress, int additionalSigners) {
        return (context, txn) -> {
            FeeCalculators.feeCalculator(changeAddress, UtxoUtil.getNoOfRequiredSigners(context.getUtxos()) + additionalSigners).apply(context, txn);

            //Incase change output goes below min ada after fee deduction
            ChangeOutputAdjustments.adjustChangeOutput(changeAddress, UtxoUtil.getNoOfRequiredSigners(context.getUtxos()) + additionalSigners).apply(context, txn);

            //If collateral return found, balance collateral outputs
            if (txn.getBody().getCollateralReturn() != null)
                CollateralBuilders.balanceCollateralOutputs().apply(context,txn);
        };
    }
}
