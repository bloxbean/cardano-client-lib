package com.bloxbean.cardano.client.function.walletshape;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.transaction.spec.ChangeOutput;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
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
 * Default wallet shape for retail users and dApps: keeps the wallet able to transact and avoids paying fees for tokens
 * that aren't used.
 * <ul>
 *     <li>Tokens in the change are grouped into bundles of at most 1,000 bytes, each holding only its min-ada.</li>
 *     <li>The rest of the ADA stays in one ADA-only output.</li>
 *     <li>{@link #withConsolidation()} also merges up to 3 small UTxOs (at most 5 ADA each) of the sender into the
 *     transaction: ADA dust, and token fragments when there are at least two. Meant for wallets building their own
 *     transactions.</li>
 * </ul>
 * Only {@link ChangeOutput}s without datum or script reference are changed. The change is shaped as
 * {@code change - feeReserve}; the reserve is added to the largest piece, which stays at the index of the original
 * change output (the fee is deducted from it during balancing), and the other pieces are appended.
 *
 * <pre>{@code
 * quickTxBuilder.compose(tx)
 *     .preBalanceTx(new DefaultWalletShaper())
 *     .withSigner(signer)
 *     .completeAndWait();
 * }</pre>
 */
@Slf4j
@Getter
public class DefaultWalletShaper implements WalletShaper {
    public static final BigInteger DEFAULT_FEE_RESERVE = BigInteger.valueOf(2_000_000L);
    public static final int CONSOLIDATION_MAX_INPUTS = 3;
    public static final BigInteger CONSOLIDATION_MAX_UTXO_LOVELACE = BigInteger.valueOf(5_000_000L);

    private static final BigInteger ONE_ADA = BigInteger.valueOf(1_000_000L);

    private final BigInteger feeReserve;
    private final boolean consolidating;
    private final ByteBudgetBundling bundling = new ByteBudgetBundling();

    public DefaultWalletShaper() {
        this(DEFAULT_FEE_RESERVE);
    }

    /**
     * @param feeReserve lovelace kept on the largest piece to pay the fee, so the other pieces still meet min-ada
     *                   after balancing
     */
    public DefaultWalletShaper(@NonNull BigInteger feeReserve) {
        this(feeReserve, false);
    }

    private DefaultWalletShaper(BigInteger feeReserve, boolean consolidating) {
        if (feeReserve.signum() < 0)
            throw new IllegalArgumentException("feeReserve must be >= 0");
        this.feeReserve = feeReserve;
        this.consolidating = consolidating;
    }

    /**
     * Default shape that also merges up to 3 small UTxOs of the sender into each transaction.
     */
    public static DefaultWalletShaper withConsolidation() {
        return withConsolidation(DEFAULT_FEE_RESERVE);
    }

    public static DefaultWalletShaper withConsolidation(@NonNull BigInteger feeReserve) {
        return new DefaultWalletShaper(feeReserve, true);
    }

    @Override
    public void apply(TxBuilderContext context, Transaction transaction) {
        List<TransactionOutput> outputs = new ArrayList<>(transaction.getBody().getOutputs());
        if (outputs.stream().noneMatch(ChangeOutput.class::isInstance)) {
            if (context.isMergeChange())
                log.warn("No change output to shape: the change was probably merged into another output " +
                        "(mergeOutputs is true). Wallet shaping is skipped.");
            return;
        }

        if (consolidating)
            consolidate(context, transaction);

        MinAda minAda = new MinAda(context.getProtocolParams());
        List<TransactionOutput> appended = new ArrayList<>();
        for (int i = 0; i < outputs.size(); i++) {
            if (isShapeable(outputs.get(i)))
                shapeInPlace(minAda, outputs, i, appended);
        }

        if (!appended.isEmpty()) {
            outputs.addAll(appended);
            transaction.getBody().setOutputs(outputs);
        }
    }

    /**
     * Replace the change output at the given index with the fee bearing piece and collect the other pieces.
     */
    private void shapeInPlace(MinAda minAda, List<TransactionOutput> outputs, int index,
                              List<TransactionOutput> appended) {
        TransactionOutput output = outputs.get(index);
        List<Value> pieces = shape(minAda, output);
        if (pieces.size() <= 1)
            return;

        outputs.set(index, new ChangeOutput(output.getAddress(), pieces.get(0)));
        for (Value piece : pieces.subList(1, pieces.size()))
            appended.add(new ChangeOutput(output.getAddress(), piece));

        log.debug("Wallet shape: change at {} split into {} outputs", output.getAddress(), pieces.size());
    }

    /**
     * Shape a change output. The first returned value is the fee bearing piece (largest lovelace amount).
     */
    private List<Value> shape(MinAda minAda, TransactionOutput output) {
        Value value = output.getValue();
        if (value.getCoin().compareTo(feeReserve) <= 0)
            return List.of(value);

        Value change = new Value(value.getCoin().subtract(feeReserve), value.getMultiAssets());
        List<Value> pieces = split(minAda, output.getAddress(), change);
        if (pieces.size() <= 1)
            return List.of(value);

        verify(minAda, output.getAddress(), change, pieces);

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

    /**
     * Token bundles at their min-ada plus one ADA-only piece. A single piece means "leave the change as it is".
     */
    List<Value> split(MinAda minAda, String address, Value change) {
        BigInteger lovelace = ChangeValues.coinOf(change);
        List<MultiAsset> tokens = ChangeValues.nonEmptyPolicies(change.getMultiAssets());
        if (lovelace.signum() <= 0 || tokens.isEmpty() || ChangeValues.hasNegativeAsset(tokens))
            return List.of(change);

        List<Value> result = new ArrayList<>();
        BigInteger remaining = lovelace;
        for (List<MultiAsset> bundle : bundling.bundle(tokens)) {
            // placeholder coin of realistic CBOR size
            BigInteger bundleMinAda = minAda.of(address, new Value(ONE_ADA, bundle));
            result.add(new Value(bundleMinAda, bundle));
            remaining = remaining.subtract(bundleMinAda);
        }

        if (remaining.signum() < 0)
            return List.of(change);

        if (remaining.compareTo(minAda.of(address, Value.fromCoin(ONE_ADA))) >= 0) {
            result.add(Value.fromCoin(remaining));
        } else if (remaining.signum() > 0) {
            int last = result.size() - 1;
            result.set(last, new Value(result.get(last).getCoin().add(remaining), result.get(last).getMultiAssets()));
        }
        return result.size() <= 1 ? List.of(change) : result;
    }

    private static void verify(MinAda minAda, String address, Value change, List<Value> pieces) {
        Map<String, BigInteger> expected = ChangeValues.toUnitMap(change);
        Map<String, BigInteger> actual = ChangeValues.sumUnits(pieces);
        if (!expected.equals(actual))
            throw new IllegalStateException("Wallet shape value mismatch. Change: " + expected + ", pieces: " + actual);
        for (Value piece : pieces) {
            if (piece.getCoin().compareTo(minAda.of(address, piece)) < 0)
                throw new IllegalStateException("Wallet shape created a piece below min-ada: " + piece);
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
            if (!isShapeable(output) || !addresses.add(output.getAddress()))
                continue;

            List<Utxo> utxos = context.getUtxoSupplier().getAll(output.getAddress());
            // Only addresses already spent from, so no new signer is needed
            if (utxos == null || utxos.stream().noneMatch(u -> inputIds.contains(id(u))))
                continue;

            Value value = output.getValue();
            for (Utxo utxo : candidates(utxos, inputIds)) {
                inputs.add(new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()));
                inputIds.add(id(utxo));
                value = value.add(utxo.toValue());
                added++;
            }
            output.setValue(value);
        }

        if (added > 0) {
            transaction.getBody().setInputs(inputs);
            log.debug("Wallet shape: consolidated {} UTxOs", added);
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
                .filter(u -> hasTokens(u) && bundling.isFragment(u.toValue().getMultiAssets()))
                .toList();
        List<Utxo> candidates = new ArrayList<>(small.stream().filter(u -> !hasTokens(u)).toList());
        if (tokenFragments.size() >= 2)
            candidates.addAll(tokenFragments);

        return candidates.stream()
                .sorted(Comparator.comparing((Utxo u) -> !hasTokens(u))
                        .thenComparing(DefaultWalletShaper::lovelaceOf)
                        .thenComparing(Utxo::getTxHash)
                        .thenComparingInt(Utxo::getOutputIndex))
                .limit(CONSOLIDATION_MAX_INPUTS)
                .toList();
    }

    private static boolean isConsolidatable(Utxo utxo) {
        return utxo.getDataHash() == null && utxo.getInlineDatum() == null && utxo.getReferenceScriptHash() == null
                && lovelaceOf(utxo).compareTo(CONSOLIDATION_MAX_UTXO_LOVELACE) <= 0;
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

    private static boolean isShapeable(TransactionOutput output) {
        return output instanceof ChangeOutput
                && output.getValue() != null
                && output.getValue().getCoin() != null
                && output.getValue().getCoin().signum() > 0
                && output.getDatumHash() == null
                && output.getInlineDatum() == null
                && output.getScriptRef() == null;
    }

    /**
     * Min-ada of an output with a given value at an address.
     */
    static final class MinAda {
        private final MinAdaCalculator calculator;

        MinAda(ProtocolParams protocolParams) {
            this.calculator = new MinAdaCalculator(protocolParams);
        }

        BigInteger of(String address, Value value) {
            return calculator.calculateMinAda(new TransactionOutput(address, value));
        }
    }
}
