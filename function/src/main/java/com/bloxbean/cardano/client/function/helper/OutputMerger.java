package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.Getter;
import lombok.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link TxBuilder} that merges all outputs of an address into one output. Created by
 * {@link OutputMergers#mergeOutputsForAddress(String)}.
 * <p>
 * Merging outputs after a wallet shaper
 * ({@link com.bloxbean.cardano.client.function.walletshape.WalletShaper}) undoes the shaping.
 */
@Getter
public class OutputMerger implements TxBuilder {
    private final String address;

    public OutputMerger(@NonNull String address) {
        this.address = address;
    }

    @Override
    public void apply(TxBuilderContext context, Transaction transaction) {
        //Find all outputs with given address, but no datumHash, inlineDatum and scriptRef
        List<TransactionOutput> addressOutputs = transaction.getBody().getOutputs()
                .stream().filter(output -> output.getAddress().equals(address)
                        && output.getDatumHash() == null && output.getInlineDatum() == null
                        && (output.getScriptRef() == null || output.getScriptRef().length == 0))
                .collect(Collectors.toList());

        if (addressOutputs.size() <= 1)
            return;

        Optional<Value> totalValue = addressOutputs.stream().map(TransactionOutput::getValue)
                .reduce(Value::add);

        TransactionOutput newOutput = new TransactionOutput(address, totalValue.get());

        //remove old outputs from transaction
        transaction.getBody().getOutputs().removeAll(addressOutputs);
        transaction.getBody().getOutputs().add(newOutput);
    }
}
