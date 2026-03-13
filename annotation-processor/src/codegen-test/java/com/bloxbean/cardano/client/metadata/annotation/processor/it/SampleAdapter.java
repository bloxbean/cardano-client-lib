package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * POJO with a custom adapter field — used to verify adapter codegen integration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@MetadataType(label = 950)
public class SampleAdapter {

    private String name;

    @MetadataField(adapter = EpochSecondsAdapter.class)
    private Instant timestamp;
}
