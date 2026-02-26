package com.bloxbean.cardano.client.quicktx;

/**
 * Controls how Phase 4 deposit resolution finds funds to cover protocol deposits
 * (stake registration, pool registration, DRep registration, governance proposals).
 */
public enum DepositMode {
    /**
     * Context-aware fallback chain (default).
     * <ul>
     *   <li>{@code mergeOutputs=false}: ChangeOutput → new UTXO selection → any output</li>
     *   <li>{@code mergeOutputs=true}: any output → new UTXO selection (no ChangeOutput exists when merged)</li>
     * </ul>
     */
    AUTO,

    /**
     * Only deduct from {@code ChangeOutput} instances at the deposit payer address.
     * Errors if no suitable change output is found.
     */
    CHANGE_OUTPUT,

    /**
     * Deduct from any output at the deposit payer address, including user-declared
     * {@code payToAddress} outputs.
     */
    ANY_OUTPUT,

    /**
     * Always select new UTXOs from the deposit payer address. Never touches existing outputs.
     * Errors if insufficient UTXOs are available.
     */
    NEW_UTXO_SELECTION
}
