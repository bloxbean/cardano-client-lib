package com.bloxbean.cardano.client.crypto;

public class Keys {
    private VerificationKey vkey;
    private SecretKey skey;

    public Keys(SecretKey skey, VerificationKey vkey) {
        this.skey = skey;
        this.vkey = vkey;
    }

    public VerificationKey getVkey() {
        return vkey;
    }

    public void setVkey(VerificationKey vkey) {
        this.vkey = vkey;
    }

    public SecretKey getSkey() {
        return skey;
    }

    public void setSkey(SecretKey skey) {
        this.skey = skey;
    }
}
