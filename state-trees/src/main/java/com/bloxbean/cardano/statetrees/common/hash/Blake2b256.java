package com.bloxbean.cardano.statetrees.common.hash;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;

/**
 * Blake2b-256 cryptographic hash function implementation.
 *
 * <p>Blake2b-256 is the standard hash function used throughout the Cardano ecosystem
 * for computing node hashes in Merkle Patricia Tries. It produces 32-byte (256-bit)
 * hash digests with excellent security properties and performance characteristics.</p>
 *
 * <p><b>Properties:</b></p>
 * <ul>
 *   <li>Output size: 32 bytes (256 bits)</li>
 *   <li>Collision resistant: computationally infeasible to find two inputs with same hash</li>
 *   <li>Preimage resistant: computationally infeasible to find input for a given hash</li>
 *   <li>Deterministic: same input always produces same output</li>
 *   <li>Fast: optimized for speed on modern hardware</li>
 * </ul>
 *
 * <p>This implementation delegates to the Cardano client library's Blake2b utilities,
 * ensuring consistency with the broader Cardano ecosystem.</p>
 */
public final class Blake2b256 {

  /**
   * Private constructor to prevent instantiation of this utility class.
   */
  private Blake2b256() {
    throw new AssertionError("Utility class - do not instantiate");
  }

  /**
   * Computes the Blake2b-256 hash of the input data.
   *
   * @param inputData the data to hash
   * @return the 32-byte Blake2b-256 hash digest
   * @throws NullPointerException if inputData is null
   */
  public static byte[] digest(byte[] inputData) {
    return Blake2bUtil.blake2bHash256(inputData);
  }
}

