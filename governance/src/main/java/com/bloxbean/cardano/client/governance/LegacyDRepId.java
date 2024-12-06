package com.bloxbean.cardano.client.governance;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.DRepType;
import com.bloxbean.cardano.client.util.HexUtil;

/**
 * DEPRECATED: CIP 105 DRep Id implementation.
 * This class is deprecated.
 *
 * @deprecated Use {@link com.bloxbean.cardano.client.governance.GovId} for CIP-129 implementation
 */
@Deprecated(since = "0.6.3")
public class LegacyDRepId {
    public static final String DREP_ID_PREFIX = "drep";
    public static final String DREP_ID_SCRIPT_PREFIX = "drep_script";

    public static String fromVerificationKey(VerificationKey verificationKey) {
        byte[] keyHash = Blake2bUtil.blake2bHash224(verificationKey.getBytes());
        return Bech32.encode(keyHash, DREP_ID_PREFIX);
    }

    public static String fromVerificationKeyBytes(byte[] bytes) {
        byte[] keyHash = Blake2bUtil.blake2bHash224(bytes);
        return Bech32.encode(keyHash, DREP_ID_PREFIX);
    }

    public static String fromKeyHash(String keyHash) {
        return fromKeyHash(HexUtil.decodeHexString(keyHash));
    }

    public static String fromKeyHash(byte[] keyHash) {
        return Bech32.encode(keyHash, DREP_ID_PREFIX);
    }

    public static String fromScriptHash(String scriptHash) {
        return fromScriptHash(HexUtil.decodeHexString(scriptHash));
    }

    public static String fromScriptHash(byte[] scriptHash) {
        return Bech32.encode(scriptHash, DREP_ID_SCRIPT_PREFIX);
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
