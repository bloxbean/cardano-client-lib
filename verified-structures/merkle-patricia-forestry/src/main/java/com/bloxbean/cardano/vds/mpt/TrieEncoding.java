package com.bloxbean.cardano.vds.mpt;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * CBOR encoding and decoding utilities for Merkle Patricia Trie nodes.
 *
 * <p>This class handles the serialization and deserialization of trie nodes using
 * CBOR (Concise Binary Object Representation) format. The encoding scheme follows
 * the Ethereum Yellow Paper specification for MPT nodes:</p>
 *
 * <p><b>Node Encoding Formats:</b></p>
 * <ul>
 *   <li><b>Branch Node:</b> 17-element array [child0, child1, ..., child15, value]</li>
 *   <li><b>Leaf Node:</b> 2 or 3-element array [HP-encoded key suffix (isLeaf=true), value, originalKey?]</li>
 *   <li><b>Extension Node:</b> 2-element array [HP-encoded path (isLeaf=false), child hash]</li>
 * </ul>
 *
 * <p><b>Node Type Detection:</b> The decoder uses array length and HP encoding flags
 * to determine the correct node type:</p>
 * <ul>
 *   <li>17 elements → BranchNode</li>
 *   <li>2 or 3 elements + HP isLeaf=true → LeafNode</li>
 *   <li>2 elements + HP isLeaf=false → ExtensionNode</li>
 * </ul>
 *
 * <p><b>CBOR Benefits:</b></p>
 * <ul>
 *   <li>Deterministic encoding ensures consistent hashes</li>
 *   <li>Compact binary representation reduces storage overhead</li>
 *   <li>Self-describing format enables robust parsing</li>
 *   <li>Wide language support for interoperability</li>
 * </ul>
 *
 * @see Node
 * @see com.bloxbean.cardano.vds.core.nibbles.Nibbles
 */
final class TrieEncoding {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TrieEncoding() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Decodes CBOR-encoded bytes into the appropriate Node type.
     *
     * <p>This method analyzes the CBOR structure to determine the correct node type:
     * <ul>
     *   <li>17-element arrays are decoded as {@link BranchNode}</li>
     *   <li>2 or 3-element arrays are decoded based on HP encoding flags:
     *       <ul>
     *         <li>HP isLeaf=true → {@link LeafNode} (3rd element is optional originalKey)</li>
     *         <li>HP isLeaf=false → {@link ExtensionNode} (only 2 elements)</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * @param encodedBytes the CBOR-encoded node data
     * @return the decoded Node instance (BranchNode, LeafNode, or ExtensionNode)
     * @throws IllegalArgumentException if encodedBytes is null, empty, or has invalid format
     * @throws RuntimeException         if CBOR decoding fails
     */
    static Node decode(byte[] encodedBytes) {
        if (encodedBytes == null) {
            throw new IllegalArgumentException("Cannot decode null bytes");
        }

        try {
            List<DataItem> items = new CborDecoder(new ByteArrayInputStream(encodedBytes)).decode();
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Empty CBOR data");
            }

            DataItem dataItem = items.get(0);
            if (!(dataItem instanceof Array)) {
                throw new IllegalArgumentException("Expected CBOR array, got " + dataItem.getClass().getSimpleName());
            }

            Array cborArray = (Array) dataItem;
            int arraySize = cborArray.getDataItems().size();

            // Determine node type based on array size
            if (arraySize == 17) {
                return BranchNode.decode(cborArray);
            } else if (arraySize == 2 || arraySize == 3) {
                // Analyze HP encoding to distinguish leaf from extension
                byte[] hpBytes = ((ByteString) cborArray.getDataItems().get(0)).getBytes();
                Nibbles.HP hpInfo = Nibbles.unpackHP(hpBytes);
                if (hpInfo.isLeaf) {
                    return LeafNode.decode(cborArray);
                } else {
                    // Extension nodes cannot have originalKey (3 elements only valid for leaves)
                    if (arraySize == 3) {
                        throw new IllegalArgumentException(
                                "Extension node cannot have 3 elements (originalKey only valid for leaves)");
                    }
                    return ExtensionNode.decode(cborArray);
                }
            } else {
                throw new IllegalArgumentException(
                        "Invalid node array size: " + arraySize + ". Expected 2-3 (leaf/extension) or 17 (branch)");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode CBOR node data", e);
        }
    }
}
