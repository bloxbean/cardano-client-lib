package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

/**
 * Visitor that prints the tree structure with intermediate nodes.
 *
 * <p>Produces an ASCII-formatted tree showing:</p>
 * <ul>
 *   <li>Node types (Branch, Extension, Leaf)</li>
 *   <li>Node hashes (truncated hex)</li>
 *   <li>Path nibbles</li>
 *   <li>Values (as hex strings)</li>
 * </ul>
 *
 * <p><b>Output Format Example:</b></p>
 * <pre>
 * Root: 0x1a2b3c4d...
 * [Branch] hash=0x5e6f7a8b...
 *   [0] -&gt; (empty)
 *   [1] -&gt; [Extension] path=[8,6,5] hash=0x7a8b9c0d...
 *     [Leaf] path=[6,c,6,f] value=0x776f726c64
 *   [2] -&gt; (empty)
 *   ...
 *   [f] -&gt; [Leaf] path=[a,b,c] value=0x68656c6c6f
 * </pre>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * StringBuilder output = new StringBuilder();
 * TreePrinterVisitor visitor = new TreePrinterVisitor(persistence, output, 0, "", rootHash);
 * rootNode.accept(visitor);
 * System.out.println(output.toString());
 * }</pre>
 *
 * @since 0.8.0
 */
final class TreePrinterVisitor implements NodeVisitor<Void> {
    private static final int MAX_HEX_LEN = 16; // Max hex chars to display (8 bytes)
    private static final int MAX_VALUE_LEN = 32; // Max hex chars for value display

    private final NodePersistence persistence;
    private final StringBuilder output;
    private final int depth;
    private final String prefix; // Indentation prefix
    private final byte[] nodeHash;

    /**
     * Creates a new tree printer visitor.
     *
     * @param persistence the node persistence layer for loading child nodes
     * @param output      the StringBuilder to append output to
     * @param depth       the current depth in the tree (for indentation)
     * @param prefix      the indentation prefix for this level
     * @param nodeHash    the hash of the current node being visited
     */
    public TreePrinterVisitor(NodePersistence persistence, StringBuilder output,
                              int depth, String prefix, byte[] nodeHash) {
        this.persistence = persistence;
        this.output = output;
        this.depth = depth;
        this.prefix = prefix;
        this.nodeHash = nodeHash;
    }

    @Override
    public Void visitLeaf(LeafNode leaf) {
        Nibbles.HP hp = Nibbles.unpackHP(leaf.getHp());
        int[] nibbles = hp.nibbles;

        output.append(prefix)
                .append("[Leaf] path=")
                .append(formatNibbles(nibbles))
                .append(" value=")
                .append(toHex(leaf.getValue(), MAX_VALUE_LEN))
                .append("\n");

        return null;
    }

    @Override
    public Void visitBranch(BranchNode branch) {
        byte[] value = branch.getValue();
        String valueStr = value != null ? toHex(value, MAX_VALUE_LEN) : "<none>";

        output.append(prefix)
                .append("[Branch] hash=")
                .append(toHex(nodeHash, MAX_HEX_LEN))
                .append(" value=")
                .append(valueStr)
                .append("\n");

        // Print each child slot
        String childIndent = prefix + "  ";
        for (int i = 0; i < 16; i++) {
            byte[] childHash = branch.getChild(i);
            String nibbleHex = Integer.toHexString(i);

            if (childHash == null || childHash.length == 0) {
                output.append(childIndent)
                        .append("[")
                        .append(nibbleHex)
                        .append("] -> (empty)\n");
            } else {
                output.append(childIndent)
                        .append("[")
                        .append(nibbleHex)
                        .append("] -> ");

                // Load and print child node
                Node childNode = persistence.load(NodeHash.of(childHash));
                if (childNode != null) {
                    TreePrinterVisitor childVisitor = new TreePrinterVisitor(
                            persistence, output, depth + 1, childIndent + "  ", childHash);
                    // Don't add newline here - child visitor will handle its own formatting
                    output.append("\n");
                    childNode.accept(childVisitor);
                } else {
                    output.append("[Missing node] hash=")
                            .append(toHex(childHash, MAX_HEX_LEN))
                            .append("\n");
                }
            }
        }

        return null;
    }

    @Override
    public Void visitExtension(ExtensionNode extension) {
        Nibbles.HP hp = Nibbles.unpackHP(extension.getHp());
        int[] nibbles = hp.nibbles;

        output.append(prefix)
                .append("[Extension] path=")
                .append(formatNibbles(nibbles))
                .append(" hash=")
                .append(toHex(nodeHash, MAX_HEX_LEN))
                .append("\n");

        // Print child
        byte[] childHash = extension.getChild();
        String childIndent = prefix + "  ";
        if (childHash != null && childHash.length > 0) {
            Node childNode = persistence.load(NodeHash.of(childHash));
            if (childNode != null) {
                TreePrinterVisitor childVisitor = new TreePrinterVisitor(
                        persistence, output, depth + 1, childIndent, childHash);
                childNode.accept(childVisitor);
            } else {
                output.append(childIndent)
                        .append("[Missing child node] hash=")
                        .append(toHex(childHash, MAX_HEX_LEN))
                        .append("\n");
            }
        } else {
            output.append(childIndent)
                    .append("(no child)\n");
        }

        return null;
    }

    /**
     * Converts bytes to a truncated hex string with "0x" prefix.
     *
     * @param bytes  the bytes to convert
     * @param maxLen maximum number of hex characters to display
     * @return formatted hex string (e.g., "0x1a2b3c...ef" or "0x1a2b3c")
     */
    private static String toHex(byte[] bytes, int maxLen) {
        if (bytes == null || bytes.length == 0) {
            return "0x";
        }

        String fullHex = HexUtil.encodeHexString(bytes);
        if (fullHex.length() <= maxLen) {
            return "0x" + fullHex;
        }

        // Truncate with ellipsis, showing start and end
        int halfLen = (maxLen - 2) / 2; // -2 for ".."
        return "0x" + fullHex.substring(0, halfLen) + ".." + fullHex.substring(fullHex.length() - halfLen);
    }

    /**
     * Formats a nibbles array as a string.
     *
     * @param nibbles the nibbles array
     * @return formatted string (e.g., "[8,6,5,c,d]")
     */
    private static String formatNibbles(int[] nibbles) {
        if (nibbles == null || nibbles.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < nibbles.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(Integer.toHexString(nibbles[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
