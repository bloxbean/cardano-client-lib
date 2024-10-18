package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.MinAdaChecker;
import com.bloxbean.cardano.client.function.Output;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Provides list of helper methods to create {@link TransactionOutput} from {@link Output}
 */
public class OutputBuilders {

    /**
     * Function to transform {@link Output} to {@link TransactionOutput}
     * Also checks min ada required in the output and update transaction output accordingly
     *
     * @param output
     * @return TxOutputBuilder function
     */
    public static TxOutputBuilder createFromOutput(Output output) {
        return createFromOutput(output, false, null);
    }

    /**
     * Function to transform {@link Output} to {@link TransactionOutput} for newly minted asset
     * Also checks min ada required in the output and update transaction output accordingly
     *
     * @param output
     * @return
     */
    public static TxOutputBuilder createFromMintOutput(Output output) {
        return createFromOutput(output, true, null);
    }

    /**
     * Function to transform {@link Output} to {@link TransactionOutput} for newly minted asset
     * Also checks min ada required in the output and update transaction output accordingly
     *
     * @param output        Output
     * @param isMintOutput  true if mint transaction, else false
     * @param minAdaChecker MinAdaChecker function
     * @return TxOutputBuilder function
     */
    public static TxOutputBuilder createFromOutput(Output output,
                                                   boolean isMintOutput, MinAdaChecker minAdaChecker) {

        return (context, outputs) -> {
            Objects.requireNonNull(output);
            Objects.requireNonNull(output.getAddress());

            if (LOVELACE.equals(output.getAssetName()) && (output.getPolicyId() == null || output.getPolicyId().isEmpty())) {
                handleLovelaceOutput(output, minAdaChecker, context, outputs);
            } else {
                handleMultiAssetOutput(output, minAdaChecker, context, outputs, isMintOutput);
            }
        };
    }

    /**
     * Function to create a TransactionOutput from a given TransactionOutput after min required ada checking
     *
     * @param txnOutput
     * @return TxOutputBuilder function
     */
    public static TxOutputBuilder createFromOutput(TransactionOutput txnOutput) {
        return createFromOutput(txnOutput, false, null);
    }

    /**
     * Function to create a TransactionOutput from a given mint TransactionOutput after min required ada checking
     *
     * @param txnOutput
     * @return TxOutputBuilder function
     */
    public static TxOutputBuilder createFromMintOutput(TransactionOutput txnOutput) {
        return createFromOutput(txnOutput, true, null);
    }

    /**
     * Function to create a TransactionOutput from a given TransactionOutput after min required ada checking
     *
     * @param txnOutput
     * @param isMintOutput  true if mint transaction, else false
     * @param minAdaChecker MinAdaChecker function
     * @return TxOutputBuilder function
     */
    public static TxOutputBuilder createFromOutput(TransactionOutput txnOutput, boolean isMintOutput, MinAdaChecker minAdaChecker) {
        return (context, outputs) -> {
            Objects.requireNonNull(txnOutput);
            Objects.requireNonNull(txnOutput.getAddress());
            Objects.requireNonNull(txnOutput.getValue());

            String address = txnOutput.getAddress();
            Value value = txnOutput.getValue();

            //If it's a mint output, add it to the context so that, it's not considered during input building
            if (isMintOutput) {
                List<MultiAsset> multiAssets = value.getMultiAssets();
                Objects.requireNonNull(multiAssets);

                multiAssets.stream()
                        .forEach(context::addMintMultiAsset);
            }

            if (context.isMergeOutputs()) {
                outputs.stream().filter(to -> address.equals(to.getAddress()))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            Value newValue = to.getValue().add(value);
                            to.setValue(newValue);
                            copyDatumAndScriptRef(txnOutput, to);

                            checkIfMinAdaIsThere(context, to, minAdaChecker);
                        }, () -> {
                            TransactionOutput output = new TransactionOutput(address, value);
                            copyDatumAndScriptRef(txnOutput, output);

                            checkIfMinAdaIsThere(context, output, minAdaChecker);
                            outputs.add(output);
                        });
            } else {
                TransactionOutput output = new TransactionOutput(address, value);
                copyDatumAndScriptRef(txnOutput, output);

                checkIfMinAdaIsThere(context, output, minAdaChecker);
                outputs.add(output);
            }
        };
    }

    private static void handleMultiAssetOutput(Output output, MinAdaChecker minAdaChecker, TxBuilderContext tc,
                                               List<TransactionOutput> outputs, boolean isMintOutput) {

        Asset asset = new Asset(output.getAssetName(), output.getQty());
        MultiAsset multiAsset = new MultiAsset(output.getPolicyId(), List.of(asset));

        if (isMintOutput) {
            tc.addMintMultiAsset(multiAsset);
        }

        if (tc.isMergeOutputs()) {
            outputs.stream().filter(to -> output.getAddress().equals(to.getAddress()))
                    .findFirst()
                    .ifPresentOrElse(to -> {
                        Value newValue = to.getValue().add(new Value(BigInteger.ZERO, List.of(multiAsset)));
                        to.setValue(newValue);
                        copyDatumAndScriptRef(output, to);

                        checkIfMinAdaIsThere(tc, to, minAdaChecker);
                    }, () -> {
                        TransactionOutput to = new TransactionOutput(output.getAddress(), new Value(BigInteger.ZERO, List.of(multiAsset)));
                        copyDatumAndScriptRef(output, to);

                        checkIfMinAdaIsThere(tc, to, minAdaChecker);
                        outputs.add(to);
                    });
        } else {
            TransactionOutput to = new TransactionOutput(output.getAddress(), new Value(BigInteger.ZERO, List.of(multiAsset)));
            copyDatumAndScriptRef(output, to);

            checkIfMinAdaIsThere(tc, to, minAdaChecker);
            outputs.add(to);
        }
    }

    private static void handleLovelaceOutput(Output output, MinAdaChecker minAdaChecker, TxBuilderContext tc,
                                             List<TransactionOutput> outputs) {

        if (tc.isMergeOutputs()) {
            outputs.stream().filter(to -> output.getAddress().equals(to.getAddress()))
                    .findFirst()
                    .ifPresentOrElse(to -> {
                                BigInteger newCoinAmt = to.getValue().getCoin().add(output.getQty());
                                to.getValue().setCoin(newCoinAmt);
                                copyDatumAndScriptRef(output, to);

                                checkIfMinAdaIsThere(tc, to, minAdaChecker);
                            },
                            () -> {
                                TransactionOutput to = new TransactionOutput(output.getAddress(), new Value(output.getQty(), new ArrayList<>()));
                                copyDatumAndScriptRef(output, to);

                                checkIfMinAdaIsThere(tc, to, minAdaChecker);
                                outputs.add(to);
                            }
                    );
        } else {
            TransactionOutput to = new TransactionOutput(output.getAddress(), new Value(output.getQty(), new ArrayList<>()));
            copyDatumAndScriptRef(output, to);

            checkIfMinAdaIsThere(tc, to, minAdaChecker);
            outputs.add(to);
        }
    }

    private static void copyDatumAndScriptRef(Output output, TransactionOutput to) {
        if (output.getDatum() != null && to.getDatumHash() == null && to.getInlineDatum() == null) {
            if (output.isInlineDatum()) {
                to.setInlineDatum(output.getDatum());
            } else {
                to.setDatumHash(output.getDatum().getDatumHashAsBytes());
            }
        }

        if (output.getScriptRef() != null && to.getScriptRef() == null) {
            to.setScriptRef(output.getScriptRef());
        }
    }

    private static void copyDatumAndScriptRef(TransactionOutput fromOutput, TransactionOutput toOutput) {
        if (fromOutput.getDatumHash() != null && fromOutput.getDatumHash().length > 0
                && toOutput.getDatumHash() == null)
        {
            toOutput.setDatumHash(fromOutput.getDatumHash());
        }

        if (fromOutput.getInlineDatum() != null && toOutput.getInlineDatum() == null) {
            toOutput.setInlineDatum(fromOutput.getInlineDatum());
        }

        if (fromOutput.getScriptRef() != null && toOutput.getScriptRef() == null) {
            toOutput.setScriptRef(fromOutput.getScriptRef());
        }
    }

    private static void checkIfMinAdaIsThere(TxBuilderContext tc, TransactionOutput output, MinAdaChecker minAdaChecker) {
        BigInteger additionalLovelace;
        if (minAdaChecker != null) {
            additionalLovelace = minAdaChecker.apply(tc, output);
        } else {
            additionalLovelace = MinAdaCheckers.minAdaChecker().apply(tc, output);
        }

        if (additionalLovelace != null && additionalLovelace.compareTo(BigInteger.ZERO) == 1) {
            Value orginalValue = output.getValue();
            Value newValue = orginalValue.add(Value.builder().coin(additionalLovelace).build());

            output.setValue(newValue);
        }
    }

}
