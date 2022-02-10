package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.function.helper.OutputBuilders;
import com.bloxbean.cardano.client.transaction.spec.PlutusData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * Represents the required output of a transaction
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Output {
    private String address;
    private String policyId;
    private String assetName;
    private BigInteger qty;
    private PlutusData datum;
    private boolean isMintOutput;

    public TxOutputBuilder outputBuilder() {
        return OutputBuilders.createFromOutput(this);
    }

    public TxOutputBuilder mintOutputBuilder() {
        return OutputBuilders.createFromMintOutput(this);
    }
}
