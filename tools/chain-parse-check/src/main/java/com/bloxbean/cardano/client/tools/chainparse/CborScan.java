package com.bloxbean.cardano.client.tools.chainparse;

import java.util.Arrays;

/**
 * An independent, iterative byte-level scan of one CBOR item (no CCL code): its nesting depth, and whether it is
 * "canonical" in the sense the CCL models re-encode it.
 * <ul>
 *     <li>every head is minimal (integers, lengths, counts, tags);</li>
 *     <li>map keys are strictly ascending bytewise (the order CCL's canonical encoder sorts them in), so no duplicates;</li>
 *     <li>no indefinite maps;</li>
 *     <li>native scripts ({@code allowIndefiniteArrays = false}): no indefinite item at all;</li>
 *     <li>Plutus data ({@code allowIndefiniteArrays = true}): indefinite lists and constructor fields are kept by the
 *     model (chunked flag); a definite byte string is at most 64 bytes, an indefinite one is 64-byte chunks with a
 *     last chunk of 1-64 and more than 64 bytes in total, as the Plutus encoder writes them; no text strings; tags
 *     only for bignums (2, 3 on a byte string) and constructors (121-127, 1280-1400, 102 on an array). As plutus-core
 *     {@code encodeData} writes them: a bignum is an integer outside -2^64..2^64-1 (so more than 8 bytes) in minimal
 *     bytes, without a leading zero; constructors 0-127 use their compact tags, not the general form
 *     {@code 102([alternative, fields])}, whose outer array is definite.</li>
 * </ul>
 */
final class CborScan {
    final boolean canonical;
    final int maxDepth;
    final boolean bignum; // contains a bignum (tag 2/3 on a byte string)

    private CborScan(boolean canonical, int maxDepth, boolean bignum) {
        this.canonical = canonical;
        this.maxDepth = maxDepth;
        this.bignum = bignum;
    }

    private static final int ARRAY = 1;
    private static final int MAP = 2;
    private static final int BYTES = 3;
    private static final int TEXT = 4;

    static CborScan scan(byte[] b, boolean plutusData) {
        int n = b.length;
        // frame stacks
        int cap = 64;
        int[] kind = new int[cap];
        long[] remaining = new long[cap];   // -1 = indefinite
        boolean[] expectKey = new boolean[cap];
        int[] keyStart = new int[cap];
        int[] prevKeyStart = new int[cap];
        int[] prevKeyEnd = new int[cap];
        long[] chunkTotal = new long[cap];
        boolean[] lastChunkShort = new boolean[cap];
        int depth = 0;
        int maxDepth = 0;
        boolean canonical = true;
        boolean pendingTag = false;
        boolean pendingBignum = false;
        boolean pendingConstr = false;
        boolean bignum = false;
        int generalForm = 0; // 1: after tag 102, 2: after its [alternative, fields] head
        int pos = 0;
        while (pos < n) {
            int initial = b[pos] & 0xff;
            if (initial == 0xff) { // break: closes the innermost indefinite frame
                pos++;
                int k = kind[depth];
                if (k == BYTES && plutusData && (chunkTotal[depth] <= 64))
                    canonical = false;
                depth--;
                // the container item is complete
                int[] r = complete(depth, pos, kind, remaining, expectKey, keyStart, prevKeyStart, prevKeyEnd, b);
                depth = r[0];
                if (r[1] == 0) canonical = false;
                continue;
            }
            if (depth > 0 && kind[depth] == MAP && expectKey[depth] && !pendingTag)
                keyStart[depth] = pos;
            int major = initial >>> 5;
            int ai = initial & 0x1f;
            long arg;
            int hl;
            if (ai < 24) { arg = ai; hl = 1; }
            else if (ai == 24) { arg = b[pos + 1] & 0xff; hl = 2; if (arg < 24 && major != 7) canonical = false; }
            else if (ai == 25) { arg = be(b, pos + 1, 2); hl = 3; if (arg < 256 && major != 7) canonical = false; }
            else if (ai == 26) { arg = be(b, pos + 1, 4); hl = 5; if (arg < 65536 && major != 7) canonical = false; }
            else if (ai == 27) { arg = be(b, pos + 1, 8); hl = 9; if (arg >= 0 && arg < (1L << 32) && major != 7) canonical = false; }
            else if (ai == 31) { arg = -1; hl = 1; }
            else throw new IllegalArgumentException("reserved additional info at " + pos);
            if (major == 6) { // a tag prefixes the next item
                pos += hl;
                if (plutusData && pendingTag) canonical = false; // the model keeps one tag at most
                pendingTag = true;
                pendingBignum = arg == 2 || arg == 3;
                pendingConstr = arg == 102 || (arg >= 121 && arg <= 127) || (arg >= 1280 && arg <= 1400);
                if (plutusData && !pendingBignum && !pendingConstr) canonical = false;
                generalForm = arg == 102 ? 1 : 0;
                continue;
            }
            if (plutusData && ((pendingBignum && major != 2) || (pendingConstr && major != 4) || major == 3))
                canonical = false; // the model drops such a tag, and writes a text string as bytes
            // constructor alternatives 0-127 have compact tags (121-127, 1280-1400); tag 102 is for the rest
            if (generalForm == 2) {
                if (major == 0 && arg < 128) canonical = false;
                generalForm = 0;
            } else if (generalForm == 1) {
                generalForm = major == 4 ? 2 : 0;
                if (major == 4 && arg == -1) canonical = false; // the model writes [alternative, fields] definite
            }
            if (pendingBignum && major == 2) {
                bignum = true;
                if (!minimalBignum(b, pos + hl, arg)) canonical = false;
            }
            pendingTag = false;
            pendingBignum = false;
            pendingConstr = false;
            boolean inChunks = depth > 0 && (kind[depth] == BYTES || kind[depth] == TEXT);
            if (major == 0 || major == 1 || major == 7) {
                pos += hl;
            } else if (major == 2 || major == 3) {
                if (arg == -1) {
                    if (!plutusData || major == 3) canonical = false;
                    depth++;
                    if (depth >= kind.length) { int nc = kind.length * 2; kind = grow(kind, nc); remaining = grow(remaining, nc);
                        expectKey = grow(expectKey, nc); keyStart = grow(keyStart, nc); prevKeyStart = grow(prevKeyStart, nc);
                        prevKeyEnd = grow(prevKeyEnd, nc); chunkTotal = grow(chunkTotal, nc); lastChunkShort = grow(lastChunkShort, nc); }
                    kind[depth] = major == 2 ? BYTES : TEXT; remaining[depth] = -1; chunkTotal[depth] = 0; lastChunkShort[depth] = false;
                    maxDepth = Math.max(maxDepth, depth);
                    pos += hl;
                    continue;
                }
                pos += hl + (int) arg;
                if (inChunks) {
                    if (plutusData && kind[depth] == BYTES) {
                        if (lastChunkShort[depth] || arg > 64 || arg == 0) canonical = false;
                        if (arg < 64) lastChunkShort[depth] = true;
                    }
                    chunkTotal[depth] += arg;
                    continue; // chunks are not items of the parent
                }
                if (plutusData && major == 2 && arg > 64) canonical = false;
            } else { // array or map
                if (arg == -1 && (major == 5 || !plutusData)) canonical = false;
                if (arg == -1 && (b[pos + hl] & 0xff) == 0xff) canonical = false; // empty indefinite: encoders write 80
                pos += hl;
                if (arg != 0) {
                    depth++;
                    if (depth >= kind.length) { int nc = kind.length * 2; kind = grow(kind, nc); remaining = grow(remaining, nc);
                        expectKey = grow(expectKey, nc); keyStart = grow(keyStart, nc); prevKeyStart = grow(prevKeyStart, nc);
                        prevKeyEnd = grow(prevKeyEnd, nc); chunkTotal = grow(chunkTotal, nc); lastChunkShort = grow(lastChunkShort, nc); }
                    kind[depth] = major == 4 ? ARRAY : MAP;
                    long count = major == 5 ? arg * 2 : arg;
                    remaining[depth] = arg == -1 ? -1 : count;
                    expectKey[depth] = major == 5;
                    prevKeyStart[depth] = -1;
                    maxDepth = Math.max(maxDepth, depth);
                    continue;
                }
            }
            int[] r = complete(depth, pos, kind, remaining, expectKey, keyStart, prevKeyStart, prevKeyEnd, b);
            depth = r[0];
            if (r[1] == 0) canonical = false;
        }
        return new CborScan(canonical, maxDepth + 1, bignum);
    }

    // The payload of a bignum starting at off (after the byte string head): an integer outside -2^64..2^64-1 in minimal
    // bytes, so more than 8 bytes without a leading zero. A chunked payload is canonical only from 64-byte chunks (the
    // chunk rules check that), so its first data byte follows the head 58 40 of the first chunk.
    private static boolean minimalBignum(byte[] b, int off, long length) {
        if (length == -1)
            return !((b[off] & 0xff) == 0x58 && (b[off + 1] & 0xff) == 0x40 && b[off + 2] == 0);
        return length > 8 && b[off] != 0;
    }

    // An item ended at pos inside frame `depth`: account for it, closing definite frames that are full.
    // Returns {new depth, 1 if key order fine else 0}.
    private static int[] complete(int depth, int pos, int[] kind, long[] remaining, boolean[] expectKey, int[] keyStart,
                                  int[] prevKeyStart, int[] prevKeyEnd, byte[] b) {
        int ok = 1;
        while (depth > 0) {
            if (kind[depth] == MAP) {
                if (expectKey[depth]) {
                    int ks = keyStart[depth];
                    if (prevKeyStart[depth] >= 0 && compareKeys(b, prevKeyStart[depth], prevKeyEnd[depth], ks, pos) >= 0)
                        ok = 0;
                    prevKeyStart[depth] = ks;
                    prevKeyEnd[depth] = pos;
                }
                expectKey[depth] = !expectKey[depth];
            }
            if (remaining[depth] == -1)
                break;
            remaining[depth]--;
            if (remaining[depth] > 0)
                break;
            depth--; // the container is complete: account for it in its parent
        }
        return new int[]{depth, ok};
    }

    // Bytewise lexicographic order of the encoded keys (RFC 8949 4.2.1), the order CCL's canonical encoder writes.
    private static int compareKeys(byte[] b, int s1, int e1, int s2, int e2) {
        int l1 = e1 - s1;
        int l2 = e2 - s2;
        for (int i = 0; i < Math.min(l1, l2); i++) {
            int c = Integer.compare(b[s1 + i] & 0xff, b[s2 + i] & 0xff);
            if (c != 0) return c;
        }
        return Integer.compare(l1, l2);
    }

    private static long be(byte[] b, int off, int len) {
        long v = 0;
        for (int i = 0; i < len; i++) v = (v << 8) | (b[off + i] & 0xff);
        return v;
    }

    private static int[] grow(int[] a, int n) { return Arrays.copyOf(a, n); }
    private static long[] grow(long[] a, int n) { return Arrays.copyOf(a, n); }
    private static boolean[] grow(boolean[] a, int n) { return Arrays.copyOf(a, n); }
}
