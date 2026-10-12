package com.bloxbean.cardano.client.txflow.stream;

import java.util.Objects;

/**
 * One coherent stored projection and its CAS watermark. Hydration must not pair
 * an old result with a newer sequence, which could overwrite subsequent truth.
 *
 * @param result stored item result
 * @param sourceSequence sequence stored with this exact result
 */
public record TxStreamStoredProjection(TxStreamItemResult result, long sourceSequence) {
    /**
     * Creates a snapshot with a non-null result.
     *
     * @param result stored result
     * @param sourceSequence the matching stored watermark
     */
    public TxStreamStoredProjection {
        Objects.requireNonNull(result, "result");
    }
}
