package com.bloxbean.cardano.client.crypto.bip32;

/**
 * Some wallets use different derivation methods from the BIP-39 seed phrase to get the root key. The default derivation
 * is ICARUS as per CIP-1852. Ledger and Trezor wallets use different derivation methods.
 */
public enum Bip32Type {
    // Standard root key creation for CIP-1852 wallets
    ICARUS,
    // Ledger
    LEDGER,
    // Trezor
    TREZOR
}
