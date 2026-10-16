package com.bloxbean.cardano.client.programmabletoken;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProgrammableTokenProtocolDescriptor {
    String id;
    String contractVersion;
}
