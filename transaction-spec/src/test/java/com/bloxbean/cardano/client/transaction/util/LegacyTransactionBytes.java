package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

/**
 * The cbor-java based slicing that {@link TransactionBytes} and {@link TransactionUtil#extractTransactionBodyFromTx}
 * used before they moved onto {@code CborSpan}, kept as a test oracle and benchmark baseline. Same logic as the old
 * code; it assumes a one-byte array header and decodes every element into a {@link DataItem} tree.
 */
final class LegacyTransactionBytes {
    final byte[] initialBytes;
    final byte[] txBodyBytes;
    final byte[] txWitnessBytes;
    final byte[] validBytes;
    final byte[] auxiliaryDataBytes;

    LegacyTransactionBytes(byte[] txBytes) {
        ByteArrayInputStream in = new ByteArrayInputStream(txBytes);
        CborDecoder decoder = new CborDecoder(in);
        initialBytes = new byte[]{(byte) in.read()};
        int pos = 1;
        txBodyBytes = next(txBytes, pos, decoder, in, null);
        pos += txBodyBytes.length;
        txWitnessBytes = next(txBytes, pos, decoder, in, null);
        pos += txWitnessBytes.length;
        DataItem[] third = new DataItem[1];
        byte[] thirdBytes = next(txBytes, pos, decoder, in, third);
        if (third[0] == SimpleValue.TRUE || third[0] == SimpleValue.FALSE) {
            validBytes = thirdBytes;
            pos += validBytes.length;
            auxiliaryDataBytes = next(txBytes, pos, decoder, in, null);
        } else {
            validBytes = null;
            auxiliaryDataBytes = thirdBytes;
        }
    }

    static byte[] extractTransactionBodyFromTx(byte[] txBytes) {
        ByteArrayInputStream in = new ByteArrayInputStream(txBytes);
        in.read();
        try {
            new CborDecoder(in).decodeNext();
        } catch (CborException e) {
            throw new IllegalStateException(e);
        }
        return Arrays.copyOfRange(txBytes, 1, txBytes.length - in.available());
    }

    private static byte[] next(byte[] txBytes, int start, CborDecoder decoder, ByteArrayInputStream in, DataItem[] item) {
        try {
            DataItem decoded = decoder.decodeNext();
            if (item != null)
                item[0] = decoded;
        } catch (CborException e) {
            throw new IllegalStateException(e);
        }
        return Arrays.copyOfRange(txBytes, start, txBytes.length - in.available());
    }
}
