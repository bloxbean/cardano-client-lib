package com.bloxbean.cardano.client.annotation.devnet.metadata;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base class for inheritance testing in metadata integration tests.
 * Fields here should be included in child converters.
 */
@Data
@NoArgsConstructor
public class NftBaseMetadata {
    private String version;
    private String author;
}
