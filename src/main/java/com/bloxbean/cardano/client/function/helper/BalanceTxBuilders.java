package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.TxBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BalanceTxBuilders {

    /**
     * Function to balance an unbalanced transaction.
     * This is a wrapper function which invokes the following functions to create a balanced transaction
     * <ul>
     *  <li>{@link FeeCalculators#feeCalculator(String, int)} </li>
     *  <li>{@link CollateralBuilders#balanceCollateralOutputs()} (For transaction with collateral return)</li>
     *  <li>{@link ChangeOutputAdjustments#adjustChangeOutput(String, int)} </li>
     * </ul>
     * @param changeAddress Change output address
     * @param nSigners No of signers. This is required for accurate fee calculation.
     * @return TxBuilder
     */
    public static TxBuilder balanceTx(String changeAddress, int nSigners) {
        return (context, txn) -> {
            FeeCalculators.feeCalculator(changeAddress, nSigners).apply(context, txn);

            //If collateral return found, balance collateral outputs
            if (txn.getBody().getCollateralReturn() != null)
                CollateralBuilders.balanceCollateralOutputs().apply(context,txn);

            //Incase change output goes below min ada after fee deduction
            ChangeOutputAdjustments.adjustChangeOutput(changeAddress, nSigners).apply(context, txn);
        };
    }
}
