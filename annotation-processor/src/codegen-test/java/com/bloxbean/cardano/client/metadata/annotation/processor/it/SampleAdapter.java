package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataDecoder;
import com.bloxbean.cardano.client.metadata.annotation.MetadataEncoder;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * POJO with a custom encoder/decoder field — used to verify adapter codegen integration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@MetadataType(label = 950)
public class SampleAdapter {

    private String name;

    @MetadataEncoder(EpochSecondsAdapter.class)
    @MetadataDecoder(EpochSecondsAdapter.class)
    private Instant timestamp;
}
