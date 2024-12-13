package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class OutputMergers {

    /**
     * Function to merge outputs for an address into one output. This is useful when you have multiple outputs for same address.
     * This function will merge all outputs into one output and remove the old outputs. Only outputs
     * with given address, but no datumHash, inlineDatum and scriptRef will be merged.
     * @param address
     * @return TxBuilder
     */
    public static TxBuilder mergeOutputsForAddress(String address) {
        return ((context, transaction) -> {
            //Find all outputs with given address, but no datumHash, inlineDatum and scriptRef
            List<TransactionOutput> addressOutputs = transaction.getBody().getOutputs()
                    .stream().filter(output -> output.getAddress().equals(address)
                            && output.getDatumHash() == null && output.getInlineDatum() == null
                            && (output.getScriptRef() == null || output.getScriptRef().length == 0))
                    .collect(Collectors.toList());

            if (addressOutputs == null || addressOutputs.size() == 0 || addressOutputs.size() == 1)
                return;

            Optional<Value> totalValue = addressOutputs.stream().map(output -> output.getValue())
                    .reduce((value1, value2) -> value1.add(value2));

            TransactionOutput newOutput = new TransactionOutput(address, totalValue.get());

            //remove old outputs from transaction
            transaction.getBody().getOutputs().removeAll(addressOutputs);
            transaction.getBody().getOutputs().add(newOutput);
        });
    }
}
