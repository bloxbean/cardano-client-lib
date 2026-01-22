package com.bloxbean.cardano.vds.mpt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

/**
 * Represents a node in the tree structure for JSON serialization.
 *
 * <p>This class hierarchy provides a structured representation of the
 * Merkle Patricia Trie that can be serialized to JSON for visualization,
 * debugging, or custom rendering purposes.</p>
 *
 * <p><b>JSON Output Example:</b></p>
 * <pre>{@code
 * {
 *   "type": "branch",
 *   "hash": "5e6f7a8b...",
 *   "value": null,
 *   "children": {
 *     "0": null,
 *     "1": {
 *       "type": "extension",
 *       "hash": "7a8b9c0d...",
 *       "path": [8, 6, 5],
 *       "child": {
 *         "type": "leaf",
 *         "path": [6, 12, 6, 15],
 *         "value": "776f726c64",
 *         "originalKey": "68656c6c6f"
 *       }
 *     },
 *     ...
 *   }
 * }
 * }</pre>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * MpfTrie trie = MpfTrie.withOriginalKeyStorage(store);
 * trie.put("hello".getBytes(), "world".getBytes());
 *
 * // Get structured tree for custom rendering
 * TreeNode structure = trie.getTreeStructure();
 *
 * // Get JSON output
 * String json = trie.printTreeJson();
 * }</pre>
 *
 * @since 0.8.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TreeNode.BranchTreeNode.class, name = "branch"),
        @JsonSubTypes.Type(value = TreeNode.ExtensionTreeNode.class, name = "extension"),
        @JsonSubTypes.Type(value = TreeNode.LeafTreeNode.class, name = "leaf")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class TreeNode {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Returns the type of this node ("branch", "extension", or "leaf").
     *
     * @return the node type as a string
     */
    public abstract String getType();

    /**
     * Serializes a TreeNode to a JSON string.
     *
     * @param node the tree node to serialize
     * @return the JSON string representation
     * @throws RuntimeException if serialization fails
     */
    public static String toJson(TreeNode node) {
        if (node == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize TreeNode to JSON", e);
        }
    }

    /**
     * Represents a branch node in the tree structure.
     *
     * <p>Branch nodes have 16 child slots (one for each hex nibble 0-f) and
     * optionally store a value when a key terminates at this branch.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class BranchTreeNode extends TreeNode {
        private final String hash;
        private final String value;
        private final Map<String, TreeNode> children;

        /**
         * Creates a new branch tree node.
         *
         * @param hash     the hash of this node (hex string)
         * @param value    the value at this node (hex string), or null
         * @param children map of nibble ("0"-"f") to child node or null
         */
        public BranchTreeNode(String hash, String value, Map<String, TreeNode> children) {
            this.hash = hash;
            this.value = value;
            this.children = children;
        }

        @Override
        @JsonProperty("type")
        public String getType() {
            return "branch";
        }

        /**
         * Gets the hash of this branch node.
         *
         * @return the hash as a hex string
         */
        @JsonProperty("hash")
        public String getHash() {
            return hash;
        }

        /**
         * Gets the value stored at this branch node.
         *
         * @return the value as a hex string, or null if no value
         */
        @JsonProperty("value")
        public String getValue() {
            return value;
        }

        /**
         * Gets the children of this branch node.
         *
         * @return map of nibble ("0"-"f") to child node or null
         */
        @JsonProperty("children")
        public Map<String, TreeNode> getChildren() {
            return children;
        }
    }

    /**
     * Represents an extension node in the tree structure.
     *
     * <p>Extension nodes compress paths by storing a sequence of nibbles
     * leading to a single child node.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ExtensionTreeNode extends TreeNode {
        private final String hash;
        private final int[] path;
        private final TreeNode child;

        /**
         * Creates a new extension tree node.
         *
         * @param hash  the hash of this node (hex string)
         * @param path  the path nibbles as an array
         * @param child the child node
         */
        public ExtensionTreeNode(String hash, int[] path, TreeNode child) {
            this.hash = hash;
            this.path = path;
            this.child = child;
        }

        @Override
        @JsonProperty("type")
        public String getType() {
            return "extension";
        }

        /**
         * Gets the hash of this extension node.
         *
         * @return the hash as a hex string
         */
        @JsonProperty("hash")
        public String getHash() {
            return hash;
        }

        /**
         * Gets the path nibbles of this extension node.
         *
         * @return array of nibble values (0-15)
         */
        @JsonProperty("path")
        public int[] getPath() {
            return path;
        }

        /**
         * Gets the child node.
         *
         * @return the child tree node
         */
        @JsonProperty("child")
        public TreeNode getChild() {
            return child;
        }
    }

    /**
     * Represents a leaf node in the tree structure.
     *
     * <p>Leaf nodes store the actual key-value pairs at the terminals
     * of the trie paths.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class LeafTreeNode extends TreeNode {
        private final int[] path;
        private final String value;
        private final String originalKey;

        /**
         * Creates a new leaf tree node.
         *
         * @param path        the path nibbles as an array
         * @param value       the value (hex string)
         * @param originalKey the original key (hex string), or null
         */
        public LeafTreeNode(int[] path, String value, String originalKey) {
            this.path = path;
            this.value = value;
            this.originalKey = originalKey;
        }

        @Override
        @JsonProperty("type")
        public String getType() {
            return "leaf";
        }

        /**
         * Gets the path nibbles of this leaf node.
         *
         * @return array of nibble values (0-15)
         */
        @JsonProperty("path")
        public int[] getPath() {
            return path;
        }

        /**
         * Gets the value stored in this leaf node.
         *
         * @return the value as a hex string
         */
        @JsonProperty("value")
        public String getValue() {
            return value;
        }

        /**
         * Gets the original (unhashed) key if stored.
         *
         * @return the original key as a hex string, or null
         */
        @JsonProperty("originalKey")
        public String getOriginalKey() {
            return originalKey;
        }
    }
}
