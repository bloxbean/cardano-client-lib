package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.address.ByronAddress;
import com.bloxbean.cardano.client.crypto.exception.AddressFormatException;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;

/**
 * Haskell's strict address decoder for outputs from decoder version 9 ({@code fromCborBothAddr} →
 * {@code fromCborRigorousBothAddr False} → {@code decodeAddrStateLenientT False False}, cardano-ledger-core
 * Address.hs:459-476, 643-690), for {@link HaskellPolicy}:
 * <ul>
 *   <li>{@code 0x82} is a Byron address, decoded in full (CRC included) by CCL's {@link ByronAddress}, as the Byron
 *       ledger decodes it ({@code DecCBOR Address});</li>
 *   <li>otherwise no unused header bit may be set ({@code header .&. 0b10001100 == 0}, so types 8–15 and network ids
 *       other than 0/1 fail), then a 28-byte payment hash, then by type a 28-byte stake hash (base), a pointer of
 *       variable-length {@code Word32}/{@code Word16}/{@code Word16} that must fit (types 4, 5; {@code decodePtr},
 *       :769-778, 832-874), or nothing (enterprise);</li>
 *   <li>no byte may be left over.</li>
 * </ul>
 * Every rejection is a {@link LedgerDecodingException} with {@link Reason#ADDRESS}.
 */
final class AddressValidator {

    private static final int BYRON_HEADER = 0x82;
    private static final int UNUSED_HEADER_BITS = 0b1000_1100;
    private static final int HASH_LENGTH = 28;

    private AddressValidator() {
    }

    static void validate(byte[] address) {
        if (address.length == 0) {
            throw reject("empty address");
        }
        int header = address[0] & 0xff;
        if (header == BYRON_HEADER) {
            byron(address);
            return;
        }
        if ((header & UNUSED_HEADER_BITS) != 0) {
            throw reject("Invalid address header, unused bits are set: 0x" + Integer.toHexString(header));
        }
        int offset = 1 + HASH_LENGTH;
        if (address.length < offset) {
            throw reject("address too short for its payment credential");
        }
        boolean base = (header & 0b0100_0000) == 0;
        boolean enterprise = (header & 0b0010_0000) != 0;
        if (base) {
            offset += HASH_LENGTH;
            if (address.length < offset) {
                throw reject("address too short for its stake credential");
            }
        } else if (!enterprise) {
            int[] cursor = {offset};
            varLength(address, cursor, 5, 0b1111_0000, "SlotNo");
            varLength(address, cursor, 3, 0b1111_1100, "TxIx");
            varLength(address, cursor, 3, 0b1111_1100, "CertIx");
            offset = cursor[0];
        }
        if (offset != address.length) {
            throw reject("Left over bytes in address: " + (address.length - offset));
        }
    }

    /**
     * {@code decodeVariableLengthWord16/32}: 7 bits per byte, most significant first, bit 7 set on every byte but the
     * last; at most {@code maxBytes} bytes, and when all of them are used the first byte must satisfy
     * {@code first & mask == 0x80} so the value fits.
     */
    private static void varLength(byte[] address, int[] cursor, int maxBytes, int mask, String name) {
        int start = cursor[0];
        for (int n = 1; ; n++) {
            if (cursor[0] >= address.length) {
                throw reject("Decoding " + name + ": Not enough bytes for decoding");
            }
            int b = address[cursor[0]++] & 0xff;
            if ((b & 0x80) == 0) {
                if (n == maxBytes && ((address[start] & 0xff) & mask) != 0x80) {
                    throw reject("Decoding " + name + ": value does not fit");
                }
                return;
            }
            if (n == maxBytes) {
                throw reject("Decoding " + name + ": too many bytes.");
            }
        }
    }

    /**
     * The envelope and its CRC, a 28-byte root, attributes with definite lengths and strictly increasing keys, a
     * canonical derivation path and protocol magic with nothing after them, and an address type of 0 or 2.
     */
    private static void byron(byte[] address) {
        try {
            new ByronAddress(address);
        } catch (AddressFormatException e) {
            throw new LedgerDecodingException(Reason.ADDRESS, e.getMessage(), e);
        }
    }

    private static LedgerDecodingException reject(String message) {
        return new LedgerDecodingException(Reason.ADDRESS, message);
    }
}
