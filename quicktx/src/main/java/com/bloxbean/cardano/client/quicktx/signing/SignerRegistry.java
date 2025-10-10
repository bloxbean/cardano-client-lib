package com.bloxbean.cardano.client.quicktx.signing;

import java.util.Optional;

/**
 * Resolves signer references (URI-style) to runtime bindings that can
 * produce TxSigner instances and optionally expose a preferred address or Wallet.
 * <p>
 * Examples of references:
 * - account://alice
 * - wallet://ops
 * - policy://nft
 */
public interface SignerRegistry {

    /**
     * Resolve a reference into a {@link SignerBinding}.
     *
     * @param ref reference string (e.g., account://alice)
     * @return optional binding if the ref is known to the registry
     */
    Optional<SignerBinding> resolve(String ref);
}

