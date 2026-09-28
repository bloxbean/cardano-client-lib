package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.model.Amount;

import java.math.BigInteger;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Whether {@link Unfrack} merges extra small UTxOs of the change address into a transaction. Part of
 * {@link WalletShape}.
 *
 * @param maxExtraInputs  max number of UTxOs added per transaction; 0 disables consolidation
 * @param maxUtxoLovelace only UTxOs with at most this much lovelace are added
 */
public record Consolidation(int maxExtraInputs, BigInteger maxUtxoLovelace) {
    public static final BigInteger DEFAULT_MAX_UTXO_LOVELACE = BigInteger.valueOf(5_000_000L);

    public Consolidation {
        if (maxExtraInputs < 0)
            throw new IllegalArgumentException("maxExtraInputs must be >= 0");
        Objects.requireNonNull(maxUtxoLovelace, "maxUtxoLovelace");
        if (maxUtxoLovelace.signum() < 0)
            throw new IllegalArgumentException("maxUtxoLovelace must be >= 0");
    }

    /**
     * No consolidation.
     */
    public static Consolidation none() {
        return new Consolidation(0, BigInteger.ZERO);
    }

    /**
     * Add up to {@code maxExtraInputs} UTxOs of at most 5 ADA per transaction, UTxOs with tokens first.
     */
    public static Consolidation opportunistic(int maxExtraInputs) {
        return new Consolidation(maxExtraInputs, DEFAULT_MAX_UTXO_LOVELACE);
    }

    /**
     * Add up to {@code maxExtraInputs} UTxOs of at most {@code maxUtxoAmount} per transaction, UTxOs with tokens first.
     */
    public static Consolidation opportunistic(int maxExtraInputs, Amount maxUtxoAmount) {
        Objects.requireNonNull(maxUtxoAmount, "maxUtxoAmount");
        if (!LOVELACE.equals(maxUtxoAmount.getUnit()))
            throw new IllegalArgumentException("Amount must be in lovelace: " + maxUtxoAmount.getUnit());
        return new Consolidation(maxExtraInputs, maxUtxoAmount.getQuantity());
    }

    public boolean isEnabled() {
        return maxExtraInputs > 0;
    }
}
