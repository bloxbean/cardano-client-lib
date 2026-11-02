package com.bloxbean.cardano.client.programmabletoken.cip113;

/**
 * The owner credential a smart wallet is keyed by: {@code (programmableLogicBase, ownerCredential)}.
 *
 * <p>CIP-113 lets the application choose. Both choices are valid on chain — the base script checks
 * that the wallet's stake credential authorised the spend — but they give the same user two
 * different addresses, so every party holding or reading tokens must agree on one.
 */
public enum SmartWalletKey {
    /** Keyed by the owner's payment credential, as in the CIP's worked example. */
    PAYMENT,
    /** Keyed by the owner's stake credential. Needs a base address carrying one. */
    STAKE
}
