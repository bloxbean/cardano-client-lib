package com.bloxbean.cardano.client.crypto.bip39;

public enum Words {
    TWELVE(128),
    FIFTEEN(160),
    EIGHTEEN(192),
    TWENTY_ONE(224),
    TWENTY_FOUR(256);

    private final int bitLength;

    private Words(int bitLength) {
        this.bitLength = bitLength;
    }

    public int bitLength() {
        return this.bitLength;
    }

    public int byteLength() {
        return this.bitLength / 8;
    }
}
