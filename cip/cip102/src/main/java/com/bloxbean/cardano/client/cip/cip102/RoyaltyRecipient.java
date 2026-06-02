package com.bloxbean.cardano.client.cip.cip102;

import com.bloxbean.cardano.client.plutus.spec.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Represents a single royalty recipient as defined in the CIP-102 datum specification.
 *
 * <p>CDDL: {@code royalty_recipient = #6.121([address, int, optional_big_int, optional_big_int])}
 * <ul>
 *   <li>{@code address} — Plutus address (payment + optional stake credential)</li>
 *   <li>{@code int} — variable fee stored as {@code floor(10 / fee_percent)}</li>
 *   <li>{@code optional_big_int} — min fee in lovelace, or absent</li>
 *   <li>{@code optional_big_int} — max fee in lovelace, or absent</li>
 * </ul>
 *
 * <p>Use {@link RoyaltyFeeUtil} to convert between human-readable percentages and the
 * on-chain fee representation.
 */
@Getter
@Builder
public class RoyaltyRecipient {

    /** Plutus address as {@code ConstrPlutusData}. Build via {@link RoyaltyAddressUtil#toPlutusData}. */
    private final ConstrPlutusData address;

    /** Variable fee in on-chain format: {@code floor(10 / fee_percent)}. */
    private final BigInteger fee;

    /** Minimum royalty fee in lovelace (absolute). Empty means no minimum. */
    @Builder.Default
    private final Optional<BigInteger> minFee = Optional.empty();

    /** Maximum royalty fee in lovelace (absolute). Empty means no maximum. */
    @Builder.Default
    private final Optional<BigInteger> maxFee = Optional.empty();

    /**
     * Serializes this recipient to {@code ConstrPlutusData} for inclusion in a royalty datum.
     *
     * @return {@code #6.121([address, fee, optional_min_fee, optional_max_fee])}
     */
    public ConstrPlutusData asPlutusData() {
        return ConstrPlutusData.of(0,
                address,
                BigIntPlutusData.of(fee),
                toOptionalBigInt(minFee),
                toOptionalBigInt(maxFee));
    }

    /**
     * Deserializes a {@code ConstrPlutusData} into a {@link RoyaltyRecipient}.
     *
     * @param constr constructor 0 with four fields
     * @return deserialized recipient
     * @throws IllegalArgumentException if the alternative is not 0
     */
    public static RoyaltyRecipient fromPlutusData(ConstrPlutusData constr) {
        if (constr.getAlternative() != 0)
            throw new IllegalArgumentException("Expected constructor alternative 0 for RoyaltyRecipient, got: " + constr.getAlternative());

        var list = constr.getData().getPlutusDataList();
        ConstrPlutusData address = (ConstrPlutusData) list.get(0);
        BigInteger fee = ((BigIntPlutusData) list.get(1)).getValue();
        Optional<BigInteger> minFee = fromOptionalBigInt((ConstrPlutusData) list.get(2));
        Optional<BigInteger> maxFee = fromOptionalBigInt((ConstrPlutusData) list.get(3));

        return RoyaltyRecipient.builder()
                .address(address)
                .fee(fee)
                .minFee(minFee)
                .maxFee(maxFee)
                .build();
    }

    /**
     * Encodes {@code Optional<BigInteger>} as {@code optional_big_int}:
     * {@code #6.121([big_int])} for Some, {@code #6.122([])} for None.
     */
    private static ConstrPlutusData toOptionalBigInt(Optional<BigInteger> value) {
        return value
                .map(v -> ConstrPlutusData.of(0, BigIntPlutusData.of(v)))
                .orElseGet(() -> ConstrPlutusData.of(1));
    }

    /**
     * Decodes {@code optional_big_int} back to {@code Optional<BigInteger>}.
     * Alternative 0 = Some, alternative 1 = None.
     */
    private static Optional<BigInteger> fromOptionalBigInt(ConstrPlutusData constr) {
        if (constr.getAlternative() == 0) {
            BigInteger value = ((BigIntPlutusData) constr.getData().getPlutusDataList().get(0)).getValue();
            return Optional.of(value);
        }
        return Optional.empty();
    }
}
