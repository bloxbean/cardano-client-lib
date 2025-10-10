package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.common.util.VarInts;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Versioned address of a JMT node.
 *
 * <p>The Jellyfish Merkle Tree persists nodes using a logical key composed of the
 * nibble path from the root to the node and the version at which that node was
 * created. This class encapsulates that composite key and provides the byte
 * encoding described in ADR-0004:</p>
 *
 * <pre>
 * NodeKeyBytes = [ 0x4E ('N'), pathLenVarInt, packedPathBytes, versionBE8 ]
 * </pre>
 *
 * <p>Path bytes pack two nibbles per byte and omit any Hex-Prefix metadata.
 * Versions are stored as unsigned 64-bit values in big-endian order so that
 * lexicographic ordering matches numeric ordering.</p>
 */
public final class NodeKey implements Comparable<NodeKey> {

    private static final byte PREFIX = 0x4E; // 'N'

    private final NibblePath path;
    private final long version;

    private NodeKey(NibblePath path, long version) {
        this.path = path;
        this.version = version;
    }

    /**
     * Creates a node key for the supplied path/version tuple.
     *
     * @param path    nibble path from root to the node (never null)
     * @param version creation version (must be &ge; 0)
     * @return immutable node key
     */
    public static NodeKey of(NibblePath path, long version) {
        Objects.requireNonNull(path, "path");
        if (version < 0) throw new IllegalArgumentException("version must be >= 0");
        return new NodeKey(path, version);
    }

    /**
     * Decodes a node key from its persisted byte representation.
     *
     * @param bytes encoded form as produced by {@link #toBytes()}
     * @return decoded node key
     */
    public static NodeKey fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length < 1 + 8) {
            throw new IllegalArgumentException("NodeKey encoding too short: " + bytes.length);
        }
        if (bytes[0] != PREFIX) {
            throw new IllegalArgumentException("Unexpected NodeKey prefix: " + bytes[0]);
        }

        int offset = 1;
        VarInts.ReadResult lenResult = VarInts.readUnsignedInt(bytes, offset);
        int nibbleLen = lenResult.value();
        offset = lenResult.nextOffset();

        int packedBytesLen = (nibbleLen + 1) / 2;
        if (packedBytesLen < 0 || offset + packedBytesLen + 8 > bytes.length) {
            throw new IllegalArgumentException("Invalid NodeKey path length");
        }

        byte[] packedPath = Arrays.copyOfRange(bytes, offset, offset + packedBytesLen);
        offset += packedBytesLen;

        byte[] versionBytes = Arrays.copyOfRange(bytes, offset, offset + 8);
        long version = ByteBuffer.wrap(versionBytes).getLong();

        if (nibbleLen == 0) {
            return new NodeKey(NibblePath.EMPTY, version);
        }

        int[] nibbles = Nibbles.toNibbles(packedPath);
        if (nibbleLen < nibbles.length) {
            nibbles = Arrays.copyOf(nibbles, nibbleLen);
        }
        NibblePath path = NibblePath.of(nibbles);
        return new NodeKey(path, version);
    }

    public NibblePath path() {
        return path;
    }

    public long version() {
        return version;
    }

    /**
     * Serializes this key to the on-disk byte format.
     */
    public byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(PREFIX);
        VarInts.writeUnsignedInt(path.length(), out);
        if (!path.isEmpty()) {
            out.writeBytes(Nibbles.fromNibbles(path.getNibbles()));
        }
        out.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(version).array());
        return out.toByteArray();
    }

    /**
     * Returns a lexicographic comparator aligned with the encoded byte ordering.
     */
    @Override
    public int compareTo(NodeKey other) {
        if (this == other) return 0;
        int cmp = comparePath(other);
        if (cmp != 0) return cmp;
        return Long.compareUnsigned(this.version, other.version);
    }

    private int comparePath(NodeKey other) {
        int[] a = this.path.getNibbles();
        int[] b = other.path.getNibbles();
        int min = Math.min(a.length, b.length);
        for (int i = 0; i < min; i++) {
            int diff = Integer.compare(a[i], b[i]);
            if (diff != 0) return diff;
        }
        return Integer.compare(a.length, b.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeKey)) return false;
        NodeKey that = (NodeKey) o;
        return version == that.version && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, version);
    }

    @Override
    public String toString() {
        return "NodeKey{" +
                "path=" + Arrays.toString(path.getNibbles()) +
                ", version=" + Long.toUnsignedString(version) +
                '}';
    }
}
