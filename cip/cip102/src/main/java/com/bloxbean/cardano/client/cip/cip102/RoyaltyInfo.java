package com.bloxbean.cardano.client.cip.cip102;

import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents the complete CIP-102 royalty datum.
 *
 * <p>CDDL: {@code royalty_info = #6.121([royalty_recipients, version, extra])}
 *
 * <p>This datum is stored inline at the UTxO holding the {@code (500)Royalty} token.
 * Use {@link RoyaltyToken} to construct the asset name and {@link RoyaltyAddressUtil}
 * to convert recipient addresses.
 *
 * <h2>Version semantics</h2>
 * <ul>
 *   <li>{@code version = 1} — single {@code (500)Royalty} token per policy ID.</li>
 *   <li>{@code version = 2} — supports multiple royalty policies via postfixed tokens
 *       (e.g. {@code (500)Royalty2}). Required when using any {@link RoyaltyToken}
 *       with a postfix.</li>
 * </ul>
 */
@Getter
@Builder
public class RoyaltyInfo {

    /** Datum version for a single royalty token per policy ID. */
    public static final int VERSION_1 = 1;
    /** Datum version supporting multiple postfixed royalty tokens per policy ID. */
    public static final int VERSION_2 = 2;

    @Builder.Default
    private final List<RoyaltyRecipient> recipients = List.of();

    // S1170: cannot be static — @Builder.Default requires an instance field so Lombok generates the builder setter correctly
    @SuppressWarnings("java:S1170")
    @Builder.Default
    private final int version = VERSION_2;

    /** Custom user-defined Plutus data. Defaults to {@code #6.121([])} (unit/void). */
    @Builder.Default
    private final PlutusData extra = PlutusData.unit();

    /**
     * Serializes this royalty info to {@code ConstrPlutusData} suitable for use
     * as an inline datum.
     *
     * @return {@code #6.121([royalty_recipients, version, extra])}
     */
    public ConstrPlutusData asPlutusData() {
        List<PlutusData> recipientDataList = recipients.stream()
                .map(RoyaltyRecipient::asPlutusData)
                .collect(Collectors.toList());

        return ConstrPlutusData.of(0,
                ListPlutusData.of(recipientDataList.toArray(new PlutusData[0])),
                BigIntPlutusData.of(BigInteger.valueOf(version)),
                extra);
    }

    /**
     * Deserializes a {@code ConstrPlutusData} (read from an inline datum) into
     * a {@link RoyaltyInfo}.
     *
     * @param constr constructor 0 with three fields
     * @return deserialized royalty info
     * @throws IllegalArgumentException if the alternative is not 0
     */
    public static RoyaltyInfo fromPlutusData(ConstrPlutusData constr) {
        if (constr.getAlternative() != 0)
            throw new IllegalArgumentException("Expected constructor alternative 0 for RoyaltyInfo, got: " + constr.getAlternative());

        var outer = constr.getData().getPlutusDataList();
        ListPlutusData recipientList = (ListPlutusData) outer.get(0);
        int version = ((BigIntPlutusData) outer.get(1)).getValue().intValue();
        PlutusData extra = outer.get(2);

        List<RoyaltyRecipient> recipients = recipientList.getPlutusDataList().stream()
                .map(d -> RoyaltyRecipient.fromPlutusData((ConstrPlutusData) d))
                .collect(Collectors.toList());

        return RoyaltyInfo.builder()
                .recipients(recipients)
                .version(version)
                .extra(extra)
                .build();
    }

    /**
     * Serializes this royalty info to raw CBOR bytes suitable for use as an inline datum.
     *
     * @return CBOR-encoded datum bytes
     */
    public byte[] toCbor() {
        return asPlutusData().serializeToBytes();
    }

    /**
     * Convenience method to deserialize from raw CBOR bytes (e.g. from an inline datum
     * returned by a backend service).
     *
     * @param cborBytes raw CBOR-encoded datum bytes
     * @return deserialized royalty info
     * @throws CborDeserializationException if the bytes cannot be decoded
     */
    public static RoyaltyInfo fromCbor(byte[] cborBytes) throws CborDeserializationException {
        ConstrPlutusData constr = (ConstrPlutusData) PlutusData.deserialize(cborBytes);
        return fromPlutusData(constr);
    }
}
