package com.bloxbean.cardano.client.programmabletoken;

import java.util.Set;

/** Recommended protocol-neutral application entry point. */
public interface ProgrammableTokenService {
    ProgrammableTokenProtocolDescriptor protocol();
    Set<ProgrammableTokenCapability> capabilities();
    ProgrammableTokenExtension extension();
}
