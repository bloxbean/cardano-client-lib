package com.bloxbean.cardano.client.crypto.bip32.key;

import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;

//This file is originally from https://github.com/semuxproject/semux-core
public class HdKey {
    private byte[] version;
    private int depth;
    private byte[] childNumber;
    private byte[] chainCode;
    private byte[] keyData;


    HdKey() {
    }

    public void setVersion(byte[] version) {
        this.version = version;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setChildNumber(byte[] childNumber) {
        this.childNumber = childNumber;
    }

    public void setChainCode(byte[] chainCode) {
        this.chainCode = chainCode;
    }

    public void setKeyData(byte[] keyData) {
        this.keyData = keyData;
    }

    public byte[] getChainCode() {
        return chainCode;
    }

    public byte[] getBytes() {
        return BytesUtil.merge(keyData, chainCode);
    }

    public int getDepth() {
        return depth;
    }

    public byte[] getKeyData() {
        return keyData;
    }

    public byte[] getVersion() {
        return version;
    }

}
