package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.Data;

import java.io.ByteArrayInputStream;

/**
 * Utility class to extract different parts of a transaction as raw bytes, without deserializing the entire transaction.
 * This is useful for ensuring that the correct transaction body bytes are signed when signing a transaction.
 * This is important because the deserialization and serialization of a transaction may alter the transaction body bytes.
 *
 */
@Data
public class TransactionBytes {
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
        if (validBytes == null) //Pre Babbage era tx
            return BytesUtil.merge(initialBytes, txBodyBytes, txWitnessBytes, auxiliaryDataBytes);
        else //Post Babbage era tx
            return BytesUtil.merge(initialBytes, txBodyBytes, txWitnessBytes, validBytes, auxiliaryDataBytes);
    }

    /**
     * Returns a new TransactionBytes object with new witnessSet bytes and the rest of the bytes as it is.
     * @param witnessBytes
     * @return a new TransactionBytes object
     */
    public TransactionBytes withNewWitnessSetBytes(byte[] witnessBytes) {
        return new TransactionBytes(initialBytes, txBodyBytes, witnessBytes, validBytes, auxiliaryDataBytes);
    }

    private TransactionBytes extractTransactionBytesFromTx(byte[] txBytes) {
        if (txBytes == null || txBytes.length == 0)
            throw new IllegalArgumentException("Transaction bytes can't be null or empty");

        ByteArrayInputStream bais = new ByteArrayInputStream(txBytes);
        CborDecoder decoder = new CborDecoder(bais);

        //Extract transaction body
        byte tag = (byte) bais.read(); //Skip the first byte as it is a tag
        initialBytes = new byte[]{tag};

        txBodyBytes = nextElementBytes(txBytes, 1, decoder, bais)._1;
        int nextPos = 1 + txBodyBytes.length;
        txWitnessBytes = nextElementBytes(txBytes, nextPos, decoder, bais)._1;
        nextPos = nextPos + txWitnessBytes.length;
        var nextElmTupel = nextElementBytes(txBytes, nextPos, decoder, bais);
        validBytes = null;
        auxiliaryDataBytes = null;
        //Babbage era tx
        if (nextElmTupel._2 == SimpleValue.TRUE || nextElmTupel._2 == SimpleValue.FALSE) {
            validBytes = nextElmTupel._1;
            nextPos = nextPos + validBytes.length;
            auxiliaryDataBytes = nextElementBytes(txBytes, nextPos, decoder, bais)._1;
        } else {
            auxiliaryDataBytes = nextElmTupel._1; //Pre Babbage Era Tx
        }

        return this;
    }

    private static Tuple<byte[], DataItem> nextElementBytes(byte[] txBytes, int startPos, CborDecoder decoder, ByteArrayInputStream bais) {
        DataItem dataItem;
        try {
            dataItem = decoder.decodeNext();
        } catch (CborException e) {
            throw new CborRuntimeException(e);
        }

        int available = bais.available();
        byte[] txBodyRaw = new byte[txBytes.length - available - startPos]; // -1 for the first byte

        //Copy tx body bytes to txBodyRaw
        System.arraycopy(txBytes,startPos,txBodyRaw,0,txBodyRaw.length);
        return new Tuple<>(txBodyRaw, dataItem);
    }

}
