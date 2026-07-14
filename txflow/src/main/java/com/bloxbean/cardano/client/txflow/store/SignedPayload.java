package com.bloxbean.cardano.client.txflow.store;

import java.util.Arrays;
import java.util.Objects;

/**
 * Durable representation of the exact signed transaction used by an attempt.
 *
 * <p>A store may retain CBOR inline or persist only an application-owned external reference.
 * Both representations carry a SHA-256 digest and the Cardano transaction hash. Recovery must
 * pass resolved bytes through {@link SignedPayloadVerifier} before resubmitting them; an external
 * resolver is a byte loader, not a trust boundary.</p>
 */
public sealed interface SignedPayload permits SignedPayload.InlineCbor, SignedPayload.ExternalCbor {
    /**
     * Returns the recorded SHA-256 digest of the signed CBOR.
     *
     * @return hexadecimal SHA-256 digest
     */
    String sha256();

    /**
     * Returns the Cardano transaction hash recorded when the payload was prepared.
     *
     * @return transaction hash expected when the CBOR is decoded
     */
    String transactionHash();

    /**
     * Signed CBOR stored directly in the execution snapshot.
     *
     * <p>The byte array is copied both on construction and access, so callers cannot mutate the
     * persisted value through an array reference.</p>
     *
     * @param cbor signed transaction bytes
     * @param sha256 hexadecimal SHA-256 digest of {@code cbor}
     * @param transactionHash Cardano transaction hash derived from {@code cbor}
     */
    record InlineCbor(byte[] cbor, String sha256, String transactionHash) implements SignedPayload {
        /**
         * Creates an inline payload with a private copy of the signed bytes.
         *
         * @param cbor signed transaction bytes to copy
         * @param sha256 hexadecimal SHA-256 digest of {@code cbor}
         * @param transactionHash Cardano transaction hash derived from {@code cbor}
         */
        public InlineCbor {
            Objects.requireNonNull(cbor, "cbor");
            cbor = Arrays.copyOf(cbor, cbor.length);
            Objects.requireNonNull(sha256, "sha256");
            Objects.requireNonNull(transactionHash, "transactionHash");
        }

        /**
         * Returns a defensive copy of the signed transaction bytes.
         *
         * @return copied signed CBOR
         */
        @Override
        public byte[] cbor() {
            return Arrays.copyOf(cbor, cbor.length);
        }

        /**
         * Compares inline payloads by their signed bytes and recorded identities.
         *
         * <p>The record-generated implementation would compare {@code byte[]} by
         * reference, which is not suitable for a durable value object.</p>
         *
         * @param other value to compare
         * @return whether both payloads contain the same bytes and identities
         */
        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InlineCbor that)) return false;
            return Arrays.equals(cbor, that.cbor)
                    && sha256.equals(that.sha256)
                    && transactionHash.equals(that.transactionHash);
        }

        /**
         * Computes a content-based hash consistent with {@link #equals(Object)}.
         *
         * @return content-based hash code
         */
        @Override
        public int hashCode() {
            int result = Arrays.hashCode(cbor);
            result = 31 * result + sha256.hashCode();
            result = 31 * result + transactionHash.hashCode();
            return result;
        }

        /**
         * Describes the payload without rendering signed transaction bytes.
         *
         * @return redacted diagnostic representation
         */
        @Override
        public String toString() {
            return "InlineCbor[cborLength=" + cbor.length
                    + ", sha256=" + sha256
                    + ", transactionHash=" + transactionHash + ']';
        }
    }

    /**
     * Application-owned reference to signed CBOR stored outside the execution snapshot.
     *
     * <p>The reference is intentionally opaque to TxFlow and is resolved only through a
     * {@link SignedPayloadResolver}. The same digest and transaction-hash checks used for inline
     * bytes still apply after resolution.</p>
     *
     * @param reference opaque external payload reference
     * @param sha256 hexadecimal SHA-256 digest of the referenced CBOR
     * @param transactionHash Cardano transaction hash derived from the referenced CBOR
     */
    record ExternalCbor(String reference, String sha256, String transactionHash)
            implements SignedPayload {
        /**
         * Creates an external payload reference with its recorded identities.
         *
         * @param reference opaque external payload reference
         * @param sha256 hexadecimal SHA-256 digest of the referenced CBOR
         * @param transactionHash Cardano transaction hash derived from the referenced CBOR
         */
        public ExternalCbor {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(sha256, "sha256");
            Objects.requireNonNull(transactionHash, "transactionHash");
        }
    }
}
