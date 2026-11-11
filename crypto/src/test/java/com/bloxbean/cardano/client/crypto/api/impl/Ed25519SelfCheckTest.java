package com.bloxbean.cardano.client.crypto.api.impl;

import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The self-check that guards {@link Ed25519Equation} against a bcprov it does not agree with libsodium on. */
class Ed25519SelfCheckTest {

    @Test
    void passesOnTheBouncyCastleOnTheClasspath() {
        assertNull(Ed25519Equation.failures(Ed25519Equation.SELF_CHECK_VECTORS));
        assertNull(Ed25519Equation.selfCheck());
        assertNull(Ed25519Equation.selfCheck());
        Ed25519Equation.Vector rfc = Ed25519Equation.SELF_CHECK_VECTORS.get(0);
        HexFormat hex = HexFormat.of();
        assertTrue(new BouncyCastleEd25519Verifier().verify(hex.parseHex(rfc.signature()), new byte[0],
                hex.parseHex(rfc.publicKey())));
    }

    @Test
    void wouldCatchACofactoredEquation() {
        // BouncyCastle's own verify checks the equation times the cofactor; on these vectors it disagrees with
        // libsodium, so an equation that behaved like it would fail the self-check.
        HexFormat hex = HexFormat.of();
        int disagreements = 0;
        for (Ed25519Equation.Vector v : Ed25519Equation.SELF_CHECK_VECTORS) {
            byte[] message = hex.parseHex(v.message());
            boolean cofactored = Ed25519.verify(hex.parseHex(v.signature()), 0, hex.parseHex(v.publicKey()), 0,
                    message, 0, message.length);
            if (cofactored != v.libsodium()) {
                disagreements++;
            }
        }
        assertEquals(1, disagreements);
    }

    @Test
    void tripsWhenTheEquationDisagreesWithLibsodium() {
        List<Ed25519Equation.Vector> wrong = Ed25519Equation.SELF_CHECK_VECTORS.stream()
                .map(v -> new Ed25519Equation.Vector(v.name(), v.publicKey(), v.message(), v.signature(),
                        !v.libsodium()))
                .toList();
        String failure = Ed25519Equation.failures(wrong);
        assertNotNull(failure);
        assertTrue(failure.contains("RFC 8032 section 7.1 test 1: accepted, libsodium rejects"), failure);
        assertTrue(failure.contains("an R with a small-order part: rejected, libsodium accepts"), failure);
        assertTrue(failure.matches("(?s).*BouncyCastle bcprov \\d+\\.\\d+.*bcprov-jdk18on.*"), failure);

        LinkageError error = assertThrows(LinkageError.class, () -> Ed25519Equation.requirePassed(failure));
        assertEquals(failure, error.getMessage());
        assertDoesNotThrow(() -> Ed25519Equation.requirePassed(null));
    }

    @Test
    void namesTheBouncyCastleVersion() {
        assertTrue(Ed25519Equation.bouncyCastleVersion().matches("\\d+\\.\\d+(\\.\\d+)? \\(.*bcprov.*\\.jar\\)"),
                Ed25519Equation.bouncyCastleVersion());
    }
}
