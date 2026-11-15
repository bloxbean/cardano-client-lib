package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An entry of a Dijkstra body's {@code required_top_level_guards} (key 24), {@code credential => plutus_data / nil}: a
 * guard the top-level transaction must have, with the data given to it, if any.
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequiredGuard {
    /** The guard's credential. */
    private Credential credential;

    /** The data given to the guard, or null for nil. */
    private PlutusData data;
}
