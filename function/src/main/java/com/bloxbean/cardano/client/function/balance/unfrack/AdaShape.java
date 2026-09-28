package com.bloxbean.cardano.client.function.balance.unfrack;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * How the ADA-only part of the change should be shaped. Part of {@link WalletShape}.
 */
public sealed interface AdaShape permits AdaShape.Lanes, AdaShape.Percentages, AdaShape.Single {

    /**
     * Split an ADA-only amount.
     *
     * @param lovelace amount to split, at least the ADA-only min-ada
     * @param request  split request
     * @return amounts which sum up to {@code lovelace}, each at least the ADA-only min-ada. One amount means
     * "do not split".
     */
    List<BigInteger> split(BigInteger lovelace, ChangeSplitRequest request);

    /**
     * Keep {@code count} ADA-only UTxOs of at least {@code laneSize} at the change address.
     */
    static AdaShape lanes(int count, Amount laneSize) {
        return new Lanes(count, lovelaceOf(laneSize));
    }

    /**
     * Split ADA by {@code percentages} when it is at least {@code threshold}. Doesn't read the wallet.
     */
    static AdaShape percentages(Amount threshold, Integer... percentages) {
        return new Percentages(lovelaceOf(threshold), Arrays.asList(percentages));
    }

    /**
     * Keep ADA in one output.
     */
    static AdaShape single() {
        return new Single();
    }

    private static BigInteger lovelaceOf(Amount amount) {
        Objects.requireNonNull(amount, "amount");
        if (!LOVELACE.equals(amount.getUnit()))
            throw new IllegalArgumentException("Amount must be in lovelace: " + amount.getUnit());
        return amount.getQuantity();
    }

    /**
     * Keeps {@code count} ADA-only UTxOs ("lanes") of at least {@code laneSize} at the change address and creates only
     * the lanes that are missing.
     * <p>
     * Existing lanes are ADA-only UTxOs of at least {@code laneSize}, without datum or script ref, that this transaction
     * doesn't spend. They are read from the {@link com.bloxbean.cardano.client.api.UtxoSupplier}; without one the ADA is
     * kept in a single output. {@code laneSize} below min-ada is raised to min-ada.
     */
    record Lanes(int count, BigInteger laneSize) implements AdaShape {
        public Lanes {
            if (count < 1)
                throw new IllegalArgumentException("count must be >= 1");
            Objects.requireNonNull(laneSize, "laneSize");
            if (laneSize.signum() < 0)
                throw new IllegalArgumentException("laneSize must be >= 0");
        }

        @Override
        public List<BigInteger> split(BigInteger lovelace, ChangeSplitRequest request) {
            if (request.getUtxoSupplier() == null)
                return List.of(lovelace);

            BigInteger laneFloor = laneSize.max(request.adaOnlyMinAda());
            int missing = count - existingLanes(request, laneFloor);
            long lanes = Math.min(missing, lovelace.divide(laneFloor).min(BigInteger.valueOf(count)).longValueExact());
            if (lanes <= 1)
                return List.of(lovelace);

            List<BigInteger> amounts = new ArrayList<>((int) lanes);
            for (int i = 0; i < lanes - 1; i++)
                amounts.add(laneFloor);
            amounts.add(lovelace.subtract(laneFloor.multiply(BigInteger.valueOf(lanes - 1))));
            return amounts;
        }

        private static int existingLanes(ChangeSplitRequest request, BigInteger laneFloor) {
            Set<String> spent = new HashSet<>();
            if (request.getTransaction() != null && request.getTransaction().getBody() != null
                    && request.getTransaction().getBody().getInputs() != null) {
                for (TransactionInput input : request.getTransaction().getBody().getInputs())
                    spent.add(input.getTransactionId() + "#" + input.getIndex());
            }

            List<Utxo> utxos = request.getUtxoSupplier().getAll(request.getAddress());
            if (utxos == null)
                return 0;

            int existing = 0;
            for (Utxo utxo : utxos) {
                if (!spent.contains(utxo.getTxHash() + "#" + utxo.getOutputIndex()) && isLane(utxo, laneFloor))
                    existing++;
            }
            return existing;
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

    /**
     * Splits ADA by {@code percentages} (last slice gets the remainder) when it is at least {@code threshold} and the
     * smallest slice meets min-ada; otherwise one output. Stateless: works without reading the wallet.
     */
    record Percentages(BigInteger threshold, List<Integer> percentages) implements AdaShape {
        public Percentages {
            Objects.requireNonNull(threshold, "threshold");
            if (threshold.signum() < 0)
                throw new IllegalArgumentException("threshold must be >= 0");
            PercentageSlices.validate(percentages);
            percentages = List.copyOf(percentages);
        }

        @Override
        public List<BigInteger> split(BigInteger lovelace, ChangeSplitRequest request) {
            if (lovelace.compareTo(threshold) < 0)
                return List.of(lovelace);
            if (PercentageSlices.smallestSlice(lovelace, percentages)
                    .compareTo(request.adaOnlyMinAda()) < 0)
                return List.of(lovelace);
            return PercentageSlices.split(lovelace, percentages);
        }
    }

    /**
     * Keeps ADA in one output.
     */
    record Single() implements AdaShape {
        @Override
        public List<BigInteger> split(BigInteger lovelace, ChangeSplitRequest request) {
            return List.of(lovelace);
        }
    }
}
