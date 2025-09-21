package com.bloxbean.cardano.client.quicktx.helper;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.NonNull;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Utility class providing list-based predicates for ScriptTx UTXO selection.
 *
 * This class offers predicates that operate on lists of UTXOs to perform
 * complex selection patterns like selecting top N by value, filtering with
 * limits, or selecting UTXOs until a target value is reached.
 *
 * The predicates returned implement both {@code Predicate<List<Utxo>>} for compatibility
 * with ScriptTx.collectFromList(), and also provide access to the transformed
 * result through the SelectingPredicate interface.
 *
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Select top 3 UTXOs by lovelace value
 * SelectingPredicate<List<Utxo>> topByValue = ListPredicates.selectTop(3,
 *     Comparator.comparing(utxo -> getLovelaceAmount(utxo)).reversed());
 *
 * // Select UTXOs until target value reached
 * SelectingPredicate<List<Utxo>> targetValue = ListPredicates.selectByTotalValue(
 *     BigInteger.valueOf(50_000_000));
 *
 * // Use in ScriptTx (future enhancement needed in ScriptTx for proper support)
 * ScriptTx scriptTx = new ScriptTx()
 *     .collectFromList(scriptAddress, topByValue, redeemer, datum)
 *     .payToAddress(receiver, amount);
 *
 * // Or get transformed result directly for testing/other uses
 * List<Utxo> selectedUtxos = topByValue.select(allUtxos);
 * }</pre>
 *
 */
public final class ListPredicates {

    /**
     * Private constructor to prevent instantiation.
     */
    private ListPredicates() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Interface for predicates that can both test and select/transform lists.
     */
    public interface SelectingPredicate<T> extends Predicate<T> {
        /**
         * Apply the selection logic and return the transformed result.
         *
         * @param input The input to transform
         * @return The selected/transformed result
         */
        T select(T input);
    }

    /**
     * Creates a predicate that selects the top N UTXOs based on the provided comparator.
     *
     * @param n Number of UTXOs to select
     * @param comparator Comparator to determine ordering
     * @return SelectingPredicate that can select top N UTXOs
     * @throws IllegalArgumentException if n is negative
     */
    public static SelectingPredicate<List<Utxo>> selectTop(int n, @NonNull Comparator<Utxo> comparator) {
        if (n < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }
        Objects.requireNonNull(comparator, "Comparator cannot be null");

        return new SelectingPredicate<List<Utxo>>() {
            @Override
            public List<Utxo> select(List<Utxo> utxos) {
                return utxos.stream()
                        .sorted(comparator)
                        .limit(n)
                        .collect(Collectors.toList());
            }

            @Override
            public boolean test(List<Utxo> utxos) {
                // For ScriptTx compatibility - return true if we have UTXOs to select from
                return !utxos.isEmpty();
            }
        };
    }

    /**
     * Creates a predicate that selects UTXOs matching the filter, up to maxCount.
     *
     * @param filter Predicate to filter UTXOs
     * @param maxCount Maximum number of UTXOs to select
     * @return Predicate that transforms list to contain filtered UTXOs
     * @throws IllegalArgumentException if maxCount is negative
     * @throws NullPointerException if filter is null
     */
    public static SelectingPredicate<List<Utxo>> selectWhere(@NonNull Predicate<Utxo> filter, int maxCount) {
        if (maxCount < 0) {
            throw new IllegalArgumentException("Max count cannot be negative");
        }
        Objects.requireNonNull(filter, "Filter cannot be null");

        return new SelectingPredicate<List<Utxo>>() {
            @Override
            public List<Utxo> select(List<Utxo> utxos) {
                return utxos.stream()
                        .filter(filter)
                        .limit(maxCount)
                        .collect(Collectors.toList());
            }

            @Override
            public boolean test(List<Utxo> utxos) {
                return utxos.stream().anyMatch(filter);
            }
        };
    }

    /**
     * Creates a predicate that selects UTXOs until the total lovelace value
     * reaches or exceeds the target amount.
     *
     * @param targetAmount Target lovelace amount
     * @return Predicate that selects UTXOs until target value is reached
     * @throws IllegalArgumentException if targetAmount is negative
     * @throws NullPointerException if targetAmount is null
     */
    public static SelectingPredicate<List<Utxo>> selectByTotalValue(@NonNull BigInteger targetAmount) {
        Objects.requireNonNull(targetAmount, "Target amount cannot be null");
        if (targetAmount.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Target amount cannot be negative");
        }

        return new SelectingPredicate<List<Utxo>>() {
            @Override
            public List<Utxo> select(List<Utxo> utxos) {
                List<Utxo> selected = new ArrayList<>();
                BigInteger total = BigInteger.ZERO;

                for (Utxo utxo : utxos) {
                    selected.add(utxo);
                    total = total.add(getLovelaceAmount(utxo));

                    if (total.compareTo(targetAmount) >= 0) {
                        return selected;
                    }
                }

                // If we couldn't reach target amount, return empty list
                return new ArrayList<>();
            }

            @Override
            public boolean test(List<Utxo> utxos) {
                BigInteger total = utxos.stream()
                    .map(ListPredicates::getLovelaceAmount)
                    .reduce(BigInteger.ZERO, BigInteger::add);
                return total.compareTo(targetAmount) >= 0;
            }
        };
    }

    /**
     * Creates a predicate that selects UTXOs until the total asset value
     * reaches or exceeds the target amount for a specific native asset.
     *
     * @param policyId Policy ID of the asset
     * @param assetName Asset name
     * @param targetAmount Target asset amount
     * @return Predicate that selects UTXOs until target asset value is reached
     * @throws IllegalArgumentException if targetAmount is negative
     * @throws NullPointerException if any parameter is null
     */
    public static SelectingPredicate<List<Utxo>> selectByTotalAssetValue(@NonNull String policyId,
                                                               @NonNull String assetName,
                                                               @NonNull BigInteger targetAmount) {
        Objects.requireNonNull(policyId, "Policy ID cannot be null");
        Objects.requireNonNull(assetName, "Asset name cannot be null");
        Objects.requireNonNull(targetAmount, "Target amount cannot be null");
        if (targetAmount.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Target amount cannot be negative");
        }

        return new SelectingPredicate<List<Utxo>>() {
            @Override
            public List<Utxo> select(List<Utxo> utxos) {
                List<Utxo> selected = new ArrayList<>();
                BigInteger total = BigInteger.ZERO;

                for (Utxo utxo : utxos) {
                    BigInteger assetAmount = getAssetQuantity(utxo, policyId, assetName);
                    if (assetAmount.compareTo(BigInteger.ZERO) > 0) {
                        selected.add(utxo);
                        total = total.add(assetAmount);

                        if (total.compareTo(targetAmount) >= 0) {
                            return selected;
                        }
                    }
                }

                // If we couldn't reach target amount, return empty list
                return new ArrayList<>();
            }

            @Override
            public boolean test(List<Utxo> utxos) {
                BigInteger total = BigInteger.ZERO;
                for (Utxo utxo : utxos) {
                    total = total.add(getAssetQuantity(utxo, policyId, assetName));
                }
                return total.compareTo(targetAmount) >= 0;
            }
        };
    }

    /**
     * Creates a predicate that returns all UTXOs unchanged.
     * Useful as a pass-through or base case.
     *
     * @return Predicate that returns the complete list
     */
    public static SelectingPredicate<List<Utxo>> selectAll() {
        return new SelectingPredicate<List<Utxo>>() {
            @Override
            public List<Utxo> select(List<Utxo> utxos) {
                return new ArrayList<>(utxos);
            }

            @Override
            public boolean test(List<Utxo> utxos) {
                return true;
            }
        };
    }

    /**
     * Creates a predicate that selects the N oldest UTXOs based on output index.
     * Lower output indices are considered older.
     *
     * @param n Number of oldest UTXOs to select
     * @return Predicate that selects oldest UTXOs
     * @throws IllegalArgumentException if n is negative
     */
    public static SelectingPredicate<List<Utxo>> selectOldestFirst(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }

        return selectTop(n, Comparator.comparing(Utxo::getOutputIndex));
    }

    /**
     * Creates a predicate that selects the N newest UTXOs based on output index.
     * Higher output indices are considered newer.
     *
     * @param n Number of newest UTXOs to select
     * @return Predicate that selects newest UTXOs
     * @throws IllegalArgumentException if n is negative
     */
    public static SelectingPredicate<List<Utxo>> selectNewestFirst(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }

        return selectTop(n, Comparator.comparing(Utxo::getOutputIndex).reversed());
    }

    /**
     * Creates a predicate that randomly selects N UTXOs using a deterministic seed.
     * This ensures reproducible results for the same input and seed.
     *
     * @param n Number of UTXOs to select randomly
     * @param seed Random seed for deterministic selection
     * @return Predicate that randomly selects UTXOs
     * @throws IllegalArgumentException if n is negative
     */
    public static SelectingPredicate<List<Utxo>> selectRandom(int n, long seed) {
        if (n < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }

        return new SelectingPredicate<List<Utxo>>() {
            @Override
            public List<Utxo> select(List<Utxo> utxos) {
                if (utxos.isEmpty() || n == 0) {
                    return new ArrayList<>();
                }

                List<Utxo> shuffled = new ArrayList<>(utxos);
                Collections.shuffle(shuffled, new Random(seed));

                return shuffled.stream()
                        .limit(n)
                        .collect(Collectors.toList());
            }

            @Override
            public boolean test(List<Utxo> utxos) {
                return utxos.size() >= n;
            }
        };
    }

    /**
     * Creates a predicate that selects UTXOs with the highest asset diversity.
     * Prioritizes UTXOs that contain multiple different assets.
     *
     * @param n Number of UTXOs to select
     * @return Predicate that selects UTXOs with most diverse assets
     * @throws IllegalArgumentException if n is negative
     */
    public static SelectingPredicate<List<Utxo>> selectMostDiverse(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }

        return selectTop(n, Comparator.comparing(ListPredicates::getAssetCount).reversed());
    }

    // Helper methods

    /**
     * Helper method to extract lovelace amount from a UTXO.
     *
     * @param utxo The UTXO to extract lovelace from
     * @return Lovelace amount, or zero if not found
     */
    private static BigInteger getLovelaceAmount(@NonNull Utxo utxo) {
        if (utxo.getAmount() == null) {
            return BigInteger.ZERO;
        }

        return utxo.getAmount().stream()
                .filter(amount -> LOVELACE.equals(amount.getUnit()))
                .map(Amount::getQuantity)
                .findFirst()
                .orElse(BigInteger.ZERO);
    }

    /**
     * Helper method to get asset quantity from UTXO for specific policy/asset.
     *
     * @param utxo The UTXO to check
     * @param policyId Policy ID of the asset
     * @param assetName Asset name
     * @return Asset quantity, or zero if not found
     */
    private static BigInteger getAssetQuantity(@NonNull Utxo utxo,
                                             @NonNull String policyId,
                                             @NonNull String assetName) {
        if (utxo.getAmount() == null) {
            return BigInteger.ZERO;
        }

        return utxo.getAmount().stream()
                .filter(amount -> !LOVELACE.equals(amount.getUnit()))
                .filter(amount -> {
                    try {
                        Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(amount.getUnit());
                        return policyId.equals(policyAssetName._1) &&
                               assetNamesMatch(assetName, policyAssetName._2);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(Amount::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    /**
     * Helper method to compare asset names, handling hex encoding.
     * Reuses the same logic as ScriptPredicates.
     */
    private static boolean assetNamesMatch(String expectedAssetName, String parsedAssetName) {
        if (expectedAssetName == null) {
            return true; // null means match any asset
        }

        // Direct comparison first
        if (Objects.equals(expectedAssetName, parsedAssetName)) {
            return true;
        }

        // Try comparing without 0x prefix if parsedAssetName starts with 0x
        if (parsedAssetName != null && parsedAssetName.startsWith("0x")) {
            try {
                String decodedParsed = new String(com.bloxbean.cardano.client.util.HexUtil.decodeHexString(parsedAssetName.substring(2)));
                if (Objects.equals(expectedAssetName, decodedParsed)) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore hex decoding errors
            }
        }

        return false;
    }

    /**
     * Helper method to count number of different assets in a UTXO.
     *
     * @param utxo The UTXO to analyze
     * @return Number of different assets (excluding lovelace)
     */
    private static int getAssetCount(@NonNull Utxo utxo) {
        if (utxo.getAmount() == null) {
            return 0;
        }

        return (int) utxo.getAmount().stream()
                .filter(amount -> !LOVELACE.equals(amount.getUnit()))
                .count();
    }
}
