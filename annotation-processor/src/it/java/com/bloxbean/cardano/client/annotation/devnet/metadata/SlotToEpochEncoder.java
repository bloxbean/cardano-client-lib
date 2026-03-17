package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;
import org.cardanofoundation.conversions.CardanoConverters;

import java.math.BigInteger;

/**
 * Stateful encoder that converts an absolute slot number to its epoch number
 * using {@link CardanoConverters} from the cf-cardano-conversions-java library.
 * <p>
 * This encoder has <strong>no public no-arg constructor</strong> — it requires a
 * {@link CardanoConverters} instance, which must be provided via a
 * {@link com.bloxbean.cardano.client.metadata.annotation.MetadataAdapterResolver}.
 */
public class SlotToEpochEncoder implements MetadataTypeAdapter<Long> {

    private final CardanoConverters converters;

    public SlotToEpochEncoder(CardanoConverters converters) {
        this.converters = converters;
    }

    @Override
    public Object toMetadata(Long absoluteSlot) {
        return BigInteger.valueOf(converters.slot().slotToEpoch(absoluteSlot));
    }

    @Override
    public Long fromMetadata(Object metadata) {
        throw new UnsupportedOperationException(
                "SlotToEpochEncoder is encode-only. Use a separate decoder for epoch→slot conversion.");
    }
}
