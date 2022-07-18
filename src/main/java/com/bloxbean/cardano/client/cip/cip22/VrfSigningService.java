package com.bloxbean.cardano.client.cip.cip22;

import com.muquit.libsodiumjna.SodiumLibrary;
import com.muquit.libsodiumjna.exceptions.SodiumLibraryException;
import org.bouncycastle.util.encoders.Hex;

import java.util.Arrays;

public class VrfSigningService {

    private static final String CIP_0022 = "cip-0022";

    public VrfSigningService() {
        this("/usr/local/lib/libsodium.dylib");
    }

    public VrfSigningService(String libsodiumPath) {
        SodiumLibrary.setLibraryPath(libsodiumPath);
    }

    public byte[] sign(byte[] message, String domain, byte[] vrfSkey) throws SodiumLibraryException {
        var prefix = String.format("%s%s", CIP_0022, domain);
        var challenge = org.bouncycastle.util.Arrays.concatenate(Hex.encode(prefix.getBytes()), message);
        var challengeHash = SodiumLibrary.cryptoBlake2bHash(Hex.decode(challenge), null);
        var signature = SodiumLibrary.cryptoVrfProve(vrfSkey, challengeHash);
        return signature;
    }

    public boolean verify(byte[] originalMessage, byte[] signedMessage, String domain, byte[] vrfVkey) throws SodiumLibraryException {
        var prefix = String.format("%s%s", CIP_0022, domain);
        var challenge = org.bouncycastle.util.Arrays.concatenate(Hex.encode(prefix.getBytes()), originalMessage);
        var challengeHash = SodiumLibrary.cryptoBlake2bHash(Hex.decode(challenge), null);
        var signatureHash = SodiumLibrary.cryptoVrfProofToHash(signedMessage);
        var verification = SodiumLibrary.cryptoVrfVerify(vrfVkey, signedMessage, challengeHash);
        return Arrays.equals(signatureHash, verification);
    }

    public byte[] getVrfVkey(byte[] skey) throws SodiumLibraryException {
        return SodiumLibrary.cryptoVrfSkeyToVkey(skey);
    }

}
