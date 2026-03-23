package com.bloxbean.cardano.client.crypto.vrf;

import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EcVrfVerifier using IETF draft-irtf-cfrg-vrf-06 test vectors
 * from NCC Group's reference implementation.
 */
class EcVrfVerifierTest {

    private final EcVrfVerifier verifier = new EcVrfVerifier();

    // --- IETF Test Vector 1: empty alpha ---
    @Test
    void testVector1_emptyAlpha() {
        byte[] pk = decodeHexString("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] pi = decodeHexString(
                "b6b4699f87d56126c9117a7da55bd0085246f4c56dbc95d20172612e9d38e8d7"
                        + "ca65e573a126ed88d4e30a46f80a666854d675cf3ba81de0de043c3774f06156"
                        + "0f55edc256a787afe701677c0f602900");
        byte[] alpha = new byte[0]; // empty
        byte[] expectedBeta = decodeHexString(
                "5b49b554d05c0cd5a5325376b3387de59d924fd1e13ded44648ab33c21349a60"
                        + "3f25b84ec5ed887995b33da5e3bfcb87cd2f64521c4c62cf825cffabbe5d31cc");

        VrfResult result = verifier.verify(pk, pi, alpha);

        assertTrue(result.isValid(), "Test vector 1 should be valid");
        assertArrayEquals(expectedBeta, result.getOutput(), "Beta output should match");
    }

    // --- IETF Test Vector 2: alpha = 0x72 ---
    @Test
    void testVector2_singleByteAlpha() {
        byte[] pk = decodeHexString("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c");
        byte[] pi = decodeHexString(
                "ae5b66bdf04b4c010bfe32b2fc126ead2107b697634f6f7337b9bff8785ee111"
                        + "200095ece87dde4dbe87343f6df3b107d91798c8a7eb1245d3bb9c5aafb09335"
                        + "8c13e6ae1111a55717e895fd15f99f07");
        byte[] alpha = decodeHexString("72");
        byte[] expectedBeta = decodeHexString(
                "94f4487e1b2fec954309ef1289ecb2e15043a2461ecc7b2ae7d4470607ef82eb"
                        + "1cfa97d84991fe4a7bfdfd715606bc27e2967a6c557cfb5875879b671740b7d8");

        VrfResult result = verifier.verify(pk, pi, alpha);

        assertTrue(result.isValid(), "Test vector 2 should be valid");
        assertArrayEquals(expectedBeta, result.getOutput(), "Beta output should match");
    }

    // --- IETF Test Vector 3: alpha = 0xaf82 ---
    @Test
    void testVector3_twoByteAlpha() {
        byte[] pk = decodeHexString("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025");
        byte[] pi = decodeHexString(
                "dfa2cba34b611cc8c833a6ea83b8eb1bb5e2ef2dd1b0c481bc42ff36ae7847f6"
                        + "ab52b976cfd5def172fa412defde270c8b8bdfbaae1c7ece17d9833b1bcf3106"
                        + "4fff78ef493f820055b561ece45e1009");
        byte[] alpha = decodeHexString("af82");
        byte[] expectedBeta = decodeHexString(
                "2031837f582cd17a9af9e0c7ef5a6540e3453ed894b62c293686ca3c1e319dde"
                        + "9d0aa489a4b59a9594fc2328bc3deff3c8a0929a369a72b1180a596e016b5ded");

        VrfResult result = verifier.verify(pk, pi, alpha);

        assertTrue(result.isValid(), "Test vector 3 should be valid");
        assertArrayEquals(expectedBeta, result.getOutput(), "Beta output should match");
    }

    // --- Intermediate value tests (hash_to_curve output H) ---
    @Test
    void testVector1_hashToCurve() {
        byte[] pk = decodeHexString("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] expectedH = decodeHexString("1c5672d919cc0a800970cd7e05cb36ed27ed354c33519948e5a9eaf89aee12b7");

        var h = verifier.hashToCurveElligator2(pk, new byte[0]);
        assertNotNull(h, "H should not be null");
        assertArrayEquals(expectedH, h.toP3().toByteArray(), "H should match intermediate value");
    }

    @Test
    void testVector2_hashToCurve() {
        byte[] pk = decodeHexString("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c");
        byte[] expectedH = decodeHexString("86725262c971bf064168bca2a87f593d425a49835bd52beb9f52ea59352d80fa");

        var h = verifier.hashToCurveElligator2(pk, decodeHexString("72"));
        assertNotNull(h);
        assertArrayEquals(expectedH, h.toP3().toByteArray(), "H should match intermediate value");
    }

    @Test
    void testVector3_hashToCurve() {
        byte[] pk = decodeHexString("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025");
        byte[] expectedH = decodeHexString("9d8663faeb6ab14a239bfc652648b34f783c2e99f758c0e1b6f4f863f9419b56");

        var h = verifier.hashToCurveElligator2(pk, decodeHexString("af82"));
        assertNotNull(h);
        assertArrayEquals(expectedH, h.toP3().toByteArray(), "H should match intermediate value");
    }

    // --- Small-order public key rejection ---
    @Test
    void testSmallOrderPublicKey_rejected() {
        // Known small-order points on Ed25519 (order divides 8, so 8*P = identity):
        // 1) The identity point (0,1) encoded as 0100...00
        byte[] identityPk = new byte[32];
        identityPk[0] = 0x01;

        // 2) Known small-order point c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac03fa
        byte[] smallOrderPk = decodeHexString("c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac03fa");

        // Use a valid proof structure (content doesn't matter, rejection should happen before verification)
        byte[] dummyProof = new byte[80];
        byte[] alpha = new byte[0];

        // Identity point: 8*(0,1) = (0,1) = neutral → must be rejected
        VrfResult result1 = verifier.verify(identityPk, dummyProof, alpha);
        assertFalse(result1.isValid(), "Identity public key should be rejected as small-order");

        // Small-order point: 8*P = neutral → must be rejected
        VrfResult result2 = verifier.verify(smallOrderPk, dummyProof, alpha);
        assertFalse(result2.isValid(), "Small-order public key should be rejected");
    }

    // --- Negative tests ---
    @Test
    void testInvalidProof_bitFlip() {
        byte[] pk = decodeHexString("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] pi = decodeHexString(
                "b6b4699f87d56126c9117a7da55bd0085246f4c56dbc95d20172612e9d38e8d7"
                        + "ca65e573a126ed88d4e30a46f80a666854d675cf3ba81de0de043c3774f06156"
                        + "0f55edc256a787afe701677c0f602900");
        byte[] alpha = new byte[0];

        // Flip last bit of proof
        byte[] badPi = pi.clone();
        badPi[badPi.length - 1] ^= 0x01;

        VrfResult result = verifier.verify(pk, badPi, alpha);
        assertFalse(result.isValid(), "Flipped proof bit should be invalid");
    }

    @Test
    void testInvalidProof_wrongAlpha() {
        byte[] pk = decodeHexString("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] pi = decodeHexString(
                "b6b4699f87d56126c9117a7da55bd0085246f4c56dbc95d20172612e9d38e8d7"
                        + "ca65e573a126ed88d4e30a46f80a666854d675cf3ba81de0de043c3774f06156"
                        + "0f55edc256a787afe701677c0f602900");

        VrfResult result = verifier.verify(pk, pi, new byte[]{0x01}); // wrong alpha
        assertFalse(result.isValid(), "Wrong alpha should be invalid");
    }

    @Test
    void testInvalidProofSize() {
        byte[] pk = new byte[32];
        assertThrows(VrfException.class, () -> verifier.verify(pk, new byte[50], new byte[0]));
    }

    @Test
    void testInvalidPublicKeySize() {
        assertThrows(VrfException.class, () -> verifier.verify(new byte[16], new byte[80], new byte[0]));
    }

}
