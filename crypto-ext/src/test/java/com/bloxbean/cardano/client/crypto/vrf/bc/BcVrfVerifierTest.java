package com.bloxbean.cardano.client.crypto.vrf.bc;

import com.bloxbean.cardano.client.crypto.vrf.EcVrfVerifier;
import com.bloxbean.cardano.client.crypto.vrf.VrfException;
import com.bloxbean.cardano.client.crypto.vrf.VrfResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BcVrfVerifier using same IETF test vectors as EcVrfVerifierTest,
 * plus cross-validation against the i2p-based implementation.
 */
class BcVrfVerifierTest {

    private final BcVrfVerifier verifier = new BcVrfVerifier();

    // --- IETF Test Vector 1: empty alpha ---
    @Test
    void testVector1_emptyAlpha() {
        byte[] pk = hexToBytes("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] pi = hexToBytes(
                "b6b4699f87d56126c9117a7da55bd0085246f4c56dbc95d20172612e9d38e8d7"
                        + "ca65e573a126ed88d4e30a46f80a666854d675cf3ba81de0de043c3774f06156"
                        + "0f55edc256a787afe701677c0f602900");
        byte[] alpha = new byte[0];
        byte[] expectedBeta = hexToBytes(
                "5b49b554d05c0cd5a5325376b3387de59d924fd1e13ded44648ab33c21349a60"
                        + "3f25b84ec5ed887995b33da5e3bfcb87cd2f64521c4c62cf825cffabbe5d31cc");

        VrfResult result = verifier.verify(pk, pi, alpha);

        assertTrue(result.isValid(), "Test vector 1 should be valid");
        assertArrayEquals(expectedBeta, result.getOutput(), "Beta output should match");
    }

    // --- IETF Test Vector 2: alpha = 0x72 ---
    @Test
    void testVector2_singleByteAlpha() {
        byte[] pk = hexToBytes("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c");
        byte[] pi = hexToBytes(
                "ae5b66bdf04b4c010bfe32b2fc126ead2107b697634f6f7337b9bff8785ee111"
                        + "200095ece87dde4dbe87343f6df3b107d91798c8a7eb1245d3bb9c5aafb09335"
                        + "8c13e6ae1111a55717e895fd15f99f07");
        byte[] alpha = hexToBytes("72");
        byte[] expectedBeta = hexToBytes(
                "94f4487e1b2fec954309ef1289ecb2e15043a2461ecc7b2ae7d4470607ef82eb"
                        + "1cfa97d84991fe4a7bfdfd715606bc27e2967a6c557cfb5875879b671740b7d8");

        VrfResult result = verifier.verify(pk, pi, alpha);

        assertTrue(result.isValid(), "Test vector 2 should be valid");
        assertArrayEquals(expectedBeta, result.getOutput(), "Beta output should match");
    }

    // --- IETF Test Vector 3: alpha = 0xaf82 ---
    @Test
    void testVector3_twoByteAlpha() {
        byte[] pk = hexToBytes("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025");
        byte[] pi = hexToBytes(
                "dfa2cba34b611cc8c833a6ea83b8eb1bb5e2ef2dd1b0c481bc42ff36ae7847f6"
                        + "ab52b976cfd5def172fa412defde270c8b8bdfbaae1c7ece17d9833b1bcf3106"
                        + "4fff78ef493f820055b561ece45e1009");
        byte[] alpha = hexToBytes("af82");
        byte[] expectedBeta = hexToBytes(
                "2031837f582cd17a9af9e0c7ef5a6540e3453ed894b62c293686ca3c1e319dde"
                        + "9d0aa489a4b59a9594fc2328bc3deff3c8a0929a369a72b1180a596e016b5ded");

        VrfResult result = verifier.verify(pk, pi, alpha);

        assertTrue(result.isValid(), "Test vector 3 should be valid");
        assertArrayEquals(expectedBeta, result.getOutput(), "Beta output should match");
    }

    // --- Hash-to-curve intermediate value tests ---
    @Test
    void testVector1_hashToCurve() {
        byte[] pk = hexToBytes("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] expectedH = hexToBytes("1c5672d919cc0a800970cd7e05cb36ed27ed354c33519948e5a9eaf89aee12b7");

        Ed25519Point h = verifier.hashToCurveElligator2(pk, new byte[0]);
        assertNotNull(h, "H should not be null");
        assertArrayEquals(expectedH, h.encode(), "H should match intermediate value");
    }

    @Test
    void testVector2_hashToCurve() {
        byte[] pk = hexToBytes("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c");
        byte[] expectedH = hexToBytes("86725262c971bf064168bca2a87f593d425a49835bd52beb9f52ea59352d80fa");

        Ed25519Point h = verifier.hashToCurveElligator2(pk, hexToBytes("72"));
        assertNotNull(h);
        assertArrayEquals(expectedH, h.encode(), "H should match intermediate value");
    }

    @Test
    void testVector3_hashToCurve() {
        byte[] pk = hexToBytes("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025");
        byte[] expectedH = hexToBytes("9d8663faeb6ab14a239bfc652648b34f783c2e99f758c0e1b6f4f863f9419b56");

        Ed25519Point h = verifier.hashToCurveElligator2(pk, hexToBytes("af82"));
        assertNotNull(h);
        assertArrayEquals(expectedH, h.encode(), "H should match intermediate value");
    }

    // --- Negative tests ---
    @Test
    void testInvalidProof_bitFlip() {
        byte[] pk = hexToBytes("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] pi = hexToBytes(
                "b6b4699f87d56126c9117a7da55bd0085246f4c56dbc95d20172612e9d38e8d7"
                        + "ca65e573a126ed88d4e30a46f80a666854d675cf3ba81de0de043c3774f06156"
                        + "0f55edc256a787afe701677c0f602900");

        byte[] badPi = pi.clone();
        badPi[badPi.length - 1] ^= 0x01;

        VrfResult result = verifier.verify(pk, badPi, new byte[0]);
        assertFalse(result.isValid(), "Flipped proof bit should be invalid");
    }

    @Test
    void testInvalidProof_wrongAlpha() {
        byte[] pk = hexToBytes("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] pi = hexToBytes(
                "b6b4699f87d56126c9117a7da55bd0085246f4c56dbc95d20172612e9d38e8d7"
                        + "ca65e573a126ed88d4e30a46f80a666854d675cf3ba81de0de043c3774f06156"
                        + "0f55edc256a787afe701677c0f602900");

        VrfResult result = verifier.verify(pk, pi, new byte[]{0x01});
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

    // --- Cross-validation against i2p-based EcVrfVerifier ---
    @Test
    void crossValidate_allVectors_matchEcVrfVerifier() {
        EcVrfVerifier i2pVerifier = new EcVrfVerifier();
        BcVrfVerifier bcVerifier = new BcVrfVerifier();

        String[][] vectors = {
                {"d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
                        "b6b4699f87d56126c9117a7da55bd0085246f4c56dbc95d20172612e9d38e8d7"
                                + "ca65e573a126ed88d4e30a46f80a666854d675cf3ba81de0de043c3774f06156"
                                + "0f55edc256a787afe701677c0f602900",
                        ""},
                {"3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
                        "ae5b66bdf04b4c010bfe32b2fc126ead2107b697634f6f7337b9bff8785ee111"
                                + "200095ece87dde4dbe87343f6df3b107d91798c8a7eb1245d3bb9c5aafb09335"
                                + "8c13e6ae1111a55717e895fd15f99f07",
                        "72"},
                {"fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025",
                        "dfa2cba34b611cc8c833a6ea83b8eb1bb5e2ef2dd1b0c481bc42ff36ae7847f6"
                                + "ab52b976cfd5def172fa412defde270c8b8bdfbaae1c7ece17d9833b1bcf3106"
                                + "4fff78ef493f820055b561ece45e1009",
                        "af82"},
        };

        for (String[] vec : vectors) {
            byte[] pk = hexToBytes(vec[0]);
            byte[] pi = hexToBytes(vec[1]);
            byte[] alpha = vec[2].isEmpty() ? new byte[0] : hexToBytes(vec[2]);

            VrfResult i2pResult = i2pVerifier.verify(pk, pi, alpha);
            VrfResult bcResult = bcVerifier.verify(pk, pi, alpha);

            assertEquals(i2pResult.isValid(), bcResult.isValid(),
                    "Validity should match for alpha=" + vec[2]);
            assertArrayEquals(i2pResult.getOutput(), bcResult.getOutput(),
                    "Output should match for alpha=" + vec[2]);
        }
    }

    // --- Cross-validation with real Cardano mainnet block data ---
    @Test
    void crossValidate_mainnetBlock_matchesEcVrfVerifier() {
        // Block #10000000 data
        byte[] vrfVkey = hexToBytes(
                "5ebd45dfdf4ee76829d195ec24771904ee3947387f3e65005deb2b7ceab393c0");
        byte[] vrfProof = hexToBytes(
                "4dd5f5e34a33a4e162cd1957a95a471c8985710da24d1bfd0cbc95d909dbaa33"
                        + "b35680a86d4dc6f64348ef60db5bdd323809fccfd4d4f480cd4d5feae4d59f6b"
                        + "7d7ec2e9faa2bc8873d3963981075703");
        byte[] epochNonce = hexToBytes(
                "aa022d10f8a29863795ff14c4e82570d1db8906f1b3fd8a90fe69b699a4398d9");

        // Construct alpha using CardanoVrfInput (Praos)
        byte[] alpha = com.bloxbean.cardano.client.crypto.vrf.cardano.CardanoVrfInput
                .mkInputVrf(117736136L, epochNonce);

        EcVrfVerifier i2pVerifier = new EcVrfVerifier();
        BcVrfVerifier bcVerifier = new BcVrfVerifier();

        VrfResult i2pResult = i2pVerifier.verify(vrfVkey, vrfProof, alpha);
        VrfResult bcResult = bcVerifier.verify(vrfVkey, vrfProof, alpha);

        assertTrue(i2pResult.isValid(), "i2p result should be valid");
        assertTrue(bcResult.isValid(), "BC result should be valid");
        assertArrayEquals(i2pResult.getOutput(), bcResult.getOutput(),
                "Both implementations should produce identical output");
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
