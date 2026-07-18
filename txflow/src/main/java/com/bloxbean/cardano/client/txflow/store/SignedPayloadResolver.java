package com.bloxbean.cardano.client.txflow.store;

/**
 * Application adapter that loads signed CBOR from an external payload reference.
 *
 * <p>The resolver only retrieves bytes. TxFlow independently verifies both their recorded
 * SHA-256 digest and Cardano transaction hash through {@link SignedPayloadVerifier} before
 * recovery may resubmit them.</p>
 */
public interface SignedPayloadResolver {
    /**
     * Resolves an opaque reference recorded in {@link SignedPayload.ExternalCbor}.
     *
     * @param reference application-owned payload reference
     * @return signed transaction CBOR
     */
    byte[] resolve(String reference);
}
