package com.bloxbean.cardano.client.tools.readerdiff;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

final class Fixtures {
    /** Yaci's core/src/test/resources/block/preprod292683.txt: a real Babbage block with three transactions. */
    static byte[] block() throws IOException {
        return HexFormat.of().parseHex(blockHex());
    }

    static String blockHex() throws IOException {
        try (InputStream in = Fixtures.class.getResourceAsStream("/block/preprod292683.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }

    private Fixtures() {
    }
}
