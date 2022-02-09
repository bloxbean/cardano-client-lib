package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a function to build a {@link TransactionOutput} and adds to the output list
 */
@FunctionalInterface
public interface TxOutputBuilder {
    void accept(TxBuilderContext context, List<TransactionOutput> outputs) throws TxBuildException, ApiException;

    default TxOutputBuilder and(TxOutputBuilder after) {
        Objects.requireNonNull(after);

        return (context, outputs) -> {
            accept(context, outputs);
            after.accept(context, outputs);
        };
    }

    default TxBuilder buildInputs(TxInputBuilder after) {
        return (context, transaction) -> {
            List<TransactionInput> inputs = new ArrayList<>();
            List<TransactionOutput> outputs = new ArrayList<>();

            this.accept(context, outputs);
            Tuple<List<TransactionInput>, List<TransactionOutput>> tuple = after.apply(context, outputs);

            if (inputs.size() > 0)
                transaction.getBody().getInputs().addAll(inputs);
            transaction.getBody().getInputs().addAll(tuple._1);

            if (outputs.size() > 0)
                transaction.getBody().getOutputs().addAll(outputs);
            if (tuple._2 != null && tuple._2.size() > 0)
                transaction.getBody().getOutputs().addAll(tuple._2);

            //Clear multiasset in the context
            context.clearMintMultiAssets();
        };
    }

}
