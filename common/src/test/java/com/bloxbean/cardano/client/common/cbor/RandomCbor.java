package com.bloxbean.cardano.client.common.cbor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Seeded generator of well-formed CBOR items that uses the non-canonical forms Cardano data may contain: indefinite
 * arrays, maps and chunked strings, non-minimal heads, duplicate map keys, tags at any width and nested tags.
 * Tags are never put directly on {@code false/true/null/undefined}: cbor-java would tag its shared singletons, which
 * changes later decodes. By default tags 30 and 38 (decoded semantically by cbor-java) are never produced; with
 * {@code semanticTags} they are, with valid and invalid payloads, to compare decoders.
 */
final class RandomCbor {
    private static final long[] TAGS = {0, 1, 2, 3, 24, 102, 121, 127, 258, 259, 1280, 1400};

    private final Random random;
    private final boolean semanticTags;

    RandomCbor(long seed) {
        this(seed, false);
    }

    RandomCbor(long seed, boolean semanticTags) {
        this.random = new Random(seed);
        this.semanticTags = semanticTags;
    }

    byte[] next(int maxDepth) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        item(out, maxDepth);
        return out.toByteArray();
    }

    private void item(ByteArrayOutputStream out, int depth) {
        int choice = random.nextInt(depth > 0 ? 10 : 6);
        switch (choice) {
            case 0:
                head(out, 0, randomUnsigned());
                break;
            case 1:
                head(out, 1, randomUnsigned());
                break;
            case 2:
                string(out, 2);
                break;
            case 3:
                string(out, 3);
                break;
            case 4:
            case 5:
                simple(out);
                break;
            case 6:
            case 7:
                container(out, random.nextBoolean() ? 4 : 5, depth);
                break;
            default:
                tag(out, depth);
                break;
        }
    }

    private void container(ByteArrayOutputStream out, int major, int depth) {
        int count = random.nextInt(5);
        boolean indefinite = random.nextInt(3) == 0;
        if (indefinite)
            out.write((major << 5) | 31);
        else
            head(out, major, count);
        int items = major == 5 ? count * 2 : count;
        byte[] duplicateKey = null;
        for (int i = 0; i < items; i++) {
            if (major == 5 && i % 2 == 0 && duplicateKey != null && random.nextInt(4) == 0) {
                out.write(duplicateKey, 0, duplicateKey.length);
                continue;
            }
            int start = out.size();
            item(out, depth - 1);
            if (major == 5 && i % 2 == 0)
                duplicateKey = java.util.Arrays.copyOfRange(out.toByteArray(), start, out.size());
        }
        if (indefinite)
            out.write(0xff);
    }

    private void tag(ByteArrayOutputStream out, int depth) {
        long tag = random.nextInt(5) == 0 ? random.nextLong() >>> random.nextInt(64) : TAGS[random.nextInt(TAGS.length)];
        if (semanticTags && random.nextInt(4) == 0) {
            semanticTag(out, depth);
            return;
        }
        if (tag == 30 || tag == 38)
            tag = 258;
        head(out, 6, tag);
        if (tag == 24) {
            byte[] embedded = next(depth - 1);
            head(out, 2, embedded.length);
            out.write(embedded, 0, embedded.length);
        } else if (tag == 2 || tag == 3) {
            string(out, 2);
        } else {
            // Any item but a simple value (cbor-java would tag its singletons)
            int start = out.size();
            do {
                byte[] payload = next(depth - 1);
                int initial = payload[0] & 0xff;
                if (initial < 0xe0) {
                    out.write(payload, 0, payload.length);
                }
            } while (out.size() == start);
        }
    }

    // Tag 30 (rational) or 38 (language-tagged string), mostly well-typed, sometimes not.
    private void semanticTag(ByteArrayOutputStream out, int depth) {
        boolean rational = random.nextBoolean();
        head(out, 6, rational ? 30 : 38);
        switch (random.nextInt(6)) {
            case 0: { // anything
                byte[] payload = next(depth - 1);
                out.write(payload, 0, payload.length);
                break;
            }
            case 1: // wrong arity
                head(out, 4, 1 + 2 * random.nextInt(2));
                for (int i = 0; i < 3; i++)
                    head(out, 0, random.nextInt(5));
                break;
            default:
                head(out, 4, 2);
                if (rational) {
                    head(out, random.nextBoolean() ? 0 : 1, randomUnsigned());
                    head(out, random.nextBoolean() ? 0 : 1, random.nextInt(8) == 0 ? 0 : 1 + random.nextInt(1000));
                } else {
                    string(out, 3);
                    string(out, 3);
                }
        }
    }

    private void string(ByteArrayOutputStream out, int major) {
        if (random.nextInt(3) == 0) {
            out.write((major << 5) | 31);
            int chunks = random.nextInt(4);
            for (int i = 0; i < chunks; i++)
                definiteString(out, major);
            out.write(0xff);
        } else {
            definiteString(out, major);
        }
    }

    private void definiteString(ByteArrayOutputStream out, int major) {
        int length = random.nextInt(random.nextInt(8) == 0 ? 300 : 40);
        byte[] payload = new byte[length];
        if (major == 3) {
            for (int i = 0; i < length; i++)
                payload[i] = (byte) ('a' + random.nextInt(26));
            payload = new String(payload, StandardCharsets.US_ASCII).getBytes(StandardCharsets.UTF_8);
        } else {
            random.nextBytes(payload);
        }
        head(out, major, payload.length);
        out.write(payload, 0, payload.length);
    }

    private void simple(ByteArrayOutputStream out) {
        switch (random.nextInt(6)) {
            case 0:
                out.write(0xf4 + random.nextInt(4)); // false, true, null, undefined
                break;
            case 1:
                out.write(0xe0 + random.nextInt(20)); // unassigned simple values 0..19
                break;
            case 2:
                out.write(0xf8); // two-byte simple value: 32..255 (below 32 is not well-formed)
                out.write(32 + random.nextInt(224));
                break;
            default:
                int width = 1 << random.nextInt(3); // half, single, double float
                out.write(0xf8 + Integer.numberOfTrailingZeros(width) + 1);
                for (int i = 0; i < width * 2; i++)
                    out.write(random.nextInt(256));
                break;
        }
    }

    private long randomUnsigned() {
        switch (random.nextInt(5)) {
            case 0:
                return random.nextInt(24);
            case 1:
                return random.nextInt(256);
            case 2:
                return random.nextInt(65536);
            case 3:
                return random.nextInt() & 0xffffffffL;
            default:
                return random.nextLong();
        }
    }

    /**
     * Writes a head with the minimal width for {@code value}, or sometimes a wider (non-minimal) one.
     */
    private void head(ByteArrayOutputStream out, int major, long value) {
        int minimal;
        if (value >= 0 && value < 24)
            minimal = -1;
        else if (value >= 0 && value < 256)
            minimal = 0;
        else if (value >= 0 && value < 65536)
            minimal = 1;
        else if (value >= 0 && value < 4294967296L)
            minimal = 2;
        else
            minimal = 3;
        int width = minimal;
        if (random.nextInt(4) == 0)
            width = Math.min(3, minimal + 1 + random.nextInt(3));
        if (width < 0) {
            out.write((major << 5) | (int) value);
            return;
        }
        out.write((major << 5) | (24 + width));
        int bytes = 1 << width;
        for (int i = bytes - 1; i >= 0; i--)
            out.write((int) (value >>> (8 * i)) & 0xff);
    }
}
