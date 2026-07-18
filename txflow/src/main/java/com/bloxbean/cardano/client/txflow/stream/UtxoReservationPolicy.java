package com.bloxbean.cardano.client.txflow.stream;

/**
 * Conservative MVP UTXO coordination policy.
 */
public enum UtxoReservationPolicy {
    /**
     * Execute generated flows serially for the stream, avoiding concurrent base
     * UTXO selection conflicts in the MVP.
     */
    SERIAL_BY_FUNDING_SCOPE;

    /**
     * Return the default serial policy.
     *
     * @return serial reservation policy
     */
    public static UtxoReservationPolicy serialByFundingScope() {
        return SERIAL_BY_FUNDING_SCOPE;
    }
}
