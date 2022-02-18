package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.function.helper.OutputBuilders;
import com.bloxbean.cardano.client.transaction.spec.PlutusData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * Represents the required output of a transaction.
 * <br>It gets converted to <code>TransactionOutput</code> by <code>{@link TxOutputBuilder}</code> function
 * <br>If asset is a multiasset, a min amount of ada is automatically added to the final TransactionOutput to meet minAda requirement in the output.
 * <br>This class provides a simple interface for a lovelace only or multiasset only transfer.
 * <br>To send both multiasset and ada in the same output to the same receiver, better to use TransactionOutput instead of this class.
 * <br>
 * <br>But to send both lovelace and multiasset to the same receiver using this class, create lovelace output first and then multiasset outputs for correct min ada calculation
 * <br>
 * <br>
 * Example:
 * <br>
 * <pre>
 *        Output output1 = Output.builder()
 *                   .address(receiver1)
 *                   .assetName(LOVELACE)
 *                   .qty(adaToLovelace(2.3))
 *                   .build();
 *        Output output2 = Output.builder()
 *                 .address(receiver)
 *                 .policyId(policy.getPolicyId())
 *                 .assetName("TestNFT")
 *                 .qty(BigInteger.valueOf(1))
 *                 .build();
 *
 *        output1.outputBuilder()
 *              .and(output2.outputBuilder())
 * </pre>
 *
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

    public TxOutputBuilder outputBuilder() {
        return OutputBuilders.createFromOutput(this);
    }

    public TxOutputBuilder mintOutputBuilder() {
        return OutputBuilders.createFromMintOutput(this);
    }
}
