package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.AddressIterator;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.common.AddressIterators;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.UtxoUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Helper class to balance a transaction with script.
 */
@Slf4j
public class ScriptBalanceTxProviders {

    //TODO -- Unit tests pending

    /**
     * Function to balance an unbalanced transaction using Automatic Utxo Discovery with Additional Signers.
     * This function invokes {@link BalanceTxBuilders#balanceTxWithAdditionalSigners(String, int)} to balance the transaction.
     * If any new inputs are added to the transaction during balancing, the script cost and fee will be recomputed.
     *
     * @param feePayer Fee payer address
     * @param additionalSigners o of Additional signers. This is required for accurate fee calculation.
     * @param containsScript If the transaction contains script
     * @return
     */
    public static TxBuilder balanceTx(String feePayer, int additionalSigners, boolean containsScript) {
        return balanceTx(AddressIterators.of(feePayer), additionalSigners, containsScript);
    }

    /**
     * Function to balance an unbalanced transaction using Automatic Utxo Discovery with Additional Signers.
     * This function invokes {@link BalanceTxBuilders#balanceTxWithAdditionalSigners(String, int)} to balance the transaction.
     * If any new inputs are added to the transaction during balancing, the script cost and fee will be recomputed.
     *
     * @param feePayerAddrIter Fee payer address iterator
     * @param additionalSigners No of Additional signers. This is required for accurate fee calculation.
     * @param containsScript If the transaction contains script
     * @return TxBuilder
     */
    public static TxBuilder balanceTx(AddressIterator feePayerAddrIter, int additionalSigners, boolean containsScript) {
        return (ctx, transaction) -> {

            String feePayerAddr = feePayerAddrIter.getFirst().getAddress();

            // A valid Cardano transaction requires at least one input.
            // When intent outputBuilder() returns null (e.g., deregistration/withdrawal),
            // refund at the change address may cover the fee, leaving 0 inputs.
            if (transaction.getBody().getInputs().isEmpty()) {
                Set<Utxo> excludeSet = new HashSet<>(ctx.getUtxos());
                Set<Utxo> selectedUtxos = ctx.getUtxoSelectionStrategy()
                        .select(feePayerAddr, new Amount(LOVELACE, BigInteger.ONE), excludeSet);

                for (Utxo utxo : selectedUtxos) {
                    transaction.getBody().getInputs().add(
                            new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()));
                    ctx.addUtxo(utxo);
                }

                if (!selectedUtxos.isEmpty()) {
                    // Add UTXO values to existing output at fee payer address, or create new change output
                    TransactionOutput changeOut = transaction.getBody().getOutputs().stream()
                            .filter(o -> feePayerAddr.equals(o.getAddress()))
                            .findFirst()
                            .orElseGet(() -> {
                                ChangeOutput co = new ChangeOutput(feePayerAddr,
                                        new Value(BigInteger.ZERO, new ArrayList<>()));
                                transaction.getBody().getOutputs().add(co);
                                return co;
                            });
                    for (Utxo utxo : selectedUtxos) {
                        UtxoUtil.copyUtxoValuesToOutput(changeOut, utxo);
                    }
                } else {
                    throw new TxBuildException("Transaction has no inputs. Could not find any UTXOs at fee payer address to fund the transaction: " + feePayerAddr);
                }
            }

            int inputSize = transaction.getBody().getInputs().size();
            BalanceTxBuilders.balanceTxWithAdditionalSigners(feePayerAddrIter.clone(), additionalSigners).apply(ctx, transaction);
            int newInputSize = transaction.getBody().getInputs().size();

            if (!containsScript || (newInputSize == inputSize)) { //TODO -- check for contains script
                return;
            }

            TransactionEvaluator transactionEvaluator = ctx.getTxnEvaluator();
            if (transactionEvaluator == null)
                throw new TxBuildException("Transaction evaluator is not set. Transaction evaluator is required to calculate script cost");

            //As new inputs are added, the cost of the transaction will increase
            //So, we need to recompute the script cost and fee
            //Add fee back to the fee payer's output
            BigInteger fee = transaction.getBody().getFee();
            transaction.getBody().setFee(BigInteger.valueOf(170000));
            transaction.getBody().getOutputs().stream()
                    .filter(output -> feePayerAddr.equals(output.getAddress()))
                    .max((to1, to2) -> to1.getValue().getCoin().compareTo(to2.getValue().getCoin()))
                    .ifPresent(transactionOutput -> transactionOutput.setValue(transactionOutput.getValue().add(Value.builder().coin(fee).build())));

            Value newCollateralReturnValue = transaction.getBody().getCollateralReturn()
                    .getValue().add(Value.builder().coin(transaction.getBody().getTotalCollateral()).build());
            transaction.getBody().getCollateralReturn().setValue(newCollateralReturnValue);
            transaction.getBody().setTotalCollateral(BigInteger.valueOf(1000000)); //reset total collateral. some dummy value

            //Recompute script cost
            ScriptCostEvaluators.evaluateScriptCost().apply(ctx, transaction);
            BalanceTxBuilders.balanceTxWithAdditionalSigners(feePayerAddrIter, additionalSigners).apply(ctx, transaction);
        };
    }
}
