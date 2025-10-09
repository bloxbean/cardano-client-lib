package com.bloxbean.cardano.statetrees.jmt.proof;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;

/**
 * Pluggable codec for encoding/decoding JMT proofs to/from wire format.
 *
 * <p>This interface allows different proof serialization strategies:
 * <ul>
 *   <li>{@link ClassicJmtProofCodec} - CBOR array format </li>
 *   <li>Custom compact formats for on-chain usage (minimize bytes)</li>
 *   <li>JSON formats for debugging/APIs</li>
 *   <li>Protobuf formats for cross-chain interoperability</li>
 * </ul>
 *
 * <p><b>Design Pattern:</b> This follows the same dependency injection pattern as
 * {@link CommitmentScheme}, allowing users to customize proof encoding without
 * changing the core tree logic.
 *
 * <p><b>Thread Safety:</b> Implementations should be stateless and thread-safe,
 * as a single codec instance may be shared across multiple proof operations.
 *
 * @see ClassicJmtProofCodec
 * @see com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree#getProofWire(byte[], long)
 * @see com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree#verifyProofWire(byte[], byte[], byte[], boolean, byte[])
 * @since 0.8.0
 */
public interface JmtProofCodec {

    /**
     * Encodes a JMT proof into wire format for transmission or storage.
     *
     * <p>The wire format is implementation-specific and should be documented
     * by each codec. Common formats include CBOR, Protobuf, JSON, or custom
     * binary encodings.
     *
     * <p><b>Encoding Requirements:</b>
     * <ul>
     *   <li>Must be deterministic (same proof → same bytes)</li>
     *   <li>Must be decodable by corresponding {@link #verify} method</li>
     *   <li>Should include all information needed for verification</li>
     * </ul>
     *
     * @param proof  the JMT proof to encode (branch steps + leaf information)
     * @param key    the original key for which the proof was generated
     * @param hashFn the hash function used by the tree
     * @param cs     the commitment scheme used by the tree
     * @return wire format bytes (encoding-specific)
     * @throws IllegalStateException if encoding fails
     */
    byte[] toWire(JmtProof proof, byte[] key, HashFunction hashFn, CommitmentScheme cs);

    /**
     * Verifies a wire format proof against an expected root hash.
     *
     * <p>This method decodes the wire format, reconstructs the Merkle path,
     * and verifies that the computed root hash matches the expected root.
     *
     * <p><b>Verification Process:</b>
     * <ol>
     *   <li>Decode wire bytes into proof structure</li>
     *   <li>Traverse proof nodes following key nibbles</li>
     *   <li>Reconstruct root hash from leaf up through internal nodes</li>
     *   <li>Compare computed root with expected root</li>
     * </ol>
     *
     * <p><b>Inclusion vs Non-Inclusion Proofs:</b>
     * <ul>
     *   <li><b>Inclusion</b> ({@code including=true}): Proves key exists with given value</li>
     *   <li><b>Non-Inclusion</b> ({@code including=false}): Proves key does NOT exist
     *       (either empty branch or conflicting leaf)</li>
     * </ul>
     *
     * @param expectedRoot the expected root hash to verify against
     * @param key          the key being proved (used to determine traversal path)
     * @param value        the expected value (for inclusion proofs), or null (for non-inclusion)
     * @param including    true for inclusion proof, false for non-inclusion proof
     * @param wire         the wire format proof bytes
     * @param hashFn       the hash function to use for verification
     * @param cs           the commitment scheme to use for verification
     * @return true if proof is valid and root matches, false otherwise
     * @throws IllegalArgumentException if wire format is invalid or corrupt
     */
    boolean verify(byte[] expectedRoot, byte[] key, byte[] value,
                   boolean including, byte[] wire,
                   HashFunction hashFn, CommitmentScheme cs);
}
