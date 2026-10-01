package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.Data;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    private static final long BOOTSTRAP_WITNESSES = 2;
    private static final long[] SIGNATURE_FIELDS = {VKEY_WITNESSES, BOOTSTRAP_WITNESSES};
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
     * If the witness set already has a vkey witness for {@code vkey}, the transaction is returned unchanged.
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
        return withNewWitnessSetBytes(addWitness(txWitnessBytes, VKEY_WITNESSES, vkey, witness.toByteArray()));
    }

    /**
     * Returns a new TransactionBytes with the signatures from {@code witnessSet} added to this transaction's witness set:
     * its vkey witnesses (field 0) and bootstrap witnesses (field 2, used to spend from Byron-era addresses). The
     * witnesses this transaction already has (signatures, scripts, datums, redeemers) and every other byte stay exactly
     * as they are, so the transaction id and the script data hash do not change.
     * <p>
     * The typical use is signing with a browser wallet over CIP-30. {@code api.signTx(tx, partialSign)} returns only the
     * witnesses the wallet created, as a {@code transaction_witness_set}, not the signed transaction. For one signature
     * that is {@code {0: [[vkey, signature]]}}:
     * <pre>
     * a1                     map with 1 entry
     *    00                  key 0: vkey witnesses
     *    81                  array with 1 witness
     *       82               [vkey, signature]
     *          58 20 ...     vkey (32 bytes)
     *          58 40 ...     signature (64 bytes)
     * </pre>
     * Adding it to the transaction:
     * <pre>{@code
     * // Backend: build the transaction and keep its exact bytes
     * byte[] txBytes = quickTxBuilder.compose(tx).build().serialize();
     *
     * // Browser, CIP-30: the wallet signs and returns its witness set
     * //   const witnessSetHex = await api.signTx(txHex, true);
     *
     * // Backend: add the wallet's signatures, then submit signedTx
     * byte[] signedTx = new TransactionBytes(txBytes)
     *         .withSignaturesFrom(HexUtil.decodeHexString(witnessSetHex))
     *         .getTxBytes();
     * }</pre>
     * Apply it to the exact bytes the signers were given. The signatures cover the transaction body as encoded, so the
     * transaction must not be rebuilt or re-serialized from a {@code Transaction} object in between. With several signers
     * (multi-signature, several wallets, several rounds), call it once per witness set, in any order. Backend keys can
     * sign the same bytes with
     * {@link com.bloxbean.cardano.client.transaction.TransactionSigner#sign(byte[], com.bloxbean.cardano.client.crypto.SecretKey)}.
     * <p>
     * Each witness is appended the way {@link #withVkeyWitness(byte[], byte[])} appends one: tag 258 and indefinite
     * lengths are kept, counts are rewritten with a minimal header, and a missing field is added at the end of the
     * witness set. A witness whose vkey is already present is skipped, so adding the same witness set twice changes
     * nothing. Any other field in {@code witnessSet} is skipped when it is byte-identical to this transaction's (some
     * wallets return the whole witness set) and rejected otherwise: scripts, datums and redeemers are added by the
     * transaction builder, and adding them here would change bytes covered by the script data hash.
     * <p>
     * Unlike {@link #withNewWitnessSetBytes(byte[])}, which replaces the whole witness set, this keeps the existing
     * witnesses.
     *
     * @param witnessSet CBOR of a transaction witness set, for example the result of CIP-30 {@code signTx}
     * @return a new TransactionBytes with the signatures added
     * @throws CborRuntimeException if either witness set is not a CBOR map with unique unsigned integer keys, a signature
     *                              field is not an array (optionally tagged 258) of witnesses, or {@code witnessSet} has
     *                              another field that differs from this transaction's
     */
    public TransactionBytes withSignaturesFrom(byte[] witnessSet) {
        CborSpan signatures = CborSpan.of(witnessSet);
        CborSpan current = CborSpan.of(txWitnessBytes);
        for (Map.Entry<CborSpan, CborSpan> entry : signatures.entries()) {
            CborSpan keySpan = entry.getKey();
            if (keySpan.tag() != -1 || keySpan.majorType() != 0)
                throw new CborRuntimeException("Witness set keys must be unsigned integers");
            long key = keySpan.asLong();
            if (key == VKEY_WITNESSES || key == BOOTSTRAP_WITNESSES)
                continue;
            Optional<CborSpan> existing = current.field(key);
            if (existing.isEmpty() || !Arrays.equals(existing.get().bytes(), entry.getValue().bytes()))
                throw new CborRuntimeException("Witness set field " + key + " differs from the transaction's; only "
                        + "vkey (0) and bootstrap (2) witnesses are added");
        }

        byte[] witnesses = txWitnessBytes;
        for (long key : SIGNATURE_FIELDS) {
            Optional<CborSpan> field = signatures.field(key);
            if (field.isEmpty())
                continue;
            for (CborSpan witness : setItems(field.get(), key))
                witnesses = addWitness(witnesses, key, witness.get(0).byteString(), witness.bytes());
        }
        return withNewWitnessSetBytes(witnesses);
    }

    // Adds one witness to field 0 or 2 of a witness set, unless the field already has a witness for the same vkey.
    private static byte[] addWitness(byte[] witnessSetBytes, long key, byte[] vkey, byte[] witness) {
        CborSpan witnessSet = CborSpan.of(witnessSetBytes);
        Optional<CborSpan> field = witnessSet.field(key);
        if (field.isPresent()) {
            for (CborSpan existing : setItems(field.get(), key)) {
                if (Arrays.equals(existing.get(0).byteString(), vkey))
                    return witnessSetBytes;
            }
            CborSpan array = field.get().untagIf(SET_TAG);
            return witnessSet.replacing(List.of(array), List.of(appendItem(array, MAJOR_ARRAY, witness)));
        }
        ByteArrayOutputStream entry = new ByteArrayOutputStream();
        writeHead(entry, 0, key);
        writeHead(entry, MAJOR_ARRAY, 1);
        entry.writeBytes(witness);
        return appendItem(witnessSet, MAJOR_MAP, entry.toByteArray());
    }

    // The witnesses in field 0 or 2: an array, optionally tagged 258.
    private static List<CborSpan> setItems(CborSpan field, long key) {
        CborSpan array = field.untagIf(SET_TAG);
        if (array.tag() != -1 || array.majorType() != MAJOR_ARRAY)
            throw new CborRuntimeException("Witness set field " + key + " must be an array, optionally tagged 258");
        return array.items();
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
