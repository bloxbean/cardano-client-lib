package com.bloxbean.cardano.client.cip.cip22;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import com.muquit.libsodiumjna.exceptions.SodiumLibraryException;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrfSigningServiceTest {

    @Test
    public void vrfVkeyFromVrfSkey() throws CborException, SodiumLibraryException {

        var skeyCbor = Hex.decode("5840adb9c97bec60189aa90d01d113e3ef405f03477d82a94f81da926c90cd46a374e0ff2371508ac339431b50af7d69cde0f120d952bb876806d3136f9a7fda4381");
        var vrfSkey = (ByteString) CborDecoder.decode(skeyCbor).get(0);

        var vkeyCbor = Hex.decode("5820e0ff2371508ac339431b50af7d69cde0f120d952bb876806d3136f9a7fda4381");
        var vrfVkey = (ByteString) CborDecoder.decode(vkeyCbor).get(0);

        var vrfSigningService = new VrfSigningService();

        var actualVrfVkey = vrfSigningService.getVrfVkey(vrfSkey.getBytes());

        assertArrayEquals(vrfVkey.getBytes(), actualVrfVkey);

    }

    @Test
    public void testVrfValidation1() throws CryptoException, CborException, SodiumLibraryException {

        byte[] skeyCbor = Hex.decode("5840adb9c97bec60189aa90d01d113e3ef405f03477d82a94f81da926c90cd46a374e0ff2371508ac339431b50af7d69cde0f120d952bb876806d3136f9a7fda4381");
        ByteString vrfSkeyBytes = (ByteString) CborDecoder.decode(skeyCbor).get(0);

        byte[] vkeyCbor = Hex.decode("5820e0ff2371508ac339431b50af7d69cde0f120d952bb876806d3136f9a7fda4381");
        ByteString vrfVkeyBytes = (ByteString) CborDecoder.decode(vkeyCbor).get(0);

        VrfSigningService vrfSigningService = new VrfSigningService();

        String domain = "cardano.org";

        byte[] originalMessage = Hex.encode("message to sign".getBytes(StandardCharsets.UTF_8));

        byte[] signedMessage = vrfSigningService.sign(originalMessage, domain, vrfSkeyBytes.getBytes());

        boolean validSignature = vrfSigningService.verify(originalMessage, signedMessage, domain, vrfVkeyBytes.getBytes());

        assertTrue(validSignature);

    }

}