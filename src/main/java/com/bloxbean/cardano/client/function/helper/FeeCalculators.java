package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Provides helper methods to get fee calculation {@link TxBuilder} transformer
 */
public class FeeCalculators {

    public static TxBuilder feeCalculator(String changeAddress, int noOfSigners) {
        return ((transactionContext, transaction) -> {
            execute(transactionContext, transaction, changeAddress, noOfSigners, null);
        });
    }

    public static TxBuilder feeCalculator(int noOfSigners, UpdateOutputFunction updateOutputWithFeeFunc) {
        return ((transactionContext, transaction) -> {
            execute(transactionContext, transaction, null, noOfSigners, updateOutputWithFeeFunc);
        });
    }

    private static void execute(TxBuilderContext context, Transaction transaction, String changeAddress,
                                int noOfSigners, UpdateOutputFunction updateOutputWithFeeFunc) {
        FeeCalculationService feeCalculationService = context.getBackendService().getFeeCalculationService();

        try {
            TransactionBody tbody = transaction.getBody();

            Transaction clonedTxn = createTransactionWithDummyWitnesses(transaction, noOfSigners);
            BigInteger baseFee = feeCalculationService.calculateFee(clonedTxn);

            //Check if there is any script included in this txn
            BigInteger scriptFee = BigInteger.ZERO;

            if (transaction.getWitnessSet() != null) {
                List<Redeemer> redeemerList = transaction.getWitnessSet().getRedeemers();
                if (redeemerList != null && redeemerList.size() > 0) {
                    List<ExUnits> exUnits = redeemerList.stream().map(redeemer -> redeemer.getExUnits())
                            .collect(Collectors.toList());

                    if (exUnits != null)
                        scriptFee = feeCalculationService.calculateScriptFee(exUnits);
                }
            }

            BigInteger totalFee = baseFee.add(scriptFee);
            tbody.setFee(totalFee);

            if (updateOutputWithFeeFunc == null) {
                //Update amount in change address
                tbody.getOutputs().stream().filter(output -> changeAddress.equals(output.getAddress()))
                        .findFirst()
                        .ifPresentOrElse(output -> {
                            output.getValue().setCoin(output.getValue().getCoin().subtract(totalFee));
                        }, () -> {
                            Value value = new Value(BigInteger.ZERO.subtract(totalFee), new ArrayList<>());
                            TransactionOutput output = new TransactionOutput(changeAddress, value); //TODO - Need to calculate fee again
                        });
            } else {
                updateOutputWithFeeFunc.accept(totalFee, transaction.getBody().getOutputs());
            }
        } catch (ApiException apiException) {
            throw new TxBuildException("Error in fee calculation", apiException);
        } catch (CborSerializationException e) {
            throw new TxBuildException("Error in fee calculation", e);
        }
    }

    private static Transaction createTransactionWithDummyWitnesses(Transaction transaction, int noOfSigners) {
        Transaction cloneTxn;
        try {
            BigInteger orginalFee = transaction.getBody().getFee();
            transaction.getBody().setFee(BigInteger.valueOf(170000)); //To avoid any NPE due to null fee

            cloneTxn = Transaction.deserialize(transaction.serialize());

            //reset fee
            transaction.getBody().setFee(orginalFee);
        } catch (CborDeserializationException | CborSerializationException e) {
            throw new TxBuildException("Error cloning the transaction", e);
        }

        for (int i = 0; i < noOfSigners; i++) {
            Account account = new Account();
            cloneTxn = account.sign(cloneTxn);
        }

        return cloneTxn;
    }

    public interface UpdateOutputFunction extends BiConsumer<BigInteger, List<TransactionOutput>> {
        void accept(BigInteger fee, List<TransactionOutput> outputs);
    }
}
