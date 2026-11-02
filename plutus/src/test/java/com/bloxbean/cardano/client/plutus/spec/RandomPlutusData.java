package com.bloxbean.cardano.client.plutus.spec;

import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * Seeded generator of Plutus data CBOR in every encoding the ledger accepts and some it does not: constructor tags
 * 121-127, 1280-1400 and 102, integers of any width and as bignums (tags 2/3, chunked or not), byte strings definite and
 * chunked, definite and indefinite lists and maps, and repeated map keys.
 */
final class RandomPlutusData {
    private final Random random;

    RandomPlutusData(long seed) {
        this.random = new Random(seed);
    }

    byte[] next(int maxDepth) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        data(out, maxDepth);
        return out.toByteArray();
    }

    private void data(ByteArrayOutputStream out, int depth) {
        switch (random.nextInt(depth > 0 ? 6 : 2)) {
            case 0:
                integer(out);
                break;
            case 1:
                // sometimes over 64 bytes, so that map keys are chunked when serialized
                bytes(out, random.nextInt(6) == 0 ? 65 + random.nextInt(60) : 2);
                break;
            case 2:
                list(out, depth);
                break;
            case 3:
                map(out, depth);
                break;
            default:
                constr(out, depth);
                break;
        }
    }

    private void integer(ByteArrayOutputStream out) {
        switch (random.nextInt(4)) {
            case 0:
                head(out, random.nextBoolean() ? 0 : 1, random.nextInt(30));
                break;
            case 1:
                head(out, random.nextBoolean() ? 0 : 1, random.nextLong() >>> random.nextInt(64));
                break;
            default: // bignum, sometimes small, sometimes over 64 bytes, sometimes chunked
                head(out, 6, random.nextBoolean() ? 2 : 3);
                bytes(out, random.nextInt(4) == 0 ? 70 + random.nextInt(60) : 1 + random.nextInt(20));
                break;
        }
    }

    private void bytes(ByteArrayOutputStream out, int length) {
        byte[] payload = new byte[random.nextInt(3) == 0 ? length : random.nextInt(length + 1)];
        random.nextBytes(payload);
        if (random.nextInt(4) == 0) {
            out.write(0x5f);
            int pos = 0;
            while (pos < payload.length) {
                int chunk = Math.min(payload.length - pos, 1 + random.nextInt(64));
                head(out, 2, chunk);
                out.write(payload, pos, chunk);
                pos += chunk;
            }
            out.write(0xff);
        } else {
            head(out, 2, payload.length);
            out.write(payload, 0, payload.length);
        }
    }

    private void list(ByteArrayOutputStream out, int depth) {
        int count = random.nextInt(5);
        boolean indefinite = random.nextBoolean();
        if (indefinite)
            out.write(0x9f);
        else
            head(out, 4, count);
        for (int i = 0; i < count; i++)
            data(out, depth - 1);
        if (indefinite)
            out.write(0xff);
    }

    private void map(ByteArrayOutputStream out, int depth) {
        int count = random.nextInt(4);
        boolean indefinite = random.nextInt(4) == 0;
        if (indefinite)
            out.write(0xbf);
        else
            head(out, 5, count);
        byte[] previousKey = null;
        for (int i = 0; i < count; i++) {
            byte[] key = previousKey != null && random.nextInt(4) == 0 ? previousKey : next(depth - 1);
            out.write(key, 0, key.length);
            data(out, depth - 1);
            previousKey = key;
        }
        if (indefinite)
            out.write(0xff);
    }

    private void constr(ByteArrayOutputStream out, int depth) {
        switch (random.nextInt(3)) {
            case 0:
                head(out, 6, 121 + random.nextInt(7));
                list(out, depth);
                break;
            case 1:
                head(out, 6, 1280 + random.nextInt(121));
                list(out, depth);
                break;
            default:
                head(out, 6, 102);
                head(out, 4, 2);
                head(out, 0, random.nextBoolean() ? random.nextInt(200) : random.nextLong() >>> 1);
                list(out, depth);
                break;
        }
    }

    // A head with the minimal width, or sometimes a wider one.
    private void head(ByteArrayOutputStream out, int major, long value) {
        int width;
        if (value >= 0 && value < 24)
            width = -1;
        else if (value >= 0 && value < 256)
            width = 0;
        else if (value >= 0 && value < 65536)
            width = 1;
        else if (value >= 0 && value < 4294967296L)
            width = 2;
        else
            width = 3;
        if (random.nextInt(5) == 0)
            width = Math.min(3, width + 1);
        if (width < 0) {
            out.write((major << 5) | (int) value);
            return;
        }
        out.write((major << 5) | (24 + width));
        for (int i = (1 << width) - 1; i >= 0; i--)
            out.write((int) (value >>> (8 * i)) & 0xff);
    }
}
