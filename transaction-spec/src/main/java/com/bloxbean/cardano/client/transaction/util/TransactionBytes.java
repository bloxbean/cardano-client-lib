package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

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
