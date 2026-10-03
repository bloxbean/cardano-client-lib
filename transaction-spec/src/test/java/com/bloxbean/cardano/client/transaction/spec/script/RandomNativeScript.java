package com.bloxbean.cardano.client.transaction.spec.script;

import java.util.Random;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;

/**
 * Seeded generator of native script CBOR in every form the ledger accepts: all six types, definite and indefinite
 * arrays, integer heads of every width (some not minimal), negative and zero {@code m}, and {@code m} above the number of
 * sub-scripts. Key hashes come from a pool of four and slots from 0-20 (and some large ones), so that evaluating against
 * random signatures and validity intervals hits every boundary.
 */
final class RandomNativeScript {
    static final String[] KEY_HASHES = {"00".repeat(28), "11".repeat(28), "22".repeat(28), "33".repeat(28)};

    private final Random random;

    RandomNativeScript(long seed) {
        this.random = new Random(seed);
    }

    byte[] next(int maxDepth) {
        StringBuilder out = new StringBuilder();
        script(out, maxDepth);
        return decodeHexString(out.toString());
    }

    private void script(StringBuilder out, int depth) {
        int type = depth > 0 ? random.nextInt(6) : new int[]{0, 4, 5}[random.nextInt(3)];
        boolean indefinite = random.nextInt(8) == 0;
        out.append(indefinite ? "9f" : type == 3 ? "83" : "82");
        unsigned(out, 0, type);
        switch (type) {
            case 0:
                out.append("581c").append(KEY_HASHES[random.nextInt(KEY_HASHES.length)]);
                break;
            case 3:
                int m = random.nextInt(10) == 0 ? (random.nextBoolean() ? Integer.MIN_VALUE : Integer.MAX_VALUE) : random.nextInt(6) - 2;
                if (m >= 0)
                    unsigned(out, 0, m);
                else
                    unsigned(out, 1, -1L - m);
                list(out, depth);
                break;
            case 4:
            case 5:
                unsigned(out, 0, random.nextInt(10) == 0 ? random.nextLong() & Long.MAX_VALUE : random.nextInt(21));
                break;
            default:
                list(out, depth);
                break;
        }
        if (indefinite)
            out.append("ff");
    }

    private void list(StringBuilder out, int depth) {
        int count = random.nextInt(4);
        boolean indefinite = random.nextInt(6) == 0;
        if (indefinite)
            out.append("9f");
        else
            out.append(String.format("%02x", 0x80 + count));
        for (int i = 0; i < count; i++)
            script(out, depth - 1);
        if (indefinite)
            out.append("ff");
    }

    // A head of major type 0 or 1, sometimes wider than needed.
    private void unsigned(StringBuilder out, int major, long value) {
        int width = value < 24 ? 0 : value < 0x100 ? 1 : value < 0x10000 ? 2 : value < 0x100000000L ? 4 : 8;
        if (width < 8 && random.nextInt(10) == 0)
            width = width == 0 ? 1 : width * 2;
        int base = major << 5;
        switch (width) {
            case 0:
                out.append(String.format("%02x", base + value));
                break;
            case 1:
                out.append(String.format("%02x%02x", base + 24, value));
                break;
            case 2:
                out.append(String.format("%02x%04x", base + 25, value));
                break;
            case 4:
                out.append(String.format("%02x%08x", base + 26, value));
                break;
            default:
                out.append(String.format("%02x%016x", base + 27, value));
                break;
        }
    }
}
