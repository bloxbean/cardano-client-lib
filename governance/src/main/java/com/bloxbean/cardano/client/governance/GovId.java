package com.bloxbean.cardano.client.governance;

import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.util.HexUtil;

/**
 * The GovId class provides methods for generating governance related ids.
 * It implements CIP-129
 */
public class GovId {
    private static final byte CC_HOT_KEY_TYPE = 0b0000_0000;
    private static final byte CC_COLD_KEY_TYPE = 0b0001_0000;
    private static final byte DREP_KEY_TYPE = 0b0010_0000;

    private static final byte KEY_HASH_CRED_TYPE = 0b0000_0010;
    private static final byte SCRIPT_HASH_CRED_TYPE = 0b0000_0011;

    private static final String CC_COLD_PREFIX = "cc_cold";
    private static final String CC_HOT_PREFIX = "cc_hot";
    private static final String DREP_PREFIX = "drep";
    private static final String GOV_ACTION_PREFIX = "gov_action";

    private GovId() {

    }

    public static String ccColdFromKeyHash(byte[] keyHash) {
        byte[] idBytes = getIdentifierBytes(CC_COLD_KEY_TYPE, KEY_HASH_CRED_TYPE, keyHash);
        return Bech32.encode(idBytes, CC_COLD_PREFIX);
    }

    public static String ccColdFromScriptHash(byte[] scriptHash) {
        byte[] idBytes = getIdentifierBytes(CC_COLD_KEY_TYPE, SCRIPT_HASH_CRED_TYPE, scriptHash);
        return Bech32.encode(idBytes, CC_COLD_PREFIX);
    }

    public static String ccHotFromKeyHash(byte[] keyHash) {
        byte[] idBytes = getIdentifierBytes(CC_HOT_KEY_TYPE, KEY_HASH_CRED_TYPE, keyHash);
        return Bech32.encode(idBytes, CC_HOT_PREFIX);
    }

    public static String ccHotFromScriptHash(byte[] scriptHash) {
        byte[] idBytes = getIdentifierBytes(CC_HOT_KEY_TYPE, SCRIPT_HASH_CRED_TYPE, scriptHash);
        return Bech32.encode(idBytes, CC_HOT_PREFIX);
    }

    public static String drepFromKeyHash(byte[] keyHash) {
        byte[] idBytes = getIdentifierBytes(DREP_KEY_TYPE, KEY_HASH_CRED_TYPE, keyHash);
        return Bech32.encode(idBytes, DREP_PREFIX);
    }

    public static String drepFromScriptHash(byte[] scriptHash) {
        byte[] idBytes = getIdentifierBytes(DREP_KEY_TYPE, SCRIPT_HASH_CRED_TYPE, scriptHash);
        return Bech32.encode(idBytes, DREP_PREFIX);
    }

    public static CredentialType credType(String bech32GovId) {
        byte[] govIdBytes = Bech32.decode(bech32GovId).data;
        byte header = govIdBytes[0];

        var credType = header & 0b0000_1111;

        switch (credType) {
            case KEY_HASH_CRED_TYPE:
                return CredentialType.Key;
            case SCRIPT_HASH_CRED_TYPE:
                return CredentialType.Script;
            default:
                throw new IllegalArgumentException("Invalid credential type : " + credType);
        }
    }

    public static CredentialType credTypeFromIdBytes(String govIdBytes) {
        return credTypeFromIdBytes(HexUtil.decodeHexString(govIdBytes));
    }

    public static CredentialType credTypeFromIdBytes(byte[] govIdBytes) {
        byte header = govIdBytes[0];
        var credType = header & 0b0000_1111;

        switch (credType) {
            case KEY_HASH_CRED_TYPE:
                return CredentialType.Key;
            case SCRIPT_HASH_CRED_TYPE:
                return CredentialType.Script;
            default:
                throw new IllegalArgumentException("Invalid credential type : " + credType);
        }
    }

    public static String govAction(String txHash, int index) {
        String indexHex = String.format("%02x", index);

        byte[] mergedBytes = HexUtil.decodeHexString(txHash + indexHex);
        return Bech32.encode(mergedBytes, GOV_ACTION_PREFIX);
    }

    private static byte[] getIdentifierBytes(byte keyType, byte credType, byte[] keyHash) {
        byte header = getHeader(keyType, credType);

        byte[] idBytes = new byte[1 + keyHash.length];
        idBytes[0] = header;

        System.arraycopy(keyHash, 0, idBytes, 1, keyHash.length);
        return idBytes;
    }

    private static byte getHeader(byte keyType, byte credType) {
        return (byte) (keyType | credType & 0xF);
    }
}
