package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.function.MinAdaChecker;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.api.util.UtxoUtil;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;
import static com.bloxbean.cardano.client.function.helper.RedeemerUtil.getScriptInputFromRedeemer;
import static com.bloxbean.cardano.client.function.helper.RedeemerUtil.getScriptInputIndex;


/**
 * Provide helper methods to adjust change output when a change output doesn't have enough lovelace (Minimum Required Ada)
 */
@Slf4j
public class ChangeOutputAdjustments {
    private final static int MAX_NO_RETRY_TO_ADJUST = 3;
    private static BigInteger changeSelectionBuffer = ONE_ADA.multiply(BigInteger.valueOf(2));
    private static BigInteger DEFAULT_FEE = BigInteger.valueOf(17_000);

    /**
     * Function to adjust change output in a <code>Transaction</code> to meet min ada requirement.
     * Finds a change output specific to given change address.
     * If multiple change outputs with less than min required ada are found for the change address, it throws <code>{@link TxBuildException}</code>
     * Get additional utxos from change address and update the change output.
     * Re-calculates fee and checks min ada in change output.
     * Retry if required, upto 3 times.
     *
     * <br>Default values:
     * <br> Sender Address = Change Address
     * <br> No of signers = 1
     *
     * @param changeAddress Address for change output selection and to select additional utxos
     * @return <code>TxBuilder</code> function
     * @throws TxBuildException If multiple change outputs with less than min required ada are found for the change address.
     * @throws ApiRuntimeException If api call error
     */
    public static TxBuilder adjustChangeOutput(String changeAddress) {
        return adjustChangeOutput(changeAddress, changeAddress, 1);
    }

    /**
     * Function to adjust change output in a <code>Transaction</code> to meet min ada requirement.
     * Finds a change output specific to given change address.
     * If multiple change outputs with less than min required ada are found for the change address, it throws <code>{@link TxBuildException}</code>
     * Get additional utxos from change address and update the change output.
     * Re-calculates fee and checks min ada in change output.
     * Retry if required, upto 3 times.
     *
     * <br>Default values:
     * <br> Sender Address = Change Address
     *
     * @param changeAddress Address for change output selection and to select additional utxos
     * @param noOfSigners   No of required signers. Required for fee calculation after adjustment
     * @return <code>TxBuilder</code> function
     * @throws TxBuildException If multiple change outputs with less than min required ada are found for the change address.
     * @throws ApiRuntimeException If api call error
     */
    public static TxBuilder adjustChangeOutput(String changeAddress, int noOfSigners) {
        return adjustChangeOutput(changeAddress, changeAddress, noOfSigners);
    }

    /**
     * Function to adjust change output in a <code>Transaction</code> to meet min ada requirement.
     * Finds a change output specific to given change address.
     * If multiple change outputs with less than min required ada are found for the change address, it throws <code>{@link TxBuildException}</code>
     * Get additional utxos from sender address and update the change output.
     * Re-calculates fee and checks min ada in change output.
     * Retry if required, upto 3 times.
     *
     * @param senderAddress Address to select additional utxos
     * @param changeAddress Address for change output selection
     * @param noOfSigners   No of required signers. Required for fee calculation after adjustment
     * @return <code>TxBuilder</code> function
     * @throws TxBuildException If multiple change outputs with less than min required ada are found for the change address.
     * @throws ApiRuntimeException If api call error
     */
    public static TxBuilder adjustChangeOutput(String senderAddress, String changeAddress, int noOfSigners) {
        return (context, transaction) -> {
            int counter = 0;
            while (true) {
                //Check if any output doesn't meet minAda requirement
                MinAdaChecker minAdaChecker = MinAdaCheckers.minAdaChecker();
                List<Tuple<TransactionOutput, BigInteger>> outputsWithLessAda = transaction.getBody().getOutputs()
                        .stream()
                        .filter(transactionOutput -> changeAddress.equals(transactionOutput.getAddress()))
                        .map(transactionOutput ->
                                new Tuple<TransactionOutput, BigInteger>(transactionOutput, minAdaChecker.apply(context, transactionOutput)))
                        .filter(tuple -> tuple._2.compareTo(BigInteger.ZERO) == 1)
                        .collect(Collectors.toList());

                if (outputsWithLessAda.size() == 0)
                    break; //All ok
                else if (outputsWithLessAda.size() > 1) {
                    if (log.isDebugEnabled())
                        log.debug(String.valueOf(transaction.getBody().getOutputs()));
                    throw new TxBuildException("Multiple outputs found with same change address with less than min required ada. Can't balance. Please adjust output first.");
                }


                if (counter >= MAX_NO_RETRY_TO_ADJUST) {
                    throw new TxBuildException("Transaction rebalance failed. Max # of retry reached: " + counter);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Trying to adjust the change output. Retry # " + counter);
                }

                //check outputs for minAda and balance if required
                try {
                    adjust(context, transaction, outputsWithLessAda.get(0)._1, outputsWithLessAda.get(0)._2, senderAddress, changeAddress);
                } catch (ApiException apiException) {
                    throw new ApiRuntimeException("Error in api call", apiException);
                }
                FeeCalculators.feeCalculator(changeAddress, noOfSigners)
                        .apply(context, transaction);

                counter++;
            }
        };
    }

    private static void adjust(TxBuilderContext context, Transaction transaction, TransactionOutput outputToAdjust, BigInteger additionalRequiredAmt,
                               String senderAddress, String changeAddress) throws ApiException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(transaction);

        if (additionalRequiredAmt.compareTo(BigInteger.ZERO) == 0)
            return;

        List<Tuple<Redeemer, TransactionInput>> originalRedeemerTxnInputList = null;
        //Create a copy of all  redeemer and corresponding inputs
        if (transaction.getWitnessSet().getRedeemers() != null && transaction.getWitnessSet().getRedeemers().size() > 0) {
            //Create a Redeemer and corresponding TransactionInput map
            originalRedeemerTxnInputList = transaction.getWitnessSet().getRedeemers()
                    .stream()
                    .map(redeemer -> {
                        if (redeemer.getTag() == RedeemerTag.Spend)
                            return new Tuple<Redeemer, TransactionInput>(redeemer, getScriptInputFromRedeemer(redeemer, transaction));
                        else
                            return new Tuple<Redeemer, TransactionInput>(redeemer, null);
                    })
                    .collect(Collectors.toList());
        }

        Set<Utxo> existingUtxos = transaction.getBody().getInputs()
                .stream()
                .map(ti -> Utxo.builder()
                        .txHash(ti.getTransactionId())
                        .outputIndex(ti.getIndex())
                        .build()).collect(Collectors.toSet());

        //Add some buffer
        final BigInteger totalRequiredWithBuffer = additionalRequiredAmt.add(changeSelectionBuffer);

        List<Utxo> newUtxos = new ArrayList<>();
        UtxoSelector utxoSelector = context.getUtxoSelector();
        //Try to find ada only utxo
        Optional<Utxo> utxoOptional = utxoSelector.findFirst(senderAddress, utxo ->
                !existingUtxos.contains(utxo) && utxo.getAmount().size() == 1
                        && utxo.getAmount().get(0).getQuantity().compareTo(totalRequiredWithBuffer) == 1);

        if (utxoOptional.isPresent()) {
            newUtxos.add(utxoOptional.get());
        } else { //Not Found
            //Use utxo selection strategy
            List<Utxo> utxosFound = null;

            try {
                utxosFound = context.getUtxoSelectionStrategy().selectUtxos(senderAddress, LOVELACE, totalRequiredWithBuffer, existingUtxos);
            } catch (ApiException ex) {
                //Not found... check without Buffer
                utxosFound = context.getUtxoSelectionStrategy().selectUtxos(senderAddress, LOVELACE, additionalRequiredAmt, existingUtxos);
            }

            if (utxosFound != null && utxosFound.size() > 0)
                newUtxos.addAll(utxosFound);
        }

        //Update Inputs with new utxos
        newUtxos.forEach(utxo -> {
            transaction.getBody().getInputs()
                    .add(new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()));
            UtxoUtil.copyUtxoValuesToOutput(outputToAdjust, utxo);
            context.addUtxo(utxo);
        });

        //As transaction is changed now, fee calculation is required.
        //Let's make fee to a default one and add the existing fee to changeoutput
        BigInteger existingFee = transaction.getBody().getFee();
        Value changeOutputValue = outputToAdjust.getValue();
        changeOutputValue = changeOutputValue.plus(Value.builder().coin(existingFee).build());
        outputToAdjust.setValue(changeOutputValue);
        transaction.getBody().setFee(DEFAULT_FEE); //Just a dummy fee for now

        if (originalRedeemerTxnInputList != null && originalRedeemerTxnInputList.size() > 0) {
            List<Redeemer> redeemerList = new ArrayList<>();
            //Re-adjust redeemer's index
            originalRedeemerTxnInputList.forEach(tuple -> {
                Redeemer redeemer = tuple._1;
                TransactionInput input = tuple._2;
                if (redeemer.getTag() == RedeemerTag.Spend) {
                    int index = getScriptInputIndex(input, transaction);
                    if (index == -1) {
                        throw new TxBuildException(String.format("Invalid redeemer index: %s, TransactionInput not found %s", index, input));
                    }

                    redeemer.setIndex(BigInteger.valueOf(index));
                }

                redeemerList.add(redeemer);
            });

            //Replace redeemer list with new list
            transaction.getWitnessSet().setRedeemers(redeemerList);
        }
    }

}
