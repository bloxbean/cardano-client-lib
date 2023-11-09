package com.bloxbean.cardano.client.governance;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.util.HexUtil;

public class DRepId {
    public static final String DREP_ID_PREFIX = "drep";

    public static String fromVerificationKey(VerificationKey verificationKey) {
        String keyHash = KeyGenUtil.getKeyHash(verificationKey);
        String drepId = Bech32.encode(HexUtil.decodeHexString(keyHash), DREP_ID_PREFIX);
        return drepId;
    }

    public static String fromVerificationKeyBytes(byte[] bytes) {
        String keyHash = KeyGenUtil.getKeyHash(bytes);
        String drepId = Bech32.encode(HexUtil.decodeHexString(keyHash), DREP_ID_PREFIX);
        return drepId;
    }

    public static String fromKeyHash(String keyHash) {
        String drepId = Bech32.encode(HexUtil.decodeHexString(keyHash), DREP_ID_PREFIX);
        return drepId;
    }
}
