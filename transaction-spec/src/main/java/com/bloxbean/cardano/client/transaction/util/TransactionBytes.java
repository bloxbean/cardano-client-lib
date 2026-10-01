package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.Data;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Utility class to extract different parts of a transaction as raw bytes, without deserializing the entire transaction.
 * This is useful for ensuring that the correct transaction body bytes are signed when signing a transaction.
 * This is important because the deserialization and serialization of a transaction may alter the transaction body bytes.
 * <p>
 * The parts are slices of the original bytes found with {@link CborSpan}, which walks the transaction without building
 * a CBOR tree and without recursion: witness sets of any nesting depth are supported, the transaction array header may
 * have any width, and an indefinite-length transaction array keeps its closing BREAK in {@link #getTxBytes()}.
 * Bytes after the transaction array are ignored.
 */
@Data
public class TransactionBytes {
    private static final byte INDEFINITE_ARRAY = (byte) 0x9f;
    private static final byte[] BREAK = {(byte) 0xff};
    private static final byte[] NONE = {};
    private static final int MAJOR_BYTES = 2;
    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_MAP = 5;
    private static final long VKEY_WITNESSES = 0;
    private static final long SET_TAG = 258;

    private byte[] initialBytes;
    private byte[] txBodyBytes;
    private byte[] txWitnessBytes;
    private byte[] validBytes;
    private byte[] auxiliaryDataBytes;

    /**
     * Extract and create TransactionBytes from transaction bytes
     * @param txBytes
     */
    public TransactionBytes(byte[] txBytes) {
        extractTransactionBytesFromTx(txBytes);
    }

    private TransactionBytes(byte[] initialBytes, byte[] txBodyBytes, byte[] txWitnessBytes, byte[] validBytes,
                             byte[] auxiliaryDataBytes) {
        this.initialBytes = initialBytes;
        this.txBodyBytes = txBodyBytes;
        this.txWitnessBytes = txWitnessBytes;
        this.validBytes = validBytes;
        this.auxiliaryDataBytes = auxiliaryDataBytes;
    }

    /**
     * Returns the final transaction bytes. This method merges all parts of the transaction to final bytes.
     * @return transaction bytes
     */
    public byte[] getTxBytes() {
        byte[] end = initialBytes.length == 1 && initialBytes[0] == INDEFINITE_ARRAY ? BREAK : NONE;
        if (validBytes == null) //[body, witnesses, auxiliary data]
            return BytesUtil.merge(initialBytes, txBodyBytes, txWitnessBytes, auxiliaryDataBytes, end);
        else //[body, witnesses, isValid, auxiliary data]
            return BytesUtil.merge(initialBytes, txBodyBytes, txWitnessBytes, validBytes, auxiliaryDataBytes, end);
    }

    /**
     * Returns a new TransactionBytes object with new witnessSet bytes and the rest of the bytes as it is.
     * @param witnessBytes
     * @return a new TransactionBytes object
     */
    public TransactionBytes withNewWitnessSetBytes(byte[] witnessBytes) {
        return new TransactionBytes(initialBytes, txBodyBytes, witnessBytes, validBytes, auxiliaryDataBytes);
    }

    /**
     * Returns a new TransactionBytes with the vkey witness {@code [vkey, signature]} added to the witness set, and every
     * other byte of the transaction unchanged.
     * <p>
     * The witness is appended to the vkey witnesses (witness set field 0). An optional tag 258 is kept as encoded; a
     * definite count is rewritten with a minimal header, which grows by one width step when the count reaches 24, 256
     * or 65,536; an indefinite array gets the witness before its BREAK. When field 0 is absent, the entry
     * {@code 0 => [witness]} is added at the end of the witness set, whose count is rewritten the same way. All other
     * witness fields (scripts, datums, redeemers) keep their original bytes, so the script data hash stays valid.
     *
     * @param vkey      verification key
     * @param signature signature of the transaction body hash
     * @return a new TransactionBytes with the new witness set
     * @throws CborRuntimeException if the witness set is not a CBOR map, has a duplicate key, or its field 0 is not an
     *                              array (optionally tagged 258)
     */
    public TransactionBytes withVkeyWitness(byte[] vkey, byte[] signature) {
        ByteArrayOutputStream witness = new ByteArrayOutputStream();
        writeHead(witness, MAJOR_ARRAY, 2);
        writeHead(witness, MAJOR_BYTES, vkey.length);
        witness.writeBytes(vkey);
        writeHead(witness, MAJOR_BYTES, signature.length);
        witness.writeBytes(signature);
        return withNewWitnessSetBytes(appendVkeyWitness(CborSpan.of(txWitnessBytes), witness.toByteArray()));
    }

    private static byte[] appendVkeyWitness(CborSpan witnessSet, byte[] witness) {
        Optional<CborSpan> vkeyWitnesses = witnessSet.field(VKEY_WITNESSES);
        if (vkeyWitnesses.isPresent()) {
            CborSpan array = vkeyWitnesses.get().untagIf(SET_TAG);
            if (array.tag() != -1 || array.majorType() != MAJOR_ARRAY)
                throw new CborRuntimeException("Witness set field 0 must be an array, optionally tagged 258");
            return witnessSet.replacing(List.of(array), List.of(appendItem(array, MAJOR_ARRAY, witness)));
        }
        ByteArrayOutputStream entry = new ByteArrayOutputStream();
        writeHead(entry, 0, VKEY_WITNESSES);
        writeHead(entry, MAJOR_ARRAY, 1);
        entry.writeBytes(witness);
        return appendItem(witnessSet, MAJOR_MAP, entry.toByteArray());
    }

    // Appends encoded content to an untagged array or map: before the BREAK of an indefinite container, otherwise
    // after the last item with the count (items or pairs) incremented in a minimal header.
    private static byte[] appendItem(CborSpan container, int major, byte[] content) {
        byte[] buffer = container.buffer();
        int end = container.offset() + container.length();
        ByteArrayOutputStream out = new ByteArrayOutputStream(container.length() + content.length + 8);
        if (container.isIndefinite()) {
            out.write(buffer, container.offset(), container.length() - 1);
            out.writeBytes(content);
            out.writeBytes(BREAK);
        } else {
            int contentStart = container.offset() + container.headerLength();
            writeHead(out, major, container.size() + 1L);
            out.write(buffer, contentStart, end - contentStart);
            out.writeBytes(content);
        }
        return out.toByteArray();
    }

    private static void writeHead(ByteArrayOutputStream out, int major, long value) { // value < 2^32
        int type = major << 5;
        if (value < 24) {
            out.write(type | (int) value);
        } else if (value < 0x100) {
            out.write(type | 24);
            out.write((int) value);
        } else if (value < 0x10000) {
            out.write(type | 25);
            out.write((int) (value >> 8));
            out.write((int) value);
        } else {
            out.write(type | 26);
            for (int shift = 24; shift >= 0; shift -= 8)
                out.write((int) (value >> shift));
        }
    }

    private void extractTransactionBytesFromTx(byte[] txBytes) {
        if (txBytes == null || txBytes.length == 0)
            throw new IllegalArgumentException("Transaction bytes can't be null or empty");

        CborSpan tx = CborSpan.at(txBytes, 0);
        List<CborSpan> items = tx.items();
        if (items.size() != 3 && items.size() != 4)
            throw new CborRuntimeException("Expected a transaction array of 3 or 4 elements, but found " + items.size());

        initialBytes = Arrays.copyOf(txBytes, tx.headerLength());
        txBodyBytes = items.get(0).bytes();
        txWitnessBytes = items.get(1).bytes();
        if (items.size() == 4) {
            CborSpan valid = items.get(2);
            valid.asBoolean(); //Element 2 of a 4-element transaction must be the isValid flag
            validBytes = valid.bytes();
            auxiliaryDataBytes = items.get(3).bytes();
        } else {
            validBytes = null;
            auxiliaryDataBytes = items.get(2).bytes();
        }
    }

}
