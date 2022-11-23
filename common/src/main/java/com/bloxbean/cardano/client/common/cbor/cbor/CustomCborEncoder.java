package com.bloxbean.cardano.client.common.cbor.cbor;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.Tag;

import java.io.OutputStream;

import static co.nstant.in.cbor.model.MajorType.MAP;

/**
 * A custom CborEncoder impl to handle serialization of {@link SortedMap} when canonical cbor is set in CborEncoder level.
 * This class exists to handle the scenario where a Map's key is already sorted by the caller, so
 * encoder doesn't need to sort it again even if canonical = true.
 */
public class CustomCborEncoder extends CborEncoder {

    private CustomMapEncoder customMapEncoder;

    /**
     * Initialize a new encoder which writes the binary encoded data to an
     * {@link OutputStream}.
     *
     * @param outputStream the {@link OutputStream} to write the encoded data to
     */
    public CustomCborEncoder(OutputStream outputStream) {
        super(outputStream);
        this.customMapEncoder = new CustomMapEncoder(this, outputStream);
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
        //If Map type, handle it here. Otherwise, delegates to default implementation in CborEncoder
        if (dataItem.getMajorType().equals(MAP)) {
            if (dataItem == null) {
                dataItem = SimpleValue.NULL;
            }

            if (dataItem.hasTag()) {
                Tag tagDi = dataItem.getTag();
                encode(tagDi);
            }

            customMapEncoder.encode((Map) dataItem);
        } else {
            super.encode(dataItem);
        }
    }

}
