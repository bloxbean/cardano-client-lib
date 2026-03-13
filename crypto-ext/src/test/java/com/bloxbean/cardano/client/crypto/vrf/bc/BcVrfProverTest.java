package com.bloxbean.cardano.client.crypto.vrf.bc;

import com.bloxbean.cardano.client.crypto.vrf.EcVrfVerifier;
import com.bloxbean.cardano.client.crypto.vrf.VrfResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BcVrfProver using IETF test vectors from draft-irtf-cfrg-vrf-06.
 * <p>
 * Test vectors contain the full 64-byte secret key (32-byte seed + 32-byte public key).
 * We prove, then verify the proof with both BcVrfVerifier and EcVrfVerifier.
 */
class BcVrfProverTest {

    private final BcVrfProver prover = new BcVrfProver();
    private final BcVrfVerifier bcVerifier = new BcVrfVerifier();
    private final EcVrfVerifier ecVerifier = new EcVrfVerifier();

    // --- IETF Test Vector 1: empty alpha ---
    // SK seed = 9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60
    // PK = d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a
    @Test
    void testVector1_proveAndVerify() {
        byte[] sk = hexToBytes(
                "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
                        + "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] pk = hexToBytes("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] alpha = new byte[0];
        byte[] expectedProof = hexToBytes(
                "b6b4699f87d56126c9117a7da55bd0085246f4c56dbc95d20172612e9d38e8d7"
                        + "ca65e573a126ed88d4e30a46f80a666854d675cf3ba81de0de043c3774f06156"
                        + "0f55edc256a787afe701677c0f602900");
        byte[] expectedBeta = hexToBytes(
                "5b49b554d05c0cd5a5325376b3387de59d924fd1e13ded44648ab33c21349a60"
                        + "3f25b84ec5ed887995b33da5e3bfcb87cd2f64521c4c62cf825cffabbe5d31cc");

        byte[] proof = prover.prove(sk, alpha);

        assertArrayEquals(expectedProof, proof, "Proof should match IETF test vector 1");

        // Verify with both verifiers
        VrfResult bcResult = bcVerifier.verify(pk, proof, alpha);
        assertTrue(bcResult.isValid(), "Proof should verify with BcVrfVerifier");
        assertArrayEquals(expectedBeta, bcResult.getOutput(), "VRF output should match");

        VrfResult ecResult = ecVerifier.verify(pk, proof, alpha);
        assertTrue(ecResult.isValid(), "Proof should verify with EcVrfVerifier");
        assertArrayEquals(expectedBeta, ecResult.getOutput(), "VRF output should match");
    }

    // --- IETF Test Vector 2: alpha = 0x72 ---
    // SK seed = 4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb
    // PK = 3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c
    @Test
    void testVector2_proveAndVerify() {
        byte[] sk = hexToBytes(
                "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb"
                        + "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c");
        byte[] pk = hexToBytes("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c");
        byte[] alpha = hexToBytes("72");
        byte[] expectedProof = hexToBytes(
                "ae5b66bdf04b4c010bfe32b2fc126ead2107b697634f6f7337b9bff8785ee111"
                        + "200095ece87dde4dbe87343f6df3b107d91798c8a7eb1245d3bb9c5aafb09335"
                        + "8c13e6ae1111a55717e895fd15f99f07");
        byte[] expectedBeta = hexToBytes(
                "94f4487e1b2fec954309ef1289ecb2e15043a2461ecc7b2ae7d4470607ef82eb"
                        + "1cfa97d84991fe4a7bfdfd715606bc27e2967a6c557cfb5875879b671740b7d8");

        byte[] proof = prover.prove(sk, alpha);

        assertArrayEquals(expectedProof, proof, "Proof should match IETF test vector 2");

        VrfResult bcResult = bcVerifier.verify(pk, proof, alpha);
        assertTrue(bcResult.isValid(), "Proof should verify with BcVrfVerifier");
        assertArrayEquals(expectedBeta, bcResult.getOutput(), "VRF output should match");

        VrfResult ecResult = ecVerifier.verify(pk, proof, alpha);
        assertTrue(ecResult.isValid(), "Proof should verify with EcVrfVerifier");
        assertArrayEquals(expectedBeta, ecResult.getOutput(), "VRF output should match");
    }

    // --- IETF Test Vector 3: alpha = 0xaf82 ---
    // SK seed = c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7
    // PK = fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025
    @Test
    void testVector3_proveAndVerify() {
        byte[] sk = hexToBytes(
                "c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7"
                        + "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025");
        byte[] pk = hexToBytes("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025");
        byte[] alpha = hexToBytes("af82");
        byte[] expectedProof = hexToBytes(
                "dfa2cba34b611cc8c833a6ea83b8eb1bb5e2ef2dd1b0c481bc42ff36ae7847f6"
                        + "ab52b976cfd5def172fa412defde270c8b8bdfbaae1c7ece17d9833b1bcf3106"
                        + "4fff78ef493f820055b561ece45e1009");
        byte[] expectedBeta = hexToBytes(
                "2031837f582cd17a9af9e0c7ef5a6540e3453ed894b62c293686ca3c1e319dde"
                        + "9d0aa489a4b59a9594fc2328bc3deff3c8a0929a369a72b1180a596e016b5ded");

        byte[] proof = prover.prove(sk, alpha);

        assertArrayEquals(expectedProof, proof, "Proof should match IETF test vector 3");

        VrfResult bcResult = bcVerifier.verify(pk, proof, alpha);
        assertTrue(bcResult.isValid(), "Proof should verify with BcVrfVerifier");
        assertArrayEquals(expectedBeta, bcResult.getOutput(), "VRF output should match");

        VrfResult ecResult = ecVerifier.verify(pk, proof, alpha);
        assertTrue(ecResult.isValid(), "Proof should verify with EcVrfVerifier");
        assertArrayEquals(expectedBeta, ecResult.getOutput(), "VRF output should match");
    }

    @Test
    void invalidSecretKeySize() {
        assertThrows(com.bloxbean.cardano.client.crypto.vrf.VrfException.class,
                () -> prover.prove(new byte[32], new byte[0]));
    }

    @Test
    void nullAlpha() {
        assertThrows(com.bloxbean.cardano.client.crypto.vrf.VrfException.class,
                () -> prover.prove(new byte[64], null));
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
