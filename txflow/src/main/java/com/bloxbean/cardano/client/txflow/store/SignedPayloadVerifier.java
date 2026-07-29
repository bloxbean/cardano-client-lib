package com.bloxbean.cardano.client.txflow.store;

import com.bloxbean.cardano.client.transaction.util.TransactionUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Resolves and verifies signed transaction payloads at the recovery trust boundary.
 *
 * <p>Verification is deliberately owned by TxFlow rather than by an application resolver. Both
 * inline and external payloads must match the persisted SHA-256 digest and the Cardano
 * transaction hash before their bytes can be returned for resubmission.</p>
 */
public final class SignedPayloadVerifier {
    private SignedPayloadVerifier() {
    }

    /**
     * Loads a payload if necessary and verifies its two persisted identities.
     *
     * @param payload inline or externally referenced signed payload
     * @param resolver external byte resolver; may be {@code null} only for an inline payload
     * @return a defensive copy of the verified CBOR
     * @throws FlowStoreException when an external resolver is missing or either identity differs
     */
    public static byte[] resolveAndVerify(SignedPayload payload, SignedPayloadResolver resolver) {
        byte[] cbor;
        if (payload instanceof SignedPayload.InlineCbor) {
            cbor = ((SignedPayload.InlineCbor) payload).cbor();
        } else {
            if (resolver == null) throw new FlowStoreException("TXFLOW_PAYLOAD_RESOLVER_REQUIRED",
                    "External signed payload requires a resolver");
            cbor = resolver.resolve(((SignedPayload.ExternalCbor) payload).reference());
        }
        if (!sha256(cbor).equalsIgnoreCase(payload.sha256())) {
            throw new FlowStoreException("TXFLOW_PAYLOAD_DIGEST_MISMATCH",
                    "Signed payload SHA-256 does not match snapshot");
        }
        if (!TransactionUtil.getTxHash(cbor).equalsIgnoreCase(payload.transactionHash())) {
            throw new FlowStoreException("TXFLOW_TRANSACTION_HASH_MISMATCH",
                    "Cardano transaction hash does not match snapshot");
        }
        return cbor.clone();
    }

    /**
     * Computes the lowercase hexadecimal SHA-256 digest of bytes.
     *
     * @param value bytes to digest
     * @return lowercase hexadecimal digest
     */
    public static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Computes the lowercase hexadecimal SHA-256 digest of a UTF-8 string.
     *
     * @param value text to digest
     * @return lowercase hexadecimal digest
     */
    public static String sha256(String value) {
        if (value == null) throw new IllegalArgumentException("value cannot be null");
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }
}
