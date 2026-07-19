package com.bloxbean.cardano.vds.jmt.store;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Stable descriptor for persisted JMT bytes and their cryptographic interpretation.
 */
public final class JmtFormatDescriptor {

    private static final int MAGIC = 0x4A4D5431; // JMT1
    private static final int MAX_ID_BYTES = 128;

    public static final int STORAGE_SCHEMA_VERSION = 1;
    public static final int NODE_ENCODING_VERSION = 1;
    public static final int NODE_KEY_ENCODING_VERSION = 1;
    public static final int RADIX = 16;
    public static final int KEY_HASH_LENGTH = 32;

    private final String profileId;
    private final String hashAlgorithmId;
    private final int hashLength;
    private final boolean persistent;

    private JmtFormatDescriptor(String profileId,
                                String hashAlgorithmId,
                                int hashLength,
                                boolean persistent) {
        this.profileId = requireIdentifier(profileId, "profileId");
        this.hashAlgorithmId = requireIdentifier(hashAlgorithmId, "hashAlgorithmId");
        if (hashLength <= 0 || hashLength > 1024) {
            throw new IllegalArgumentException("hashLength must be between 1 and 1024");
        }
        this.hashLength = hashLength;
        this.persistent = persistent;
    }

    public static JmtFormatDescriptor classicBlake2b256V1() {
        return new JmtFormatDescriptor("classic-radix16-blake2b256-v1", "blake2b-256", 32, true);
    }

    public static JmtFormatDescriptor custom(String profileId,
                                             String hashAlgorithmId,
                                             int hashLength) {
        return new JmtFormatDescriptor(profileId, hashAlgorithmId, hashLength, true);
    }

    /**
     * Descriptor used by legacy constructors whose hash algorithm has no stable identity.
     * Persistent stores reject this descriptor.
     */
    public static JmtFormatDescriptor unversioned() {
        return new JmtFormatDescriptor("unversioned", "unspecified", 32, false);
    }

    public String profileId() {
        return profileId;
    }

    public String hashAlgorithmId() {
        return hashAlgorithmId;
    }

    public int hashLength() {
        return hashLength;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public byte[] encode() {
        byte[] profileBytes = profileId.getBytes(StandardCharsets.UTF_8);
        byte[] hashBytes = hashAlgorithmId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 7 + Short.BYTES * 2
                + profileBytes.length + hashBytes.length + 1);
        buffer.putInt(MAGIC);
        buffer.putInt(STORAGE_SCHEMA_VERSION);
        buffer.putInt(NODE_ENCODING_VERSION);
        buffer.putInt(NODE_KEY_ENCODING_VERSION);
        buffer.putInt(RADIX);
        buffer.putInt(KEY_HASH_LENGTH);
        buffer.putInt(hashLength);
        buffer.put((byte) (persistent ? 1 : 0));
        putString(buffer, profileBytes);
        putString(buffer, hashBytes);
        return buffer.array();
    }

    public static JmtFormatDescriptor decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            if (buffer.remaining() < Integer.BYTES * 7 + 1 + Short.BYTES * 2) {
                throw new IllegalArgumentException("JMT format descriptor is truncated");
            }
            if (buffer.getInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid JMT format descriptor magic");
            }
            requireVersion("storage schema", buffer.getInt(), STORAGE_SCHEMA_VERSION);
            requireVersion("node encoding", buffer.getInt(), NODE_ENCODING_VERSION);
            requireVersion("NodeKey encoding", buffer.getInt(), NODE_KEY_ENCODING_VERSION);
            requireVersion("radix", buffer.getInt(), RADIX);
            requireVersion("key hash length", buffer.getInt(), KEY_HASH_LENGTH);
            int hashLength = buffer.getInt();
            byte persistentByte = buffer.get();
            if (persistentByte != 0 && persistentByte != 1) {
                throw new IllegalArgumentException("Invalid JMT persistent-profile flag");
            }
            String profileId = readString(buffer, "profileId");
            String hashAlgorithmId = readString(buffer, "hashAlgorithmId");
            if (buffer.hasRemaining()) {
                throw new IllegalArgumentException("Trailing bytes in JMT format descriptor");
            }
            return new JmtFormatDescriptor(profileId, hashAlgorithmId, hashLength,
                    persistentByte == 1);
        } catch (java.nio.BufferUnderflowException e) {
            throw new IllegalArgumentException("JMT format descriptor is truncated", e);
        }
    }

    public void requirePersistent() {
        if (!persistent) {
            throw new JmtFormatMismatchException("Persistent JMT stores require an explicit stable "
                    + "JmtProfile; legacy/unversioned constructors are in-memory only");
        }
    }

    private static void putString(ByteBuffer buffer, byte[] bytes) {
        if (bytes.length > MAX_ID_BYTES) {
            throw new IllegalArgumentException("JMT format identifier is too long");
        }
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
    }

    private static String readString(ByteBuffer buffer, String field) {
        int length = Short.toUnsignedInt(buffer.getShort());
        if (length == 0 || length > MAX_ID_BYTES || length > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid JMT " + field + " length: " + length);
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void requireVersion(String name, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalArgumentException("Unsupported JMT " + name + ": " + actual
                    + " (expected " + expected + ")");
        }
    }

    private static String requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_ID_BYTES) {
            throw new IllegalArgumentException(name + " must be at most " + MAX_ID_BYTES
                    + " UTF-8 bytes");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            boolean allowed = ch >= 'a' && ch <= 'z'
                    || ch >= '0' && ch <= '9'
                    || ch == '.' || ch == '_' || ch == '-';
            if (!allowed) {
                throw new IllegalArgumentException(name + " contains unsupported character: " + ch);
            }
        }
        return normalized;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof JmtFormatDescriptor)) {
            return false;
        }
        JmtFormatDescriptor other = (JmtFormatDescriptor) obj;
        return hashLength == other.hashLength
                && persistent == other.persistent
                && profileId.equals(other.profileId)
                && hashAlgorithmId.equals(other.hashAlgorithmId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId, hashAlgorithmId, hashLength, persistent);
    }

    @Override
    public String toString() {
        return "JmtFormatDescriptor{" + profileId + ", hash=" + hashAlgorithmId
                + ", hashLength=" + hashLength + '}';
    }
}
