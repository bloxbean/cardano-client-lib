package com.bloxbean.cardano.client.governance.keys;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPrivateKey;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.governance.GovId;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.NonNull;

/**
 * Committee hot key Bech32 encoding
 * CIP 105 - https://github.com/cardano-foundation/CIPs/tree/master/CIP-0105
 */
public class CommitteeHotKey {
    private static final String CC_HOT_KEY = "cc_hot_sk";
    private static final String CC_HOT_VK = "cc_hot_vk";
    private static final String CC_HOT_XSK = "cc_hot_xsk";
    private static final String CC_HOT_XVK = "cc_hot_xvk";
    private static final String CC_HOT_VKH = "cc_hot_vkh";
    private static final String CC_HOT_SCRIPT = "cc_hot_script";

    private byte[] signingKey;
    private byte[] verificationKey;

    private byte[] extendedSigningKey;
    private byte[] extendedVerificationKey;

    private CommitteeHotKey(@NonNull HdKeyPair hdKeyPair) {
        this(hdKeyPair.getPrivateKey(), hdKeyPair.getPublicKey());
    }

    private CommitteeHotKey(HdPrivateKey hdPrivateKey, HdPublicKey hdPublicKey) {
        if (hdPrivateKey != null) {
            this.signingKey = hdPrivateKey.getKeyData();
            this.extendedSigningKey = hdPrivateKey.getBytes();
        }

        if (hdPublicKey != null) {
            this.verificationKey = hdPublicKey.getKeyData();
            this.extendedVerificationKey = hdPublicKey.getBytes();
        }
    }

    /**
     * Create a CommitteeHotKey object from HdKeyPair
     * @param hdKeyPair HdKeyPair
     * @return CommitteeHotKey
     */
    public static CommitteeHotKey from(HdKeyPair hdKeyPair) {
        return new CommitteeHotKey(hdKeyPair);
    }

    /**
     * Create a CommitteeHotKey object from HdPrivateKey
     * @param hdPrivateKey HdPrivateKey
     * @return CommitteeHotKey
     */
    public static CommitteeHotKey from(HdPrivateKey hdPrivateKey) {
        return new CommitteeHotKey(hdPrivateKey, null);
    }

    /**
     * Create a CommitteeHotKey object from HdPublicKey
     * @param hdPublicKey HdPublicKey
     * @return CommitteeHotKey
     */
    public static CommitteeHotKey from(HdPublicKey hdPublicKey) {
        return new CommitteeHotKey(null, hdPublicKey);
    }

    /**
     * Get the signing key
     * @return signing key
     */
    public byte[] signingKey() {
        return signingKey;
    }

    /**
     * Get the bech32 encoded signing key
     * @return bech32 encoded signing key or null if signing key is null
     */
    public String bech32SigningKey() {
        if (signingKey == null) return null;
        return bech32SigningKey(signingKey);
    }

    /**
     * Get the verification key
     * @return verification key
     */
    public byte[] verificationKey() {
        return verificationKey;
    }

    /**
     * Get the bech32 encoded verification key
     * @return bech32 encoded verification key or null if verification key is null
     */
    public String bech32VerificationKey() {
        if (verificationKey == null) return null;
        return bech32VerificationKey(verificationKey);
    }

    /**
     * Get the extended signing key
     * @return extended signing key
     */
    public byte[] extendedSigningKey() {
        return extendedSigningKey;
    }

    /**
     * Get the bech32 encoded extended signing key
     * @return bech32 encoded extended signing key or null if extended signing key is null
     */
    public String bech32ExtendedSigningKey() {
        if (extendedSigningKey == null) return null;
        return bech32ExtendedSigningKey(extendedSigningKey);
    }

    /**
     * Get the extended verification key
     * @return
     */
    public byte[] extendedVerificationKey() {
        return extendedVerificationKey;
    }

    /**
     * Get the bech32 encoded extended verification key
     * @return bech32 encoded extended verification key or null if extended verification key is null
     */
    public String bech32ExtendedVerificationKey() {
        if (extendedVerificationKey == null) return null;
        return bech32ExtendedVerificationKey(extendedVerificationKey);
    }

    /**
     * Get the verification key hash
     * @return byte[] verification key hash
     */
    public byte[] verificationKeyHash() {
        if (verificationKey == null) return null;
        return Blake2bUtil.blake2bHash224(verificationKey);
    }

    /**
     * Get the bech32 encoded verification key hash
     * @return String bech32 encoded verification key hash
     */
    public String bech32VerificationKeyHash() {
        return bech32VerificationKeyHash(verificationKeyHash());
    }

    /**
     * Retrieves the bech32 encoded CIP-129 hot credential committee identifier derived from the verification key hash.
     *
     * @return the bech32 encoded hot credential committee identifier.
     */
    public String id() {
        return GovId.ccHotFromKeyHash(verificationKeyHash());
    }

    //-- static methods to get bech32 encoded keys from key bytes

    public static String scriptId(String scriptHash) {
        return scriptId(HexUtil.decodeHexString(scriptHash));
    }

    public static String scriptId(byte[] scriptHash) {
        return GovId.ccHotFromScriptHash(scriptHash);
    }

    /**
     * Get the bech32 encoded script hash
     * @param scriptHash script hash
     * @return bech32 encoded script hash
     */
    public static String bech32ScriptHash(String scriptHash) {
        return bech32ScriptHash(HexUtil.decodeHexString(scriptHash));
    }

    /**
     * Get the bech32 encoded script hash
     * @param scriptHash script hash
     * @return bech32 encoded script hash
     */
    public static String bech32ScriptHash(byte[] scriptHash) {
        return Bech32.encode(scriptHash, CC_HOT_SCRIPT);
    }

    /**
     * Get the bech32 encoded verification key hash
     * @param verificationKeyHash verification key hash
     * @return bech32 encoded verification key hash
     */
    public static String bech32VerificationKeyHash(String verificationKeyHash) {
        return bech32VerificationKeyHash(HexUtil.decodeHexString(verificationKeyHash));
    }

    /**
     * Get the bech32 encoded verification key hash
     * @param verificationKeyHash verification key hash
     * @return bech32 encoded verification key hash
     */
    public static String bech32VerificationKeyHash(byte[] verificationKeyHash) {
        return Bech32.encode(verificationKeyHash, CC_HOT_VKH);
    }

    /**
     * Get the bech32 encoded signing key
     * @param signingKey signing key
     * @return bech32 encoded signing key
     */
    public static String bech32SigningKey(byte[] signingKey) {
        return Bech32.encode(signingKey, CC_HOT_KEY);
    }

    /**
     * Get the bech32 encoded verification key
     * @param verificationKey verification key
     * @return bech32 encoded verification key
     */
    public static String bech32VerificationKey(byte[] verificationKey) {
        return Bech32.encode(verificationKey, CC_HOT_VK);
    }

    /**
     * Get the bech32 encoded extended signing key
     * @param extendedSigningKey extended signing key
     * @return bech32 encoded extended signing key
     */
    public static String bech32ExtendedSigningKey(byte[] extendedSigningKey) {
        return Bech32.encode(extendedSigningKey, CC_HOT_XSK);
    }

    /**
     * Get the bech32 encoded extended verification key
     * @param extendedVerificationKey extended verification key
     * @return bech32 encoded extended verification key
     */
    public static String bech32ExtendedVerificationKey(byte[] extendedVerificationKey) {
        return Bech32.encode(extendedVerificationKey, CC_HOT_XVK);
    }

}
