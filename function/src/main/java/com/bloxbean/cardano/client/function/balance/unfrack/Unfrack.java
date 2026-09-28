package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Pre-balance {@link TxBuilder} which "unfracks" the wallet in the same transaction: it optionally merges small UTxOs
 * ({@link Consolidation}) and splits change into outputs that match a {@link WalletShape} (token bundles plus ADA-only
 * outputs), so that change stays below max value size and payments don't move every token in the wallet.
 * <p>
 * Default shape is {@link WalletShape#hygiene()}. A custom {@link ChangeSplitStrategy} (e.g. {@link EvolutionStrategy})
 * can be used instead of a shape.
 * <p>
 * Only {@link ChangeOutput}s without datum or script reference are changed. Consolidation only adds UTxOs of an address
 * that already has an input in the transaction: small ADA-only UTxOs and token fragments (not full bundles, see
 * {@link TokenBundlingStrategy#isFragment(List)}), and is skipped for transactions with redeemers. The strategy splits
 * {@code change - feeReserve}; the reserve is then added to the largest piece, which stays at the index of the original
 * change output (the fee is deducted from it during balancing). The other pieces are appended at the end, so indexes
 * of all other outputs are preserved. The strategy result is verified: pieces must sum up to the change and each piece
 * must meet min-ada, otherwise an {@link IllegalStateException} is thrown.
 *
 * <pre>{@code
 * quickTxBuilder.compose(tx)
 *     .feePayer(sender)
 *     .preBalanceTx(new Unfrack(WalletShape.throughput(10, Amount.ada(60))))
 *     .withSigner(signer)
 *     .completeAndWait();
 * }</pre>
 */
@Slf4j
@Getter
public class Unfrack implements TxBuilder {
    public static final BigInteger DEFAULT_FEE_RESERVE = BigInteger.valueOf(2_000_000L);

    private final ChangeSplitStrategy strategy;
    private final Consolidation consolidation;
    private final TokenBundlingStrategy tokenBundling;
    private final BigInteger feeReserve;

    public Unfrack() {
        this(WalletShape.hygiene());
    }

    public Unfrack(WalletShape shape) {
        this(shape, DEFAULT_FEE_RESERVE);
    }

    /**
     * @param shape      target wallet shape
     * @param feeReserve lovelace kept on the largest piece to pay the fee, so the other pieces still meet min-ada
     *                   after balancing
     */
    public Unfrack(@NonNull WalletShape shape, @NonNull BigInteger feeReserve) {
        this(new WalletShapeStrategy(shape), shape.getConsolidation(), shape.getTokens(), feeReserve);
    }

    public Unfrack(ChangeSplitStrategy strategy) {
        this(strategy, DEFAULT_FEE_RESERVE);
    }

    /**
     * @param strategy   custom change split strategy; no consolidation
     * @param feeReserve lovelace kept on the largest piece to pay the fee
     */
    public Unfrack(@NonNull ChangeSplitStrategy strategy, @NonNull BigInteger feeReserve) {
        this(strategy, Consolidation.none(), null, feeReserve);
    }

    private Unfrack(@NonNull ChangeSplitStrategy strategy, @NonNull Consolidation consolidation,
                    TokenBundlingStrategy tokenBundling, @NonNull BigInteger feeReserve) {
        if (feeReserve.signum() < 0)
            throw new IllegalArgumentException("feeReserve must be >= 0");
        this.strategy = strategy;
        this.consolidation = consolidation;
        this.tokenBundling = tokenBundling;
        this.feeReserve = feeReserve;
    }

    @Override
    public void apply(TxBuilderContext context, Transaction transaction) {
        if (consolidation.isEnabled())
            consolidate(context, transaction);

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
     * Add small UTxOs of each change address as inputs and their value to its first change output.
     */
    private void consolidate(TxBuilderContext context, Transaction transaction) {
        if (context.getUtxoSupplier() == null || hasRedeemers(transaction))
            return;

        List<TransactionInput> inputs = transaction.getBody().getInputs() != null
                ? new ArrayList<>(transaction.getBody().getInputs()) : new ArrayList<>();
        Set<String> inputIds = new HashSet<>();
        for (TransactionInput input : inputs)
            inputIds.add(input.getTransactionId() + "#" + input.getIndex());

        Set<String> addresses = new HashSet<>();
        int added = 0;
        for (TransactionOutput output : transaction.getBody().getOutputs()) {
            if (!isSplittable(output) || !addresses.add(output.getAddress()))
                continue;

            List<Utxo> utxos = context.getUtxoSupplier().getAll(output.getAddress());
            // Only addresses already spent from, so no new signer is needed
            if (utxos == null || utxos.stream().noneMatch(u -> inputIds.contains(id(u))))
                continue;

            List<Utxo> candidates = candidates(utxos, inputIds);

            Value value = output.getValue();
            for (Utxo utxo : candidates) {
                inputs.add(new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()));
                inputIds.add(id(utxo));
                value = value.add(utxo.toValue());
                added++;
            }
            output.setValue(value);
        }

        if (added > 0) {
            transaction.getBody().setInputs(inputs);
            log.debug("Unfrack: consolidated {} UTxOs", added);
        }
    }

    /**
     * Small ADA-only UTxOs, and token fragments when there are at least two of them (one fragment alone would only be
     * re-bundled). Token fragments first, then smallest first.
     */
    private List<Utxo> candidates(List<Utxo> utxos, Set<String> inputIds) {
        List<Utxo> small = utxos.stream()
                .filter(u -> !inputIds.contains(id(u)) && isConsolidatable(u))
                .toList();
        List<Utxo> tokenFragments = small.stream()
                .filter(u -> hasTokens(u) && (tokenBundling == null || tokenBundling.isFragment(u.toValue().getMultiAssets())))
                .toList();
        List<Utxo> candidates = new ArrayList<>(small.stream().filter(u -> !hasTokens(u)).toList());
        if (tokenFragments.size() >= 2)
            candidates.addAll(tokenFragments);

        return candidates.stream()
                .sorted(Comparator.comparing((Utxo u) -> !hasTokens(u))
                        .thenComparing(Unfrack::lovelaceOf)
                        .thenComparing(Utxo::getTxHash)
                        .thenComparingInt(Utxo::getOutputIndex))
                .limit(consolidation.maxExtraInputs())
                .toList();
    }

    private boolean isConsolidatable(Utxo utxo) {
        return utxo.getDataHash() == null && utxo.getInlineDatum() == null && utxo.getReferenceScriptHash() == null
                && lovelaceOf(utxo).compareTo(consolidation.maxUtxoLovelace()) <= 0;
    }

    private static boolean hasRedeemers(Transaction transaction) {
        return transaction.getWitnessSet() != null && transaction.getWitnessSet().getRedeemers() != null
                && !transaction.getWitnessSet().getRedeemers().isEmpty();
    }

    private static boolean hasTokens(Utxo utxo) {
        return utxo.getAmount() != null && utxo.getAmount().stream().anyMatch(a -> !LOVELACE.equals(a.getUnit()));
    }

    private static BigInteger lovelaceOf(Utxo utxo) {
        if (utxo.getAmount() == null)
            return BigInteger.ZERO;
        return utxo.getAmount().stream()
                .filter(a -> LOVELACE.equals(a.getUnit()))
                .map(Amount::getQuantity)
                .findFirst()
                .orElse(BigInteger.ZERO);
    }

    private static String id(Utxo utxo) {
        return utxo.getTxHash() + "#" + utxo.getOutputIndex();
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
