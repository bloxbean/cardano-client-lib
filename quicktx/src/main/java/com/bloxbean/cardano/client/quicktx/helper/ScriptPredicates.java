package com.bloxbean.cardano.client.quicktx.helper;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.NonNull;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Utility class providing common predicates for ScriptTx UTXO selection.
 *
 * This class offers a fluent API for building predicates to filter UTXOs
 * based on common criteria like address, datum, asset holdings, etc.
 * All predicates are stateless and thread-safe.
 *
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Basic address predicate
 * Predicate<Utxo> atScript = ScriptPredicates.atAddress(scriptAddress);
 *
 * // Combine multiple conditions with AND
 * Predicate<Utxo> complex = ScriptPredicates.and(
 *     ScriptPredicates.atAddress(scriptAddress),
 *     ScriptPredicates.withInlineDatum(expectedDatum),
 *     ScriptPredicates.withMinLovelace(BigInteger.valueOf(10_000_000))
 * );
 *
 * // Use in ScriptTx
 * ScriptTx scriptTx = new ScriptTx()
 *     .collectFrom(scriptAddress, complex, redeemer, datum)
 *     .payToAddress(receiver, amount);
 * }</pre>
 *
 */
public final class ScriptPredicates {

    /**
     * Private constructor to prevent instantiation.
     */
    private ScriptPredicates() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a predicate that matches UTXOs at the specified address.
     *
     * @param address The address to match (Bech32 format)
     * @return Predicate that tests if UTXO is at the given address
     * @throws NullPointerException if address is null
     */
    public static Predicate<Utxo> atAddress(@NonNull String address) {
        Objects.requireNonNull(address, "Address cannot be null");
        return utxo -> Objects.equals(utxo.getAddress(), address);
    }

    /**
     * Creates a predicate that matches UTXOs with the specified inline datum.
     *
     * @param expectedDatum The datum to match
     * @return Predicate that tests if UTXO has the given inline datum
     * @throws NullPointerException if expectedDatum is null
     */
    public static Predicate<Utxo> withInlineDatum(@NonNull PlutusData expectedDatum) {
        Objects.requireNonNull(expectedDatum, "Expected datum cannot be null");
        String expectedHex = expectedDatum.serializeToHex();
        return utxo -> Objects.equals(utxo.getInlineDatum(), expectedHex);
    }

    /**
     * Creates a predicate that matches UTXOs with the specified datum hash.
     *
     * @param datumHash The datum hash to match (hex string)
     * @return Predicate that tests if UTXO has the given datum hash
     * @throws NullPointerException if datumHash is null
     */
    public static Predicate<Utxo> withDatumHash(@NonNull String datumHash) {
        Objects.requireNonNull(datumHash, "Datum hash cannot be null");
        return utxo -> Objects.equals(utxo.getDataHash(), datumHash);
    }

    /**
     * Creates a predicate that matches UTXOs with at least the specified lovelace amount.
     *
     * @param minLovelace Minimum lovelace amount
     * @return Predicate that tests if UTXO has at least the minimum lovelace
     * @throws NullPointerException if minLovelace is null
     * @throws IllegalArgumentException if minLovelace is negative
     */
    public static Predicate<Utxo> withMinLovelace(@NonNull BigInteger minLovelace) {
        Objects.requireNonNull(minLovelace, "Minimum lovelace cannot be null");
        if (minLovelace.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum lovelace cannot be negative");
        }

        return utxo -> {
            BigInteger lovelaceAmount = getLovelaceAmount(utxo);
            return lovelaceAmount.compareTo(minLovelace) >= 0;
        };
    }

    /**
     * Creates a predicate that matches UTXOs containing the specified native asset.
     *
     * @param policyId Policy ID of the asset (hex string)
     * @param assetName Asset name (null to match any asset under the policy)
     * @return Predicate that tests if UTXO contains the specified asset
     * @throws NullPointerException if policyId is null
     */
    public static Predicate<Utxo> withAsset(@NonNull String policyId, String assetName) {
        Objects.requireNonNull(policyId, "Policy ID cannot be null");

        return utxo -> {
            if (utxo.getAmount() == null) {
                return false;
            }

            return utxo.getAmount().stream()
                    .filter(amount -> !LOVELACE.equals(amount.getUnit()))  // Skip lovelace
                    .anyMatch(amount -> {
                        try {
                            Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(amount.getUnit());
                            String unitPolicyId = policyAssetName._1;
                            String unitAssetName = policyAssetName._2;

                            if (!policyId.equals(unitPolicyId)) {
                                return false;
                            }
                            // Use helper to compare asset names properly
                            return assetNamesMatch(assetName, unitAssetName);
                        } catch (Exception e) {
                            // Invalid unit format, skip
                            return false;
                        }
                    });
        };
    }

    /**
     * Creates a predicate that matches UTXOs containing at least the specified quantity
     * of a native asset.
     *
     * @param policyId Policy ID of the asset (hex string)
     * @param assetName Asset name
     * @param minQuantity Minimum quantity of the asset
     * @return Predicate that tests if UTXO contains at least the minimum quantity
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if minQuantity is negative
     */
    public static Predicate<Utxo> withMinAssetQuantity(@NonNull String policyId,
                                                       @NonNull String assetName,
                                                       @NonNull BigInteger minQuantity) {
        Objects.requireNonNull(policyId, "Policy ID cannot be null");
        Objects.requireNonNull(assetName, "Asset name cannot be null");
        Objects.requireNonNull(minQuantity, "Minimum quantity cannot be null");
        if (minQuantity.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum quantity cannot be negative");
        }

        return utxo -> {
            if (utxo.getAmount() == null) {
                return false;
            }

            return utxo.getAmount().stream()
                    .filter(amount -> !LOVELACE.equals(amount.getUnit()))  // Skip lovelace
                    .anyMatch(amount -> {
                        try {
                            Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(amount.getUnit());
                            String unitPolicyId = policyAssetName._1;
                            String unitAssetName = policyAssetName._2;

                            return policyId.equals(unitPolicyId) &&
                                   assetNamesMatch(assetName, unitAssetName) &&
                                   amount.getQuantity().compareTo(minQuantity) >= 0;
                        } catch (Exception e) {
                            // Invalid unit format, skip
                            return false;
                        }
                    });
        };
    }

    /**
     * Combines multiple predicates with AND logic - all predicates must be true.
     *
     * @param predicates Predicates to combine
     * @return Combined predicate using AND logic
     * @throws IllegalArgumentException if no predicates provided
     */
    @SafeVarargs
    public static Predicate<Utxo> and(@NonNull Predicate<Utxo>... predicates) {
        if (predicates.length == 0) {
            throw new IllegalArgumentException("At least one predicate must be provided");
        }

        return Arrays.stream(predicates)
                .reduce(Predicate::and)
                .orElse(utxo -> true);
    }

    /**
     * Combines multiple predicates with OR logic - at least one predicate must be true.
     *
     * @param predicates Predicates to combine
     * @return Combined predicate using OR logic
     * @throws IllegalArgumentException if no predicates provided
     */
    @SafeVarargs
    public static Predicate<Utxo> or(@NonNull Predicate<Utxo>... predicates) {
        if (predicates.length == 0) {
            throw new IllegalArgumentException("At least one predicate must be provided");
        }

        return Arrays.stream(predicates)
                .reduce(Predicate::or)
                .orElse(utxo -> false);
    }

    /**
     * Negates a predicate - returns true when the predicate returns false.
     *
     * @param predicate Predicate to negate
     * @return Negated predicate
     * @throws NullPointerException if predicate is null
     */
    public static Predicate<Utxo> not(@NonNull Predicate<Utxo> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        return predicate.negate();
    }

    /**
     * Creates a predicate that always returns true (matches all UTXOs).
     * Useful as a base case or for testing.
     *
     * @return Predicate that always returns true
     */
    public static Predicate<Utxo> any() {
        return utxo -> true;
    }

    /**
     * Creates a predicate that always returns false (matches no UTXOs).
     * Useful as a base case or for testing.
     *
     * @return Predicate that always returns false
     */
    public static Predicate<Utxo> none() {
        return utxo -> false;
    }

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
     * Helper method to compare asset names, handling hex encoding.
     * AssetUtil returns asset names with "0x" prefix as hex-encoded strings,
     * but we want to support both plain strings and hex strings.
     *
     * @param expectedAssetName The asset name to match (can be plain string or hex)
     * @param parsedAssetName The parsed asset name from AssetUtil (with 0x prefix)
     * @return true if asset names match
     */
    private static boolean assetNamesMatch(String expectedAssetName, String parsedAssetName) {
        if (expectedAssetName == null) {
            return true; // null means match any asset
        }

        // Direct comparison first
        if (Objects.equals(expectedAssetName, parsedAssetName)) {
            return true;
        }

        // Try hex encoding the expected name and comparing
        try {
            String hexExpected = HexUtil.encodeHexString(expectedAssetName.getBytes(), true);
            if (Objects.equals(hexExpected, parsedAssetName)) {
                return true;
            }
        } catch (Exception e) {
            // Ignore hex encoding errors
        }

        // Try comparing without 0x prefix if parsedAssetName starts with 0x
        if (parsedAssetName != null && parsedAssetName.startsWith("0x")) {
            try {
                String decodedParsed = new String(HexUtil.decodeHexString(parsedAssetName.substring(2)));
                if (Objects.equals(expectedAssetName, decodedParsed)) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore hex decoding errors
            }
        }

        return false;
    }
}
