package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataDecoder;
import com.bloxbean.cardano.client.metadata.annotation.MetadataEncoder;
import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Integration test POJO exercising {@code @MetadataEncoder}/{@code @MetadataDecoder}
 * with stateful adapters resolved via {@code MetadataAdapterResolver}.
 * <p>
 * {@code ScaleEncoder} and {@code ScaleDecoder} have no public no-arg constructor —
 * they require a scale factor argument, demonstrating that the resolver pattern is essential.
 */
@Data
@NoArgsConstructor
@MetadataType(label = 1905)
public class MetadataEncoderDecoder {

    @MetadataField(key = "test_id", required = true)
    private String testId;

    /** Encoder-only: value × 1000 on-chain, built-in deserialization reads raw BigInteger. */
    @MetadataEncoder(ScaleEncoder.class)
    @MetadataField(key = "encoded_val")
    private long encodedValue;

    /** Decoder-only: built-in serialization, value ÷ 1000 on deserialization. */
    @MetadataDecoder(ScaleDecoder.class)
    @MetadataField(key = "decoded_val")
    private long decodedValue;

    /** Both: encoder × 1000, decoder ÷ 1000 — full round-trip. */
    @MetadataEncoder(ScaleEncoder.class)
    @MetadataDecoder(ScaleDecoder.class)
    @MetadataField(key = "round_trip_val")
    private long roundTripValue;

    /** Slot-to-epoch encoder using cf-cardano-conversions-java. Encode-only. */
    @MetadataEncoder(SlotToEpochEncoder.class)
    @MetadataField(key = "epoch_from_slot")
    private long slotForEpoch;

    /** Plain field — no encoder/decoder, baseline. */
    @MetadataField(key = "plain_val")
    private long plainValue;
}
