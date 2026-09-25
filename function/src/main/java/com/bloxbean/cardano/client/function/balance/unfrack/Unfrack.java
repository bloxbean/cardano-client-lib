package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pre-balance {@link TxBuilder} which "unfracks" change outputs in the same transaction: change is split into several
 * outputs by a {@link ChangeSplitStrategy}, e.g. token bundles plus ADA-only outputs, so that change stays below max
 * value size and payments don't move every token in the wallet.
 * <p>
 * Default strategy is {@link EvolutionStrategy}. Other strategies: {@link PercentageSplitStrategy},
 * {@link EqualLanesStrategy}, {@link PaymentSizedStrategy}, {@link TargetShapeStrategy}.
 * <p>
 * Only {@link ChangeOutput}s without datum or script reference are split. The strategy splits
 * {@code change - feeReserve}; the reserve is then added to the largest piece, which stays at the index of the original
 * change output (the fee is deducted from it during balancing). The other pieces are appended at the end, so indexes
 * of all other outputs are preserved. The strategy result is verified: pieces must sum up to the change and each piece
 * must meet min-ada, otherwise an {@link IllegalStateException} is thrown.
 *
 * <pre>{@code
 * quickTxBuilder.compose(tx)
 *     .feePayer(sender)
 *     .preBalanceTx(new Unfrack(new EqualLanesStrategy()))
 *     .withSigner(signer)
 *     .completeAndWait();
 * }</pre>
 */
@Slf4j
@Getter
public class Unfrack implements TxBuilder {
    public static final BigInteger DEFAULT_FEE_RESERVE = BigInteger.valueOf(2_000_000L);

    private final ChangeSplitStrategy strategy;
    private final BigInteger feeReserve;

    public Unfrack() {
        this(new EvolutionStrategy());
    }

    public Unfrack(ChangeSplitStrategy strategy) {
        this(strategy, DEFAULT_FEE_RESERVE);
    }

    /**
     * @param strategy   change split strategy
     * @param feeReserve lovelace kept on the largest piece to pay the fee, so the other pieces still meet min-ada
     *                   after balancing
     */
    public Unfrack(@NonNull ChangeSplitStrategy strategy, @NonNull BigInteger feeReserve) {
        if (feeReserve.signum() < 0)
            throw new IllegalArgumentException("feeReserve must be >= 0");
        this.strategy = strategy;
        this.feeReserve = feeReserve;
    }

    @Override
    public void apply(TxBuilderContext context, Transaction transaction) {
        List<TransactionOutput> outputs = new ArrayList<>(transaction.getBody().getOutputs());
        List<TransactionOutput> appended = new ArrayList<>();

        for (int i = 0; i < outputs.size(); i++) {
            TransactionOutput output = outputs.get(i);
            if (isSplittable(output))
                splitInPlace(context, transaction, outputs, i, appended);
        }

        if (!appended.isEmpty()) {
            outputs.addAll(appended);
            transaction.getBody().setOutputs(outputs);
        }
    }

    /**
     * Replace the output at the given index with the fee bearing piece and collect the other pieces in appended.
     */
    private void splitInPlace(TxBuilderContext context, Transaction transaction, List<TransactionOutput> outputs,
                              int index, List<TransactionOutput> appended) {
        TransactionOutput output = outputs.get(index);
        List<Value> pieces = split(context, transaction, output);
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
    private List<Value> split(TxBuilderContext context, Transaction transaction, TransactionOutput output) {
        Value value = output.getValue();
        if (value.getCoin().compareTo(feeReserve) <= 0)
            return List.of(value);

        ChangeSplitRequest request = ChangeSplitRequest.builder()
                .address(output.getAddress())
                .change(new Value(value.getCoin().subtract(feeReserve), value.getMultiAssets()))
                .protocolParams(context.getProtocolParams())
                .transaction(transaction)
                .utxoSupplier(context.getUtxoSupplier())
                .build();

        List<Value> pieces = strategy.split(request);
        if (pieces == null || pieces.isEmpty())
            throw new IllegalStateException(strategyName() + " returned no change pieces");
        if (pieces.size() == 1)
            return List.of(value);

        verify(request, pieces);

        int largest = 0;
        for (int i = 1; i < pieces.size(); i++) {
            if (pieces.get(i).getCoin().compareTo(pieces.get(largest).getCoin()) > 0)
                largest = i;
        }

        List<Value> result = new ArrayList<>(pieces.size());
        Value feePiece = pieces.get(largest);
        result.add(new Value(feePiece.getCoin().add(feeReserve), feePiece.getMultiAssets()));
        for (int i = 0; i < pieces.size(); i++) {
            if (i != largest)
                result.add(pieces.get(i));
        }
        return result;
    }

    private void verify(ChangeSplitRequest request, List<Value> pieces) {
        Map<String, BigInteger> expected = ChangeValues.toUnitMap(request.getChange());
        Map<String, BigInteger> actual = ChangeValues.sumUnits(pieces);
        if (!expected.equals(actual))
            throw new IllegalStateException(strategyName() + " value mismatch. Change: " + expected
                    + ", sum of pieces: " + actual);

        for (Value piece : pieces) {
            if (piece.getCoin() == null || piece.getCoin().compareTo(request.minAda(piece)) < 0)
                throw new IllegalStateException(strategyName() + " created a piece below min-ada: " + piece);
        }
    }

    private String strategyName() {
        return strategy.getClass().getSimpleName();
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
}
