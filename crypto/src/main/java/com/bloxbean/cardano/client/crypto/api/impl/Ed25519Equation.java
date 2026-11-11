package com.bloxbean.cardano.client.crypto.api.impl;

import org.bouncycastle.math.ec.rfc7748.X25519Field;
import org.bouncycastle.util.Arrays;

import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.Manifest;

/**
 * The verification equation of libsodium's {@code crypto_sign_ed25519_verify_detached}, after its input checks.
 * <p>
 * libsodium decodes {@code -A} ({@code ge25519_frombytes_negate_vartime}), computes {@code k = SHA-512(R || A || M)
 * mod L}, then {@code R' = k(-A) + SB} ({@code ge25519_double_scalarmult_vartime}), and accepts when the encoding of
 * {@code R'} equals the 32 bytes of {@code R}. This is that algorithm step for step: the same decoding, the same signed
 * sliding windows ({@code slide_vartime}), the same tables of odd multiples, the same byte comparison. The check is
 * cofactorless: a key or {@code R} with a small-order part is accepted only when the equation holds exactly, as in
 * libsodium. BouncyCastle's {@code Ed25519.verify} cannot stand in for it, because it checks the equation times
 * the cofactor ({@code scalarMultStraus128Var} ends in doublings that clear it).
 * <p>
 * The libsodium sources are {@code src/libsodium/crypto_sign/ed25519/ref10/open.c} and
 * {@code src/libsodium/crypto_core/ed25519/ref10/ed25519_ref10.c}; the functions named below are the same in
 * libsodium 1.0.18, 1.0.20 and input-output-hk/libsodium dbb48cce5429cb6585c9034f002568964f1ce567, the version
 * cardano-node links.
 * <p>
 * The field arithmetic is BouncyCastle's {@link X25519Field}, which works in place on {@code int[]}. The point
 * formulas are BouncyCastle's, from {@code org.bouncycastle.math.ec.rfc8032.Ed25519} in bcprov-jdk18on 1.78.1 (the
 * same in 1.83 and 1.86), which keeps their operands inside {@link X25519Field}'s bounds; each says which method it
 * is. That code is
 * <i>Copyright (c) 2000-2024 The Legion of the Bouncy Castle Inc. (https://www.bouncycastle.org)</i>, used under its
 * MIT-style licence: "Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions: The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."
 */
final class Ed25519Equation {

    private static final class F extends X25519Field {
    }

    private static final String SELF_CHECK_ADVICE = "Ed25519 verification uses BouncyCastle's internal field "
            + "arithmetic (org.bouncycastle.math.ec.rfc7748.X25519Field), so it runs only on a bcprov version it "
            + "agrees with libsodium on. It needs bcprov 1.67 or newer; use bcprov-jdk18on 1.86 (also tested with "
            + "1.78.1 and 1.83), report the version found, or select I2pEd25519Verifier with "
            + "CryptoConfiguration.INSTANCE.setEd25519Verifier.";

    /** The group order {@code L}. */
    private static final BigInteger ORDER = BigInteger.TWO.pow(252)
            .add(new BigInteger("27742317777372353535851937790883648493"));

    /** {@code d = -121665/121666}, {@code 2d} and {@code 4d}. */
    private static final int[] D = new int[F.SIZE];
    private static final int[] D2 = new int[F.SIZE];
    private static final int[] D4 = new int[F.SIZE];

    /**
     * {@code B, 3B, ..., 15B}: libsodium's {@code Bi} table in {@code ge25519_double_scalarmult_vartime}, made the way
     * BouncyCastle's {@code Ed25519.precompute} makes its {@code PRECOMP_BASE_WNAF}.
     */
    private static final Precomp[] BASE_ODD_MULTIPLES = new Precomp[8];

    static {
        try {
            buildTables();
        } catch (LinkageError e) {
            // A bcprov without the X25519Field this class was written against (before 1.67).
            throw new LinkageError("Ed25519 verification cannot run on BouncyCastle bcprov " + bouncyCastleVersion()
                    + ": " + e + ". " + SELF_CHECK_ADVICE, e);
        }
    }

    private static void buildTables() {
        F.decode(HexFormat.of().parseHex("a3785913ca4deb75abd841414d0a700098e879777940c78c73fe6f2bee6c0352"), 0, D);
        F.add(D, D, D2);
        F.normalize(D2);
        F.add(D2, D2, D4);
        F.normalize(D4);

        Affine base = new Affine();
        if (!decodeNegated(HexFormat.of().parseHex(
                "5866666666666666666666666666666666666666666666666666666666666666"), base)) {
            throw new LinkageError("Ed25519: the base point does not decode with BouncyCastle bcprov "
                    + bouncyCastleVersion() + "; " + SELF_CHECK_ADVICE);
        }
        F.negate(base.x, base.x);
        F.normalize(base.x);

        Extended q = new Extended();
        copy(base, q);
        Extended twice = new Extended();
        Temp t = new Temp();
        add(q, q, twice, t);
        int[] inverse = F.create();
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                add(q, twice, q, t);
            }
            // z := 1/(2z), so that x.z and y.z are x/2 and y/2.
            F.add(q.z, q.z, inverse);
            F.inv(inverse, inverse);
            int[] xh = F.create();
            int[] yh = F.create();
            F.mul(q.x, inverse, xh);
            F.mul(q.y, inverse, yh);
            Precomp r = BASE_ODD_MULTIPLES[i] = new Precomp();
            F.apm(yh, xh, r.ypxH, r.ymxH);
            F.mul(xh, yh, r.xyd);
            F.mul(r.xyd, D4, r.xyd);
            F.normalize(r.ymxH);
            F.normalize(r.ypxH);
            F.normalize(r.xyd);
        }
    }

    private Ed25519Equation() {
    }

    /**
     * Whether {@code R} (the first 32 bytes of {@code signature}) is the encoding of {@code SB - kA}.
     * The caller has checked the lengths and libsodium's input checks; {@code S} is below {@code L}.
     */
    static boolean holds(byte[] publicKey, byte[] message, byte[] signature) {
        requirePassed(selfCheck());
        return equationHolds(publicKey, message, signature);
    }

    // The self-check. X25519Field is BouncyCastle's internal arithmetic, not its public API, and a consumer's build may
    // resolve another bcprov than the one this module is tested with. Before the first verification, the equation
    // must give libsodium's verdicts on these vectors, or every verification fails with a LinkageError (an Error,
    // so that no catch of Exception turns it into an ordinary false or a failed script).

    /** A case with libsodium's verdict (PyNaCl 1.6.2, libsodium 1.0.20-stable, crypto_sign_open). */
    record Vector(String name, String publicKey, String message, String signature, boolean libsodium) {
    }

    static final List<Vector> SELF_CHECK_VECTORS = List.of(
            new Vector("RFC 8032 section 7.1 test 1",
                    "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", "",
                    "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
                            + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b", true),
            // R = rB + T (T of order 8): SB - kA = rB != R. libsodium rejects it, a cofactored check accepts it.
            new Vector("an R with a small-order part",
                    "91c82cf052f0ca436aa42042597b353ddeacb39c4c8d2f11f24c86a28857817d", "2891c7db6b177b",
                    "f22a89ca724581d70f4f61af196b9c7f01cfef7119337540d360ea9cfcc3c315"
                            + "df2d5ee84950485798af86b4ee44377e0ff58a6c647d4e2d30ac12abaa4d7a03", false),
            // A and R with small-order parts that cancel: SB - kA = R exactly, so libsodium accepts it.
            new Vector("a key and R with small-order parts that cancel",
                    "64fbc9dd8d882582ac4485342d249a7d42a8d9fd07cfcea33aaf8963a72e9be5", "bf7d",
                    "f2ebf3a65afb6702a617fe6dd850790b543bf1e00bf7db36e3f60d1850622253"
                            + "a7d5eaae539a7fc14d0cec6650810690255d2c5b8c2c46f8afbf9e77904b130c", true));

    /** The self-check's outcome once known: {@link #PASSED} or what went wrong. */
    private static volatile String selfCheckOutcome;

    private static final String PASSED = "passed";

    /**
     * Runs the self-check on first use and keeps a definite outcome: a pass, wrong verdicts, or a
     * {@link LinkageError} from the bcprov on the classpath. Any other error, such as an {@link OutOfMemoryError} or a
     * {@link StackOverflowError}, propagates without an outcome, so the next verification runs the check again.
     *
     * @return {@code null} when it passed, otherwise what went wrong
     */
    static String selfCheck() {
        String outcome = selfCheckOutcome;
        if (outcome == null) {
            String failure;
            try {
                failure = failures(SELF_CHECK_VECTORS);
            } catch (LinkageError e) {
                failure = "Ed25519 verification cannot run on BouncyCastle bcprov " + bouncyCastleVersion() + ": " + e
                        + ". " + SELF_CHECK_ADVICE;
            }
            outcome = failure == null ? PASSED : failure;
            selfCheckOutcome = outcome;
        }
        return PASSED.equals(outcome) ? null : outcome;
    }

    /** {@code null} when the equation gives each vector libsodium's verdict, otherwise what went wrong. */
    static String failures(List<Vector> vectors) {
        HexFormat hex = HexFormat.of();
        StringBuilder wrong = new StringBuilder();
        for (Vector v : vectors) {
            boolean actual = equationHolds(hex.parseHex(v.publicKey()), hex.parseHex(v.message()),
                    hex.parseHex(v.signature()));
            if (actual != v.libsodium()) {
                wrong.append(wrong.isEmpty() ? "" : "; ").append(v.name()).append(": ")
                        .append(actual ? "accepted" : "rejected").append(", libsodium ")
                        .append(v.libsodium() ? "accepts" : "rejects");
            }
        }
        return wrong.isEmpty() ? null : "Ed25519 verification failed its self-check with BouncyCastle bcprov "
                + bouncyCastleVersion() + " (" + wrong + "). " + SELF_CHECK_ADVICE;
    }

    /** Throws the self-check's failure, if there is one. */
    static void requirePassed(String failure) {
        if (failure != null) {
            throw new LinkageError(failure);
        }
    }

    /** The {@code Bundle-Version} of the jar {@link X25519Field} came from, for the self-check's message. */
    static String bouncyCastleVersion() {
        URL classFile;
        try {
            classFile = X25519Field.class.getResource("X25519Field.class");
        } catch (LinkageError e) {
            // No X25519Field at all: name the jar of a class every bcprov has.
            classFile = Arrays.class.getResource("Arrays.class");
        }
        if (classFile == null || !"jar".equals(classFile.getProtocol())) {
            return "of unknown version";
        }
        String url = classFile.toString();
        String jar = url.substring(0, url.indexOf("!/") + 2);
        try (InputStream in = URI.create(jar + "META-INF/MANIFEST.MF").toURL().openStream()) {
            String version = new Manifest(in).getMainAttributes().getValue("Bundle-Version");
            return (version == null ? "of unknown version" : version) + " (" + jar.substring(4, jar.length() - 2) + ")";
        } catch (Exception e) {
            // Only the message of an error already being reported depends on this.
            return "of unknown version (" + e + ")";
        }
    }

    /** The equation itself. */
    private static boolean equationHolds(byte[] publicKey, byte[] message, byte[] signature) {
        Affine negatedKey = new Affine();
        if (!decodeNegated(publicKey, negatedKey)) {
            return false;
        }

        MessageDigest sha512;
        try {
            sha512 = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 is not available", e);
        }
        sha512.update(signature, 0, 32);
        sha512.update(publicKey);
        sha512.update(message);
        byte[] k = reduce(sha512.digest());

        byte[] s = new byte[32];
        System.arraycopy(signature, 32, s, 0, 32);

        Accum r = new Accum();
        doubleScalarMult(k, negatedKey, s, r);

        byte[] encoded = new byte[32];
        encode(r, encoded);
        for (int i = 0; i < 32; i++) {
            if (encoded[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * libsodium {@code ge25519_frombytes_negate_vartime} ({@code ed25519_ref10.c}), with BouncyCastle's
     * {@code decodePointVar} steps for the square root: the point {@code -P} for the encoding of {@code P}, or
     * false when {@code y} has no {@code x} on the curve. The sign bit of an {@code x} of zero is not checked.
     */
    private static boolean decodeNegated(byte[] encoded, Affine r) {
        F.decode(encoded, 0, r.y);
        int[] u = F.create();
        int[] v = F.create();
        F.sqr(r.y, u);
        F.mul(D, u, v);
        F.subOne(u);
        F.addOne(v);
        if (!F.sqrtRatioVar(u, v, r.x)) {
            return false;
        }
        F.normalize(r.x);
        F.normalize(r.y);
        int sign = (encoded[31] & 0x80) >>> 7;
        if ((r.x[0] & 1) == sign) {
            F.negate(r.x, r.x);
            F.normalize(r.x);
        }
        return true;
    }

    /**
     * libsodium {@code sc25519_reduce} ({@code ed25519_ref10.c}): a 64-byte little-endian number modulo {@code L}, as
     * 32 bytes.
     */
    private static byte[] reduce(byte[] wide) {
        byte[] bigEndian = new byte[wide.length];
        for (int i = 0; i < wide.length; i++) {
            bigEndian[i] = wide[wide.length - 1 - i];
        }
        byte[] reduced = new BigInteger(1, bigEndian).mod(ORDER).toByteArray();
        byte[] littleEndian = new byte[32];
        for (int i = 0; i < reduced.length && i < 32; i++) {
            littleEndian[i] = reduced[reduced.length - 1 - i];
        }
        return littleEndian;
    }

    /**
     * libsodium {@code slide_vartime} ({@code ed25519_ref10.c}), line for line: signed odd digits of at most 15 in
     * absolute value.
     */
    private static byte[] slide(byte[] a) {
        byte[] r = new byte[256];
        for (int i = 0; i < 256; i++) {
            r[i] = (byte) (1 & (a[i >> 3] >> (i & 7)));
        }
        for (int i = 0; i < 256; i++) {
            if (r[i] == 0) {
                continue;
            }
            for (int b = 1; b <= 6 && i + b < 256; b++) {
                if (r[i + b] == 0) {
                    continue;
                }
                int ribs = r[i + b] << b;
                int cmp = r[i] + ribs;
                if (cmp <= 15) {
                    r[i] = (byte) cmp;
                    r[i + b] = 0;
                } else {
                    cmp = r[i] - ribs;
                    if (cmp < -15) {
                        break;
                    }
                    r[i] = (byte) cmp;
                    for (int k = i + b; k < 256; k++) {
                        if (r[k] == 0) {
                            r[k] = 1;
                            break;
                        }
                        r[k] = 0;
                    }
                }
            }
        }
        return r;
    }

    /**
     * libsodium {@code ge25519_double_scalarmult_vartime} ({@code ed25519_ref10.c}): {@code r = aA + bB}, with the
     * table {@code A, 3A, ..., 15A} and, from the top nonzero digit down, a doubling then the two additions.
     */
    private static void doubleScalarMult(byte[] a, Affine point, byte[] b, Accum r) {
        byte[] aSlide = slide(a);
        byte[] bSlide = slide(b);

        Temp t = new Temp();
        PrecompZ[] oddMultiples = new PrecompZ[8];
        Extended q = new Extended();
        copy(point, q);
        Extended twice = new Extended();
        add(q, q, twice, t);
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                add(q, twice, q, t);
            }
            copy(q, oddMultiples[i] = new PrecompZ());
        }

        setNeutral(r);
        int i = 255;
        while (i >= 0 && aSlide[i] == 0 && bSlide[i] == 0) {
            i--;
        }
        for (; i >= 0; i--) {
            doublePoint(r);
            int da = aSlide[i];
            if (da != 0) {
                addVar(da < 0, oddMultiples[Math.abs(da) >> 1], r, t);
            }
            int db = bSlide[i];
            if (db != 0) {
                addVar(db < 0, BASE_ODD_MULTIPLES[Math.abs(db) >> 1], r, t);
            }
        }
    }

    /**
     * libsodium {@code ge25519_tobytes} ({@code ed25519_ref10.c}), as BouncyCastle's {@code normalizeToAffine} and
     * {@code encodePoint}: {@code y} with the sign of {@code x} in the top bit.
     */
    private static void encode(Accum p, byte[] out) {
        int[] zInverse = F.create();
        int[] x = F.create();
        int[] y = F.create();
        F.invVar(p.z, zInverse);
        F.mul(p.x, zInverse, x);
        F.mul(p.y, zInverse, y);
        F.normalize(x);
        F.normalize(y);
        F.encode(y, out, 0);
        out[31] |= (byte) ((x[0] & 1) << 7);
    }

    // Point representations and formulas after BouncyCastle's Ed25519.

    /** Affine {@code (x, y)}. */
    private static final class Affine {
        final int[] x = F.create();
        final int[] y = F.create();
    }

    /** Extended {@code (X : Y : Z : T)}. */
    private static final class Extended {
        final int[] x = F.create();
        final int[] y = F.create();
        final int[] z = F.create();
        final int[] t = F.create();
    }

    /** {@code (X : Y : Z)} with {@code T = u.v} kept as two factors. */
    private static final class Accum {
        final int[] x = F.create();
        final int[] y = F.create();
        final int[] z = F.create();
        final int[] u = F.create();
        final int[] v = F.create();
    }

    /** An affine point as {@code (y - x)/2, (y + x)/2, x.y.d}. */
    private static final class Precomp {
        final int[] ymxH = F.create();
        final int[] ypxH = F.create();
        final int[] xyd = F.create();
    }

    /** A projective point as {@code Y - X, Y + X, 2d.T, 2Z}: the halving moved onto {@code Z}. */
    private static final class PrecompZ {
        final int[] ymxH = F.create();
        final int[] ypxH = F.create();
        final int[] xyd = F.create();
        final int[] z = F.create();
    }

    private static final class Temp {
        final int[] r0 = F.create();
        final int[] r1 = F.create();
    }

    /** BouncyCastle {@code pointSetNeutral(PointAccum)}. */
    private static void setNeutral(Accum p) {
        F.zero(p.x);
        F.one(p.y);
        F.one(p.z);
        F.zero(p.u);
        F.one(p.v);
    }

    /** BouncyCastle {@code pointCopy(PointAffine, PointExtended)}. */
    private static void copy(Affine p, Extended r) {
        F.copy(p.x, 0, r.x, 0);
        F.copy(p.y, 0, r.y, 0);
        F.one(r.z);
        F.mul(p.x, p.y, r.t);
    }

    /** BouncyCastle {@code pointCopy(PointExtended, PointPrecompZ)}. */
    private static void copy(Extended p, PrecompZ r) {
        F.apm(p.y, p.x, r.ypxH, r.ymxH);
        F.mul(p.t, D2, r.xyd);
        F.add(p.z, p.z, r.z);
    }

    /**
     * BouncyCastle {@code pointAdd(PointExtended, PointExtended, PointExtended, PointTemp)}: {@code r = p + q};
     * {@code p} may be {@code r}, {@code q} may not.
     */
    private static void add(Extended p, Extended q, Extended r, Temp t) {
        int[] a = r.x;
        int[] b = r.y;
        int[] c = t.r0;
        int[] d = t.r1;
        int[] e = a;
        int[] f = c;
        int[] g = d;
        int[] h = b;

        F.apm(p.y, p.x, b, a);
        F.apm(q.y, q.x, d, c);
        F.mul(a, c, a);
        F.mul(b, d, b);
        F.mul(p.t, q.t, c);
        F.mul(c, D2, c);
        F.add(p.z, p.z, d);
        F.mul(d, q.z, d);
        F.apm(b, a, h, e);
        F.apm(d, c, g, f);
        F.mul(e, h, r.t);
        F.mul(f, g, r.z);
        F.mul(e, f, r.x);
        F.mul(h, g, r.y);
    }

    /**
     * BouncyCastle {@code pointAddVar(boolean, PointPrecomp, PointAccum, PointTemp)}: {@code r + p}, or {@code r - p}
     * when {@code negate}.
     */
    private static void addVar(boolean negate, Precomp p, Accum r, Temp t) {
        int[] a = r.x;
        int[] b = r.y;
        int[] c = t.r0;
        int[] e = r.u;
        int[] f = a;
        int[] g = b;
        int[] h = r.v;

        int[] na = negate ? b : a;
        int[] nb = negate ? a : b;
        int[] nf = na;
        int[] ng = nb;

        F.apm(r.y, r.x, b, a);
        F.mul(na, p.ymxH, na);
        F.mul(nb, p.ypxH, nb);
        F.mul(r.u, r.v, c);
        F.mul(c, p.xyd, c);
        F.apm(b, a, h, e);
        F.apm(r.z, c, ng, nf);
        F.mul(f, g, r.z);
        F.mul(f, e, r.x);
        F.mul(g, h, r.y);
    }

    /**
     * BouncyCastle {@code pointAddVar(boolean, PointPrecompZ, PointAccum, PointTemp)}: {@code r + p}, or
     * {@code r - p} when {@code negate}.
     */
    private static void addVar(boolean negate, PrecompZ p, Accum r, Temp t) {
        int[] a = r.x;
        int[] b = r.y;
        int[] c = t.r0;
        int[] d = r.z;
        int[] e = r.u;
        int[] f = a;
        int[] g = b;
        int[] h = r.v;

        int[] na = negate ? b : a;
        int[] nb = negate ? a : b;
        int[] nf = na;
        int[] ng = nb;

        F.apm(r.y, r.x, b, a);
        F.mul(na, p.ymxH, na);
        F.mul(nb, p.ypxH, nb);
        F.mul(r.u, r.v, c);
        F.mul(c, p.xyd, c);
        F.mul(r.z, p.z, d);
        F.apm(b, a, h, e);
        F.apm(d, c, ng, nf);
        F.mul(f, g, r.z);
        F.mul(f, e, r.x);
        F.mul(g, h, r.y);
    }

    /** BouncyCastle {@code pointDouble(PointAccum)}. */
    private static void doublePoint(Accum r) {
        int[] a = r.x;
        int[] b = r.y;
        int[] c = r.z;
        int[] e = r.u;
        int[] f = a;
        int[] g = b;
        int[] h = r.v;

        F.add(r.x, r.y, e);
        F.sqr(r.x, a);
        F.sqr(r.y, b);
        F.sqr(r.z, c);
        F.add(c, c, c);
        F.apm(a, b, h, g);
        F.sqr(e, e);
        F.sub(h, e, e);
        F.add(c, g, f);
        F.carry(f);
        F.mul(f, g, r.z);
        F.mul(f, e, r.x);
        F.mul(g, h, r.y);
    }
}
