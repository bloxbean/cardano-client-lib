package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Wallet-aware strategy: keeps {@code targetLanes} ADA-only UTxOs of at least {@code laneAmount} at the change address.
 * <p>
 * It counts the existing lanes (ADA-only UTxOs of at least {@code laneAmount} not spent by this transaction) and splits
 * the change into only as many lanes as are missing. The last lane takes the remainder. When the wallet already has
 * enough lanes, ADA stays in one output, so the UTxO count doesn't keep growing.
 * <p>
 * Needs a {@link com.bloxbean.cardano.client.api.UtxoSupplier}; without one no lanes are counted. It loads all UTxOs
 * of the change address, which can be slow for large wallets.
 */
@Getter
@ToString
public class TargetShapeStrategy extends AbstractChangeSplitStrategy {
    public static final int DEFAULT_TARGET_LANES = 5;
    public static final BigInteger DEFAULT_LANE_AMOUNT = BigInteger.valueOf(10_000_000L);

    private final int targetLanes;
    private final BigInteger laneAmount;

    public TargetShapeStrategy() {
        this(null, null, null);
    }

    /**
     * @param targetLanes   wanted number of ADA-only UTxOs at the change address. Default: 5
     * @param laneAmount    min lovelace of a lane, raised to min-ada if lower. Default: 10 ADA
     * @param tokenBundling token bundling. Default: {@link ByteBudgetBundling}
     */
    @Builder
    public TargetShapeStrategy(Integer targetLanes, BigInteger laneAmount, TokenBundlingStrategy tokenBundling) {
        super(tokenBundling);
        this.targetLanes = targetLanes != null ? targetLanes : DEFAULT_TARGET_LANES;
        this.laneAmount = laneAmount != null ? laneAmount : DEFAULT_LANE_AMOUNT;

        if (this.targetLanes < 1)
            throw new IllegalArgumentException("targetLanes must be >= 1");
        if (this.laneAmount.signum() < 0)
            throw new IllegalArgumentException("laneAmount must be >= 0");
    }

    @Override
    protected List<BigInteger> splitAda(BigInteger lovelace, ChangeSplitRequest request) {
        BigInteger laneFloor = laneAmount.max(request.adaOnlyMinAda());
        int missing = targetLanes - existingLanes(request, laneFloor);
        long lanes = Math.min(missing, lovelace.divide(laneFloor).min(BigInteger.valueOf(targetLanes)).longValueExact());
        if (lanes <= 1)
            return List.of(lovelace);

        List<BigInteger> amounts = new ArrayList<>((int) lanes);
        for (int i = 0; i < lanes - 1; i++)
            amounts.add(laneFloor);
        amounts.add(lovelace.subtract(laneFloor.multiply(BigInteger.valueOf(lanes - 1))));
        return amounts;
    }

    private static int existingLanes(ChangeSplitRequest request, BigInteger laneFloor) {
        if (request.getUtxoSupplier() == null)
            return 0;

        Set<String> spent = new HashSet<>();
        if (request.getTransaction() != null && request.getTransaction().getBody() != null
                && request.getTransaction().getBody().getInputs() != null) {
            for (TransactionInput input : request.getTransaction().getBody().getInputs())
                spent.add(input.getTransactionId() + "#" + input.getIndex());
        }

        List<Utxo> utxos = request.getUtxoSupplier().getAll(request.getAddress());
        if (utxos == null)
            return 0;

        int count = 0;
        for (Utxo utxo : utxos) {
            if (!spent.contains(utxo.getTxHash() + "#" + utxo.getOutputIndex()) && isLane(utxo, laneFloor))
                count++;
        }
        return count;
    }

    private static boolean isLane(Utxo utxo, BigInteger laneFloor) {
        List<Amount> amounts = utxo.getAmount();
        if (amounts == null || amounts.size() != 1 || !LOVELACE.equals(amounts.get(0).getUnit()))
            return false;
        if (utxo.getDataHash() != null || utxo.getInlineDatum() != null || utxo.getReferenceScriptHash() != null)
            return false;
        return amounts.get(0).getQuantity().compareTo(laneFloor) >= 0;
    }
}
