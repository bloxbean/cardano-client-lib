package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.AddressIterator;
import com.bloxbean.cardano.client.api.common.AddressIterators;
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
        return balanceTx(AddressIterators.of(changeAddress), nSigners);
    }

    /**
     * Function to balance an unbalanced transaction.
     * This is a wrapper function which invokes the following functions to create a balanced transaction
     * <ul>
     *  <li>{@link FeeCalculators#feeCalculator(String, int)} </li>
     *  <li>{@link ChangeOutputAdjustments#adjustChangeOutput(String, int)} </li>
     *  <li>{@link CollateralBuilders#balanceCollateralOutputs()} (For transaction with collateral return)</li>
     * </ul>
     *
     * @param changeAddressIter An iterator for change addresses to be used for adjusting outputs.
     *                          The first address from the iterator is used as the change address
     * @param nSigners          No of signers. This is required for accurate fee calculation.
     * @return A {@link TxBuilder} instance that balances the transaction during its building phase.
     */
    public static TxBuilder balanceTx(AddressIterator changeAddressIter, int nSigners) {
        return (context, txn) -> {
            String changeAddress = changeAddressIter.getFirst().getAddress();

            FeeCalculators.feeCalculator(changeAddress, nSigners).apply(context, txn);

            //Incase change output goes below min ada after fee deduction
            ChangeOutputAdjustments.adjustChangeOutput(changeAddressIter.clone(), changeAddress, nSigners).apply(context, txn);

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
     *
     * @param changeAddress Change output address
     * @param additionalSigners No of Additional signers. This is required for accurate fee calculation.
     * @return TxBuilder
     */
    public static TxBuilder balanceTxWithAdditionalSigners(String changeAddress, int additionalSigners) {
        return balanceTxWithAdditionalSigners(AddressIterators.of(changeAddress), additionalSigners);
    }

    /**
     * Function to balance an unbalanced transaction using Automatic Utxo Discovery with Additional Signers.
     * This is a wrapper function which invokes the following functions to create a balanced transaction
     * <ul>
     *  <li>{@link FeeCalculators#feeCalculator(String, int)} </li>
     *  <li>{@link ChangeOutputAdjustments#adjustChangeOutput(String, int)} </li>
     *  <li>{@link CollateralBuilders#balanceCollateralOutputs()} (For transaction with collateral return)</li>
     * </ul>
     *
     * @param changeAddressIter An iterator to provide addresses for any change output.
     *                          The first address from the iterator is used as the change address
     * @param additionalSigners The number of additional required signers to include in the
     *                          transaction balancing process.
     * @return A {@link TxBuilder} function that applies the necessary adjustments to balance
     *         the transaction, including fee calculation, change output adjustments, and
     *         collateral balancing if applicable.
     */
    public static TxBuilder balanceTxWithAdditionalSigners(AddressIterator changeAddressIter, int additionalSigners) {
        return (context, txn) -> {
            String changeAddress = changeAddressIter.getFirst().getAddress();

            FeeCalculators.feeCalculator(changeAddress, UtxoUtil.getNoOfRequiredSigners(context.getAllUtxos()) + additionalSigners).apply(context, txn);

            //Incase change output goes below min ada after fee deduction
            ChangeOutputAdjustments.adjustChangeOutput(changeAddressIter.clone(), UtxoUtil.getNoOfRequiredSigners(context.getAllUtxos()) + additionalSigners).apply(context, txn);

            //If collateral return found, balance collateral outputs
            if (txn.getBody().getCollateralReturn() != null)
                CollateralBuilders.balanceCollateralOutputs().apply(context,txn);
        };
    }
}
