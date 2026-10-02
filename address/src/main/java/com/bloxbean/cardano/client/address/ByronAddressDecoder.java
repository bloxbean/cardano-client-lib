package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.crypto.exception.AddressFormatException;

import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Strict decoder for the binary form of a Byron address.
 * <p>
 * Follows the Byron ledger decoder ({@code DecCBOR Address} in cardano-ledger's Byron era, decoded with the
 * Byron protocol version):
 * <pre>
 * address    = [ #6.24(bytes .cbor payload), crc32 : uint .size 4 ]
 * payload    = [ root : bytes .size 28, attributes, type : 0 / 2 ]
 * attributes = { * uint .le 255 =&gt; bytes }
 *              ; 1 =&gt; bytes .cbor bytes   (encrypted HD derivation path)
 *              ; 2 =&gt; bytes .cbor uint32  (protocol magic, absent on mainnet)
 * </pre>
 * Arrays, maps and byte strings must have definite lengths, attribute keys must be strictly increasing, the
 * address type, protocol magic and derivation path must be canonically encoded, and no item may be followed by
 * trailing bytes. Attributes with other keys are accepted without being interpreted, like the ledger does.
 * Like the Shelley-era ledger, the address must start with the byte {@code 0x82} (array of two items).
 */
final class ByronAddressDecoder {
    static final int ROOT_LENGTH = 28;

    private static final byte HEADER = (byte) 0x82;
    private static final long CBOR_IN_CBOR_TAG = 24;
    private static final long ATTR_DERIVATION_PATH = 1;
    private static final long ATTR_PROTOCOL_MAGIC = 2;
    private static final long MAX_UINT8 = 0xFFL;
    private static final long MAX_UINT32 = 0xFFFFFFFFL;

    private ByronAddressDecoder() {
    }

    static Decoded decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            throw invalid("empty address");
        if (bytes[0] != HEADER)
            throw invalid("address must start with 0x82");

        CborReader envelope = new CborReader(bytes);
        envelope.readArrayHeader(2);
        long tag = envelope.readTag();
        if (tag != CBOR_IN_CBOR_TAG)
            throw invalid("expected tag 24, found tag " + Long.toUnsignedString(tag));
        byte[] payload = envelope.readBytes(false);
        long expectedCrc = envelope.readUint(MAX_UINT32, false);
        envelope.requireEnd();

        CRC32 crc32 = new CRC32();
        crc32.update(payload);
        if (crc32.getValue() != expectedCrc)
            throw invalid("CRC32 checksum mismatch");

        CborReader reader = new CborReader(payload);
        reader.readArrayHeader(3);
        byte[] root = reader.readBytes(false);
        if (root.length != ROOT_LENGTH)
            throw invalid("root must be " + ROOT_LENGTH + " bytes, found " + root.length);

        byte[] derivationPath = null;
        Long protocolMagic = null;
        int attributeCount = reader.readMapHeader();
        long previousKey = -1;
        for (int i = 0; i < attributeCount; i++) {
            long key = reader.readUint(MAX_UINT8, false);
            if (key <= previousKey)
                throw invalid("attribute keys must be strictly increasing");
            previousKey = key;

            byte[] value = reader.readBytes(false);
            if (key == ATTR_DERIVATION_PATH) {
                CborReader nested = new CborReader(value);
                derivationPath = nested.readBytes(true);
                nested.requireEnd();
            } else if (key == ATTR_PROTOCOL_MAGIC) {
                CborReader nested = new CborReader(value);
                protocolMagic = nested.readUint(MAX_UINT32, true);
                nested.requireEnd();
            }
        }

        long typeValue = reader.readUint(MAX_UINT8, true);
        ByronAddressType type = ByronAddressType.fromValue(typeValue);
        if (type == null)
            throw invalid("unknown address type " + typeValue);
        reader.requireEnd();

        return new Decoded(root, derivationPath, protocolMagic, type);
    }

    private static AddressFormatException invalid(String reason) {
        return new AddressFormatException("Invalid Byron address: " + reason);
    }

    static final class Decoded {
        final byte[] root;
        final byte[] derivationPath;
        final Long protocolMagic;
        final ByronAddressType type;

        private Decoded(byte[] root, byte[] derivationPath, Long protocolMagic, ByronAddressType type) {
            this.root = root;
            this.derivationPath = derivationPath;
            this.protocolMagic = protocolMagic;
            this.type = type;
        }
    }

    /**
     * Minimal CBOR reader for the definite-length items a Byron address is made of.
     * <p>
     * cbor-java (co.nstant.in.cbor), used elsewhere in CCL, is not used here because its decoder cannot enforce the
     * ledger rules above and is unsafe on untrusted input:
     * <ul>
     *     <li>it discards how an integer or length was encoded ({@code 18 02} and {@code 02} decode to the same value),
     *     so the canonical encoding of the address type, protocol magic and derivation path cannot be checked</li>
     *     <li>an indefinite-length byte string is merged into one and not marked as chunked</li>
     *     <li>duplicate map keys are silently merged</li>
     *     <li>it allocates from a declared length before checking the input: {@code 5a 7fffffff} throws
     *     {@link OutOfMemoryError}, so a 9 character Base58 string ({@code 27uung4Ye}) could exhaust the heap</li>
     * </ul>
     * TODO: replace with a shared CBOR utility (bounded, definite-length, encoding-width and offset aware) once one exists
     * in a common module. Other components need the same checks, e.g. {@code BoundedCbor} in jellyfish-merkle.
     */
    private static final class CborReader {
        private static final int MAJOR_UINT = 0;
        private static final int MAJOR_BYTES = 2;
        private static final int MAJOR_ARRAY = 4;
        private static final int MAJOR_MAP = 5;
        private static final int MAJOR_TAG = 6;

        private final byte[] data;
        private int pos;

        CborReader(byte[] data) {
            this.data = data;
        }

        long readUint(long max, boolean canonical) {
            long value = readArgument(MAJOR_UINT, "unsigned integer", canonical);
            if (value < 0 || value > max)
                throw invalid("unsigned integer out of range");
            return value;
        }

        long readTag() {
            return readArgument(MAJOR_TAG, "tag", false);
        }

        byte[] readBytes(boolean canonical) {
            int length = readLength(MAJOR_BYTES, "byte string", canonical);
            byte[] bytes = Arrays.copyOfRange(data, pos, pos + length);
            pos += length;
            return bytes;
        }

        void readArrayHeader(int expectedLength) {
            int length = readLength(MAJOR_ARRAY, "array", false);
            if (length != expectedLength)
                throw invalid("expected array of " + expectedLength + " items, found " + length);
        }

        int readMapHeader() {
            return readLength(MAJOR_MAP, "map", false);
        }

        void requireEnd() {
            if (pos != data.length)
                throw invalid("unexpected trailing bytes");
        }

        private int readLength(int majorType, String name, boolean canonical) {
            long length = readArgument(majorType, name, canonical);
            if (length < 0 || length > data.length - pos)
                throw invalid(name + " length exceeds available data");
            return (int) length;
        }

        /**
         * Reads an item header of the given major type and returns its argument (value, length or tag number).
         * An 8-byte argument above {@link Long#MAX_VALUE} is returned as a negative number.
         */
        private long readArgument(int majorType, String name, boolean canonical) {
            int initialByte = readByte();
            if (initialByte >>> 5 != majorType)
                throw invalid("expected " + name + " at offset " + (pos - 1));

            int additionalInfo = initialByte & 0x1F;
            long value;
            boolean shortest;
            if (additionalInfo < 24) {
                value = additionalInfo;
                shortest = true;
            } else if (additionalInfo == 24) {
                value = readBigEndian(1);
                shortest = value >= 24;
            } else if (additionalInfo == 25) {
                value = readBigEndian(2);
                shortest = value > 0xFFL;
            } else if (additionalInfo == 26) {
                value = readBigEndian(4);
                shortest = value > 0xFFFFL;
            } else if (additionalInfo == 27) {
                value = readBigEndian(8);
                shortest = Long.compareUnsigned(value, MAX_UINT32) > 0;
            } else if (additionalInfo == 31) {
                throw invalid("indefinite-length " + name + " is not allowed");
            } else {
                throw invalid("reserved additional info in " + name + " header");
            }

            if (canonical && !shortest)
                throw invalid("non-canonical encoding of " + name);
            return value;
        }

        private long readBigEndian(int size) {
            if (size > data.length - pos)
                throw invalid("unexpected end of data");
            long value = 0;
            for (int i = 0; i < size; i++)
                value = (value << 8) | (data[pos++] & 0xFF);
            return value;
        }

        private int readByte() {
            if (pos >= data.length)
                throw invalid("unexpected end of data");
            return data[pos++] & 0xFF;
        }
    }
}
