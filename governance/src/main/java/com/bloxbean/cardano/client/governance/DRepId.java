package com.bloxbean.cardano.client.governance;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.DRepType;
import com.bloxbean.cardano.client.util.HexUtil;

public class DRepId {
    public static final String DREP_ID_PREFIX = "drep";
    public static final String DREP_ID_SCRIPT_PREFIX = "drep_script";

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
        return fromKeyHash(HexUtil.decodeHexString(keyHash));
    }

    public static String fromKeyHash(byte[] keyHash) {
        String drepId = Bech32.encode(keyHash, DREP_ID_PREFIX);
        return drepId;
    }

    public static String fromScriptHash(String scriptHash) {
        return fromScriptHash(HexUtil.decodeHexString(scriptHash));
    }

    public static String fromScriptHash(byte[] scriptHash) {
        String drepId = Bech32.encode(scriptHash, DREP_ID_SCRIPT_PREFIX);
        return drepId;
    }

    public static DRep toDrep(String drepId, DRepType drepType) {
        byte[] bytes = Bech32.decode(drepId).data;

        if (drepType == DRepType.ADDR_KEYHASH) {
            return DRep.addrKeyHash(HexUtil.encodeHexString(bytes));
        } else if (drepType == DRepType.SCRIPTHASH) {
            return DRep.scriptHash(HexUtil.encodeHexString(bytes));
        } else {
            throw new IllegalArgumentException("Invalid DrepType");
        }
    }
}
