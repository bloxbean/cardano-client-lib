package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.Ed25519Verifier;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import net.i2p.crypto.eddsa.math.Curve;
import net.i2p.crypto.eddsa.math.GroupElement;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Both {@link Ed25519Verifier}s against libsodium's verdicts, and against each other.
 * <p>
 * The verdicts in {@code /ed25519/*.txt} come from libsodium 1.0.20-stable, the {@code crypto_sign_open} that PyNaCl
 * 1.6.2 bundles; its verification path is the same as in input-output-hk/libsodium dbb48cce, the version
 * cardano-node links. {@code libsodium-corpus.txt} holds generated cases (valid, mutated, small-order, mixed-order
 * and non-canonical points, S out of range), {@code fixed-vectors.txt} RFC 8032 section 7.1 and Wycheproof
 * {@code ed25519_test.json}, {@code probes.txt} crafted cases and ed25519-speccheck.
 */
class Ed25519VerifierTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final BigInteger L = BigInteger.TWO.pow(252)
            .add(new BigInteger("27742317777372353535851937790883648493"));

    private record Case(String category, byte[] key, byte[] message, byte[] signature, String verdict) {
        @Override
        public String toString() {
            return category + " " + HEX.formatHex(key) + " " + HEX.formatHex(message) + " "
                    + HEX.formatHex(signature) + " " + verdict;
        }
    }

    private static Ed25519Verifier verifier(String name) {
        return name.equals("bc") ? new BouncyCastleEd25519Verifier() : new I2pEd25519Verifier();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bc", "i2p"})
    void hasLibsodiumsVerdicts(String name) throws IOException {
        Ed25519Verifier verifier = verifier(name);
        List<Case> cases = new ArrayList<>(read("libsodium-corpus.txt"));
        cases.addAll(read("fixed-vectors.txt"));
        cases.addAll(read("probes.txt"));
        assertThat(cases).hasSize(2368 + 215 + 17);
        List<String> wrong = new ArrayList<>();
        for (Case c : cases) {
            // E: a length libsodium's callers refuse; this library answers false there.
            boolean expected = c.verdict.equals("1");
            if (verifier.verify(c.signature, c.message, c.key) != expected) {
                wrong.add(c.toString());
            }
        }
        assertThat(wrong).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bc", "i2p"})
    void refusesMalformedInputWithFalse(String name) {
        Ed25519Verifier verifier = verifier(name);
        assertThat(verifier.verify(null, new byte[0], new byte[32])).isFalse();
        assertThat(verifier.verify(new byte[64], new byte[0], null)).isFalse();
        assertThat(verifier.verify(new byte[63], new byte[0], new byte[32])).isFalse();
        assertThat(verifier.verify(new byte[64], new byte[0], new byte[31])).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bc", "i2p"})
    void aNullMessageFailsOnlyOnceTheKeyDecodes(String name) {
        Ed25519Verifier verifier = verifier(name);
        byte[] signature = HEX.parseHex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
                + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");
        byte[] notAPoint = new byte[32];
        notAPoint[0] = 2;
        assertThat(verifier.verify(signature, null, notAPoint)).isFalse();
        byte[] key = HEX.parseHex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        assertThatThrownBy(() -> verifier.verify(signature, null, key)).isInstanceOf(CryptoException.class);
    }

    @Test
    void theConfigurationRefusesNoVerifier() {
        assertThatThrownBy(() -> CryptoConfiguration.INSTANCE.setEd25519Verifier(null))
                .isInstanceOf(NullPointerException.class);
        assertThat(CryptoConfiguration.INSTANCE.getEd25519Verifier()).isNotNull();
    }

    /** Seeded cases built here; the two verifiers must agree on each. */
    @Test
    void theTwoVerifiersAgree() throws NoSuchAlgorithmException {
        Ed25519Verifier bc = new BouncyCastleEd25519Verifier();
        Ed25519Verifier i2p = new I2pEd25519Verifier();
        List<String> wrong = new ArrayList<>();
        int accepted = 0;
        int acceptedMixed = 0;
        for (Case c : generate(new Random(745), 4000)) {
            boolean expected = i2p.verify(c.signature, c.message, c.key);
            if (bc.verify(c.signature, c.message, c.key) != expected) {
                wrong.add(c + " i2p=" + expected);
            }
            if (expected) {
                accepted++;
                if (c.category.startsWith("mixed")) {
                    acceptedMixed++;
                }
            }
        }
        assertThat(wrong).isEmpty();
        // Not a vacuous agreement: honest signatures and exact mixed-order ones are among the cases.
        assertThat(accepted).isGreaterThan(1000);
        assertThat(acceptedMixed).isGreaterThan(300);
    }

    @Test
    void theConfigurationSelectsTheVerifier() {
        CryptoConfiguration config = CryptoConfiguration.INSTANCE;
        Ed25519Verifier before = config.getEd25519Verifier();
        assertThat(before).isInstanceOf(BouncyCastleEd25519Verifier.class);
        List<Ed25519Verifier> used = new ArrayList<>();
        try {
            config.setEd25519Verifier((signature, message, publicKey) -> used.add(null));
            SigningProvider provider = new EdDSASigningProvider();
            assertThat(provider.verify(new byte[64], new byte[0], new byte[32])).isTrue();
            assertThat(used).hasSize(1);
        } finally {
            config.setEd25519Verifier(before);
        }
        SigningProvider fixed = new EdDSASigningProvider(new I2pEd25519Verifier());
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] key = HEX.parseHex("9518c18103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a8");
        byte[] signature = HEX.parseHex("f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794"
                + "831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07");
        assertThat(fixed.verify(signature, message, key)).isTrue();
    }

    private static List<Case> read(String name) throws IOException {
        List<Case> cases = new ArrayList<>();
        try (InputStream in = Ed25519VerifierTest.class.getResourceAsStream("/ed25519/" + name)) {
            String text = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            for (String line : text.split("\n")) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(" ", -1);
                cases.add(new Case(f[0], HEX.parseHex(f[1]), HEX.parseHex(f[2]), HEX.parseHex(f[3]), f[4]));
            }
        }
        return cases;
    }

    // Case generation. Keys are aB + T and nonces rB + T' for random a, r and torsion points T, T'; signatures are
    // S = r + k.a mod L. The i2p group arithmetic only builds the inputs.

    private static final EdDSAParameterSpec SPEC = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
    private static final Curve CURVE = SPEC.getCurve();

    private static List<Case> generate(Random random, int count) throws NoSuchAlgorithmException {
        GroupElement order8 = new GroupElement(CURVE,
                HEX.parseHex("26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc05"));
        List<GroupElement> torsion = new ArrayList<>();
        GroupElement t = CURVE.getZero(GroupElement.Representation.P3);
        for (int i = 0; i < 8; i++) {
            torsion.add(t);
            t = t.add(order8.toCached()).toP3();
        }
        List<byte[]> specialEncodings = specialEncodings(torsion);

        List<Case> cases = new ArrayList<>();
        while (cases.size() < count) {
            byte[] message = new byte[random.nextInt(4) == 0 ? 0 : random.nextInt(80)];
            random.nextBytes(message);
            BigInteger a = randomScalar(random);
            int keyTorsion = random.nextInt(4) == 0 ? 0 : random.nextInt(8);
            byte[] key = add(base(a), torsion.get(keyTorsion)).toByteArray();
            BigInteger r = randomScalar(random);
            int nonceTorsion = random.nextInt(8);
            String kind = "mixed";
            if (random.nextBoolean()) {
                // Pick r until R = SB - kA holds exactly: T' = -k.T, so that libsodium accepts.
                for (int tries = 0; tries < 64; tries++) {
                    byte[] nonce = add(base(r), torsion.get(nonceTorsion)).toByteArray();
                    int k8 = hash(nonce, key, message).mod(BigInteger.valueOf(8)).intValue();
                    if ((nonceTorsion + k8 * keyTorsion) % 8 == 0) {
                        kind = "mixedExact";
                        break;
                    }
                    r = randomScalar(random);
                    nonceTorsion = random.nextInt(8);
                }
            }
            byte[] nonce = add(base(r), torsion.get(nonceTorsion)).toByteArray();
            BigInteger k = hash(nonce, key, message);
            byte[] signature = concat(nonce, scalarBytes(r.add(k.multiply(a)).mod(L)));
            cases.add(new Case(kind, key, message, signature, "?"));

            // A variant of the same case.
            byte[] key2 = key.clone();
            byte[] signature2 = signature.clone();
            String variant;
            switch (random.nextInt(6)) {
                case 0 -> {
                    int bit = random.nextInt(256);
                    signature2[bit / 8] ^= (byte) (1 << (bit % 8));
                    variant = "flipR";
                }
                case 1 -> {
                    int bit = random.nextInt(256);
                    signature2[32 + bit / 8] ^= (byte) (1 << (bit % 8));
                    variant = "flipS";
                }
                case 2 -> {
                    int bit = random.nextInt(256);
                    key2[bit / 8] ^= (byte) (1 << (bit % 8));
                    variant = "flipA";
                }
                case 3 -> {
                    BigInteger s = new BigInteger(1, reverse(signature, 32, 64));
                    BigInteger big = s.add(L.multiply(BigInteger.valueOf(1 + random.nextInt(15))));
                    System.arraycopy(scalarBytes(big.min(BigInteger.TWO.pow(256).subtract(BigInteger.ONE))), 0,
                            signature2, 32, 32);
                    variant = "bigS";
                }
                case 4 -> {
                    byte[] special = specialEncodings.get(random.nextInt(specialEncodings.size()));
                    System.arraycopy(special, 0, signature2, 0, 32);
                    variant = "specialR";
                }
                default -> {
                    key2 = specialEncodings.get(random.nextInt(specialEncodings.size())).clone();
                    variant = "specialA";
                }
            }
            cases.add(new Case(variant, key2, message.clone(), signature2, "?"));
        }
        return cases;
    }

    /** Every torsion point and every {@code y >= p}, with both sign bits. */
    private static List<byte[]> specialEncodings(List<GroupElement> torsion) {
        List<byte[]> encodings = new ArrayList<>();
        for (GroupElement t : torsion) {
            byte[] e = t.toByteArray();
            encodings.add(e.clone());
            e[31] ^= (byte) 0x80;
            encodings.add(e);
        }
        BigInteger p = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));
        for (BigInteger y = p; y.bitLength() <= 255; y = y.add(BigInteger.ONE)) {
            byte[] e = scalarBytes(y);
            encodings.add(e.clone());
            e[31] ^= (byte) 0x80;
            encodings.add(e);
        }
        return encodings;
    }

    private static GroupElement base(BigInteger scalar) {
        return SPEC.getB().scalarMultiply(scalarBytes(scalar));
    }

    private static GroupElement add(GroupElement p, GroupElement q) {
        return p.add(q.toCached()).toP3();
    }

    private static BigInteger randomScalar(Random random) {
        BigInteger s;
        do {
            s = new BigInteger(253, random);
        } while (s.signum() == 0 || s.compareTo(L) >= 0);
        return s;
    }

    private static BigInteger hash(byte[] nonce, byte[] key, byte[] message) throws NoSuchAlgorithmException {
        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
        sha512.update(nonce);
        sha512.update(key);
        sha512.update(message);
        byte[] h = sha512.digest();
        return new BigInteger(1, reverse(h, 0, h.length)).mod(L);
    }

    private static byte[] scalarBytes(BigInteger value) {
        byte[] bigEndian = value.toByteArray();
        byte[] out = new byte[32];
        for (int i = 0; i < 32 && i < bigEndian.length; i++) {
            out[i] = bigEndian[bigEndian.length - 1 - i];
        }
        return out;
    }

    private static byte[] reverse(byte[] bytes, int from, int to) {
        byte[] out = new byte[to - from];
        for (int i = 0; i < out.length; i++) {
            out[i] = bytes[to - 1 - i];
        }
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
