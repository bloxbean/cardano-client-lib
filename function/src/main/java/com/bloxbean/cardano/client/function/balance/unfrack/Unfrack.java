package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.balance.TxBalancer;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link TxBalancer} which "unfracks" change outputs in the same transaction: token change is bundled by policy
 * and ada change is subdivided into several outputs, so that the wallet ends up with multiple independent UTxOs
 * which can be spent by concurrent (in-flight) transactions.
 * <p>
 * Algorithm follows the Evolution SDK unfrack implementation, see {@link UnfrackPlanner}.
 * <p>
 * Only {@link ChangeOutput}s without datum or script reference are split. The largest piece stays at the index of the
 * original change output (and carries the {@code feeReserve} from {@link UnfrackConfig}, as the fee is deducted
 * from it during balancing); the other pieces are appended at the end, so indexes of all other outputs are
 * preserved.
 *
 * <pre>{@code
 * quickTxBuilder.compose(tx)
 *     .feePayer(sender)
 *     .balancer(new Unfrack())
 *     .withSigner(signer)
 *     .completeAndWait();
 * }</pre>
 */
@Slf4j
public class Unfrack implements TxBalancer {
    private final UnfrackConfig config;

    public Unfrack() {
        this(UnfrackConfig.defaults());
    }

    public Unfrack(UnfrackConfig config) {
        config.validate();
        this.config = config;
    }

    public UnfrackConfig getConfig() {
        return config;
    }

    @Override
    public TxBuilder preBalance() {
        return (context, transaction) -> {
            UnfrackPlanner planner = new UnfrackPlanner(config, context.getProtocolParams());
            List<TransactionOutput> outputs = new ArrayList<>(transaction.getBody().getOutputs());
            List<TransactionOutput> appended = new ArrayList<>();

            for (int i = 0; i < outputs.size(); i++) {
                TransactionOutput output = outputs.get(i);
                if (isSplittable(output))
                    splitInPlace(planner, outputs, i, appended);
            }

            if (!appended.isEmpty()) {
                outputs.addAll(appended);
                transaction.getBody().setOutputs(outputs);
            }
        };
    }

    /**
     * Replace the output at the given index with the fee bearing piece and collect the other pieces in appended.
     */
    private void splitInPlace(UnfrackPlanner planner, List<TransactionOutput> outputs, int index,
                              List<TransactionOutput> appended) {
        TransactionOutput output = outputs.get(index);
        List<Value> pieces = split(planner, output);
        if (pieces.size() <= 1)
            return;

        outputs.set(index, new ChangeOutput(output.getAddress(), pieces.get(0)));
        for (Value piece : pieces.subList(1, pieces.size()))
            appended.add(new ChangeOutput(output.getAddress(), piece));

        log.debug("Unfrack: split change output at {} into {} outputs", output.getAddress(), pieces.size());
    }

    /**
     * Split a change output. The first returned value is the fee bearing piece (largest lovelace amount).
     */
    private List<Value> split(UnfrackPlanner planner, TransactionOutput output) {
        Value value = output.getValue();
        BigInteger reserve = config.getFeeReserve();
        if (value.getCoin().compareTo(reserve) <= 0)
            return List.of(value);

        List<Value> pieces = planner.plan(output.getAddress(),
                new Value(value.getCoin().subtract(reserve), value.getMultiAssets()));
        if (pieces.size() <= 1)
            return List.of(value);

        int largest = 0;
        for (int i = 1; i < pieces.size(); i++) {
            if (pieces.get(i).getCoin().compareTo(pieces.get(largest).getCoin()) > 0)
                largest = i;
        }

        List<Value> result = new ArrayList<>(pieces.size());
        Value feePiece = pieces.get(largest);
        result.add(new Value(feePiece.getCoin().add(reserve), feePiece.getMultiAssets()));
        for (int i = 0; i < pieces.size(); i++) {
            if (i != largest)
                result.add(pieces.get(i));
        }

        assertValuePreserved(value, result);
        return result;
    }

    private static boolean isSplittable(TransactionOutput output) {
        return output instanceof ChangeOutput
                && output.getValue() != null
                && output.getValue().getCoin() != null
                && output.getValue().getCoin().signum() > 0
                && output.getDatumHash() == null
                && output.getInlineDatum() == null
                && output.getScriptRef() == null;
    }

    private static void assertValuePreserved(Value original, List<Value> pieces) {
        Map<String, BigInteger> expected = toUnitMap(original);
        Map<String, BigInteger> actual = new HashMap<>();
        for (Value piece : pieces)
            toUnitMap(piece).forEach((unit, qty) -> actual.merge(unit, qty, BigInteger::add));
        actual.values().removeIf(qty -> qty.signum() == 0);
        if (!expected.equals(actual))
            throw new IllegalStateException("Unfrack value mismatch. Original: " + expected + ", split sum: " + actual);
    }

    private static Map<String, BigInteger> toUnitMap(Value value) {
        Map<String, BigInteger> units = new HashMap<>();
        if (value.getCoin() != null && value.getCoin().signum() != 0)
            units.put("lovelace", value.getCoin());
        if (value.getMultiAssets() != null) {
            for (MultiAsset ma : value.getMultiAssets()) {
                for (Asset asset : ma.getAssets()) {
                    if (asset.getValue().signum() != 0)
                        units.merge(ma.getPolicyId() + "." + asset.getNameAsHex(), asset.getValue(), BigInteger::add);
                }
            }
        }
        return units;
    }
}
