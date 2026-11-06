package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * An entry of a Dijkstra body's {@code account_balance_intervals} (key 26) or
 * {@code starting_account_balance_intervals} (key 27): the balance a reward account must have, either {@link #exact},
 * or within {@link #inclusiveLower} and {@link #exclusiveUpper}, at least one of which is set, as the ledger's four
 * forms ({@code Dijkstra/Scripts.hs}).
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalanceInterval {
    /** The bech32 reward address. */
    private String rewardAddress;

    /** The exact balance, written as a coin; null for the bounds form. */
    private BigInteger exact;

    /** The inclusive lower bound, or null for none. */
    private BigInteger inclusiveLower;

    /** The exclusive upper bound, or null for none. */
    private BigInteger exclusiveUpper;

    // The interval, a coin or [lower / nil, upper / nil].
    DataItem serializeInterval() throws CborSerializationException {
        boolean bounded = inclusiveLower != null || exclusiveUpper != null;
        if (exact != null) {
            if (bounded)
                throw new CborSerializationException("AccountBalanceInterval of " + rewardAddress
                        + ": exact and a bound are both set");
            return new UnsignedInteger(exact);
        }
        if (!bounded)
            throw new CborSerializationException("AccountBalanceInterval of " + rewardAddress
                    + ": exact, inclusiveLower and exclusiveUpper are all null");
        Array bounds = new Array();
        bounds.add(inclusiveLower != null ? new UnsignedInteger(inclusiveLower) : SimpleValue.NULL);
        bounds.add(exclusiveUpper != null ? new UnsignedInteger(exclusiveUpper) : SimpleValue.NULL);
        return bounds;
    }
}
