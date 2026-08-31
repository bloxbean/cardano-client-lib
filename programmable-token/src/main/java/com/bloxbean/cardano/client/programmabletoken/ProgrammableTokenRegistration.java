package com.bloxbean.cardano.client.programmabletoken;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Map;

/** Protocol-specific registration declaration carried without coupling the domain API to a CIP. */
@Value
@Builder
public class ProgrammableTokenRegistration {
    @Singular("field")
    Map<String, Object> protocolData;
}
