package com.bloxbean.cardano.client.crypto.vrf;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Result of a VRF verification.
 */
@Getter
@AllArgsConstructor
public class VrfResult {
    private final boolean valid;
    private final byte[] output;

    public static VrfResult invalid() {
        return new VrfResult(false, null);
    }

    public static VrfResult valid(byte[] output) {
        return new VrfResult(true, output);
    }
}
