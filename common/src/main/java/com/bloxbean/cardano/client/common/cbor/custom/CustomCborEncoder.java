package com.bloxbean.cardano.client.common.cbor.custom;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;

import java.io.OutputStream;

import static co.nstant.in.cbor.model.MajorType.BYTE_STRING;
import static co.nstant.in.cbor.model.MajorType.MAP;

/**
 * A custom CborEncoder impl to handle serialization of {@link SortedMap} when canonical cbor is set in CborEncoder level.
 * This class exists to handle the scenario where a Map's key is already sorted by the caller, so
 * encoder doesn't need to sort it again even if canonical = true.
 *
 * It is also used to handle chunked ByteStrings in PlutusData serialization/deserialization.
 */
public class CustomCborEncoder extends CborEncoder {

    private CustomMapEncoder customMapEncoder;
    private CustomByteStringEncoder chunkByteStringEncoder;

    /**
     * Initialize a new encoder which writes the binary encoded data to an
     * {@link OutputStream}.
     *
     * @param outputStream the {@link OutputStream} to write the encoded data to
     */
    public CustomCborEncoder(OutputStream outputStream) {
        super(outputStream);
        this.customMapEncoder = new CustomMapEncoder(this, outputStream);
        this.chunkByteStringEncoder = new CustomByteStringEncoder(this, outputStream);
    }

    /**
     * Encode a single {@link DataItem}.
     *
     * @param dataItem the {@link DataItem} to encode. If null, encoder encodes a
     *                 {@link SimpleValue} NULL value.
     * @throws CborException if {@link DataItem} could not be encoded or there was
     *                       an problem with the {@link OutputStream}.
     */
    public void encode(DataItem dataItem) throws CborException {
        if (dataItem == null) {
            dataItem = SimpleValue.NULL;
        }

        //If Map type or ByteString, handle it here. Otherwise, delegates to default implementation in CborEncoder
        if (dataItem.getMajorType().equals(MAP)) {
            if (dataItem.hasTag()) {
                Tag tagDi = dataItem.getTag();
                encode(tagDi);
            }

            customMapEncoder.encode((Map) dataItem);
        } else if (dataItem.getMajorType().equals(BYTE_STRING)) {
            if (dataItem.hasTag()) {
                Tag tagDi = dataItem.getTag();
                encode(tagDi);
            }

            chunkByteStringEncoder.encode((ByteString) dataItem);
        } else {
            super.encode(dataItem);
        }
    }

}
