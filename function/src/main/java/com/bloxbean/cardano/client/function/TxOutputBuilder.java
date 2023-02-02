package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a function to build a {@link TransactionOutput} and adds to the output list
 */
@FunctionalInterface
public interface TxOutputBuilder {
    void accept(TxBuilderContext context, List<TransactionOutput> outputs);

    default TxOutputBuilder and(TxOutputBuilder after) {
        Objects.requireNonNull(after);

        return (context, outputs) -> {
            accept(context, outputs);
            after.accept(context, outputs);
        };
    }

    @Deprecated
    default TxBuilder buildInputs(TxInputBuilder after) {
        return (context, transaction) -> {
            List<TransactionInput> inputs = new ArrayList<>();
            List<TransactionOutput> outputs = new ArrayList<>();

            this.accept(context, outputs);
            TxInputBuilder.Result inputBuilderResult = after.apply(context, outputs);

            if (!inputs.isEmpty())
                transaction.getBody().getInputs().addAll(inputs);
            transaction.getBody().getInputs().addAll(inputBuilderResult.inputs);

            if (!outputs.isEmpty())
                transaction.getBody().getOutputs().addAll(outputs);
            if (inputBuilderResult.changes != null && !inputBuilderResult.changes.isEmpty())
                inputBuilderResult.changes.forEach(txOutput -> {
                    TransactionOutput txOutputSameAddress = transaction.getBody().
                            getOutputs()
                            .stream()
                            .filter(transactionOutput -> transactionOutput.getAddress().equals(txOutput.getAddress()))
                            .findFirst().orElse(null);
                    if (txOutputSameAddress != null) {
                        txOutputSameAddress.setValue(txOutputSameAddress.getValue().plus(txOutput.getValue()));
                    } else {
                        transaction.getBody().getOutputs().add(txOutput);
                    }
                });

            //Clear multiasset in the context
            context.clearMintMultiAssets();
        };
    }

    default TxBuilder buildInputs(TxInputBuilder after, boolean separateChangeOutput) {
        return (context, transaction) -> {
            List<TransactionInput> inputs = new ArrayList<>();
            List<TransactionOutput> outputs = new ArrayList<>();

            this.accept(context, outputs);
            TxInputBuilder.Result inputBuilderResult = after.apply(context, outputs);

            if (!inputs.isEmpty())
                transaction.getBody().getInputs().addAll(inputs);
            transaction.getBody().getInputs().addAll(inputBuilderResult.inputs);

            if (!outputs.isEmpty())
                transaction.getBody().getOutputs().addAll(outputs);
            if (inputBuilderResult.changes != null && !inputBuilderResult.changes.isEmpty()) {
                if (separateChangeOutput) {
                    transaction.getBody().getOutputs().addAll(inputBuilderResult.changes);
                } else {
                    inputBuilderResult.changes.forEach(txOutput -> {
                        TransactionOutput txOutputSameAddress = transaction.getBody().
                                getOutputs()
                                .stream()
                                .filter(transactionOutput -> transactionOutput.getAddress().equals(txOutput.getAddress()))
                                .findFirst().orElse(null);
                        if (txOutputSameAddress != null) {
                            txOutputSameAddress.setValue(txOutputSameAddress.getValue().plus(txOutput.getValue()));
                        } else {
                            transaction.getBody().getOutputs().add(txOutput);
                        }
                    });
                }
            }
            //Clear multiasset in the context
            context.clearMintMultiAssets();
        };
    }
}
