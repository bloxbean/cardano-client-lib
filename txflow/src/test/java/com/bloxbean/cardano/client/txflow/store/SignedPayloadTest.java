package com.bloxbean.cardano.client.txflow.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SignedPayloadTest {

    @Test
    void stringDigestUsesStableUtf8Encoding() {
        assertEquals(
                "51d0694d840e0b86577895f93182c682a8af88c18998afd6256e03e7254d6ef9",
                SignedPayloadVerifier.sha256("付款-🚀"));
    }

    @Test
    void inlinePayloadUsesContentValueSemantics() {
        SignedPayload.InlineCbor first = new SignedPayload.InlineCbor(
                new byte[]{1, 2, 3}, "digest", "hash");
        SignedPayload.InlineCbor same = new SignedPayload.InlineCbor(
                new byte[]{1, 2, 3}, "digest", "hash");
        SignedPayload.InlineCbor different = new SignedPayload.InlineCbor(
                new byte[]{1, 2, 4}, "digest", "hash");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
    }

    @Test
    void inlinePayloadDefensivelyCopiesConstructorAndAccessorArrays() {
        byte[] source = {1, 2, 3};
        SignedPayload.InlineCbor payload = new SignedPayload.InlineCbor(
                source, "digest", "hash");
        SignedPayload.InlineCbor expected = new SignedPayload.InlineCbor(
                new byte[]{1, 2, 3}, "digest", "hash");
        int originalHashCode = payload.hashCode();

        source[0] = 99;
        byte[] returned = payload.cbor();
        returned[1] = 88;

        assertArrayEquals(new byte[]{1, 2, 3}, payload.cbor());
        assertEquals(expected, payload);
        assertEquals(originalHashCode, payload.hashCode());
    }

    @Test
    void inlinePayloadDiagnosticsDoNotRenderSignedBytes() {
        SignedPayload.InlineCbor payload = new SignedPayload.InlineCbor(
                new byte[]{12, 34, 56}, "digest", "hash");

        assertEquals("InlineCbor[cborLength=3, sha256=digest, transactionHash=hash]",
                payload.toString());
        assertFalse(payload.toString().contains("12"));
    }
}
