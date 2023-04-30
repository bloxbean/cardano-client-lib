package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.util.HexUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Credential class represents key hash or script hash
 */
@Getter
@ToString
@EqualsAndHashCode
public class Credential {
    //key hash or script hash
    private byte[] bytes;
    private CredentialType type;

    private Credential(byte[] bytes, CredentialType type) {
        this.bytes = bytes;
        this.type = type;
    }

    /**
     * Returns credential bytes
     * @return byte array
     */
    public byte[] getBytes() {
        return bytes;
    }

    /**
     * Creates Credential from key hash
     * @param keyHash - hex encoded key hash
     * @return Credential
     */
    public static Credential fromKey(String keyHash) {
        return new Credential(HexUtil.decodeHexString(keyHash), CredentialType.Key);
    }

    /**
     * Creates Credential from key hash
     * @param keyHash - byte array key hash
     * @return Credential
     */
    public static Credential fromKey(byte[] keyHash) {
        return new Credential(keyHash, CredentialType.Key);
    }

    /**
     * Creates Credential from script hash
     * @param scriptHash - hex encoded script hash
     * @return Credential
     */
    public static Credential fromScript(String scriptHash) {
        return new Credential(HexUtil.decodeHexString(scriptHash), CredentialType.Script);
    }

    /**
     * Creates Credential from script hash
     * @param scriptHash - byte array script hash
     * @return Credential
     */
    public static Credential fromScript(byte[] scriptHash) {
        return new Credential(scriptHash, CredentialType.Script);
    }
}
