package com.bloxbean.cardano.client.crypto.bip32.key;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash224;

//This file is originally from https://github.com/semuxproject/semux-core
public class HdPublicKey extends HdKey {

    //Needed during address encoding
    public byte[] getKeyHash() {
        return blake2bHash224(this.getKeyData());
    }

}
