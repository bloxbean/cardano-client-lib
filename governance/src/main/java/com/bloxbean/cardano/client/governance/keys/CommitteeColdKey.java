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
 * Committee cold key Bech32 encoding
 * CIP 105 - https://github.com/cardano-foundation/CIPs/tree/master/CIP-0105
 */
public class CommitteeColdKey {
    private static final String CC_COLD_KEY = "cc_cold_sk";
    private static final String CC_COLD_VK = "cc_cold_vk";
    private static final String CC_COLD_XSK = "cc_cold_xsk";
    private static final String CC_COLD_XVK = "cc_cold_xvk";

    private static final String CC_COLD_VKH = "cc_cold_vkh";
    private static final String CC_COLD_SCRIPT = "cc_cold_script";

    private byte[] signingKey;
    private byte[] verificationKey;

    private byte[] extendedSigningKey;
    private byte[] extendedVerificationKey;

    private CommitteeColdKey(@NonNull HdKeyPair hdKeyPair) {
        this(hdKeyPair.getPrivateKey(), hdKeyPair.getPublicKey());
    }

    private CommitteeColdKey(HdPrivateKey hdPrivateKey, HdPublicKey hdPublicKey) {
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
     * Create a CommitteeColdKey object from HdKeyPair
     * @param hdKeyPair HdKeyPair
     * @return CommitteeColdKey
     */
    public static CommitteeColdKey from(HdKeyPair hdKeyPair) {
        return new CommitteeColdKey(hdKeyPair);
    }

    /**
     * Create a CommitteeColdKey object from HdPrivateKey
     *
     * @param hdPrivateKey
     * @return CommitteeColdKey
     */
    public static CommitteeColdKey from(HdPrivateKey hdPrivateKey) {
        return new CommitteeColdKey(hdPrivateKey, null);
    }

    /**
     * Create a CommitteeColdKey object from HdPublicKey
     * @param hdPublicKey HdPublicKey
     * @return CommitteeColdKey
     */
    public static CommitteeColdKey from(HdPublicKey hdPublicKey) {
        return new CommitteeColdKey(null, hdPublicKey);
    }

    /**
     * Get the signing key
     * @return byte[] signing key
     */
    public byte[] signingKey() {
        return signingKey;
    }

    /**
     * Get the bech32 encoded signing key
     * @return String bech32 encoded signing key or null if signing key is null
     */
    public String bech32SigningKey() {
        if (signingKey == null) return null;
        return bech32SigningKey(signingKey);
    }

    /**
     * Get the verification key
     * @return byte[] verification key
     */
    public byte[] verificationKey() {
        return verificationKey;
    }

    /**
     * Get the bech32 encoded verification key
     * @return String bech32 encoded verification key or null if verification key is null
     */
    public String bech32VerificationKey() {
        if (verificationKey == null) return null;
        return bech32VerificationKey(verificationKey);
    }

    /**
     * Get the extended signing key
     * @return byte[] extended signing key
     */
    public byte[] extendedSigningKey() {
        return extendedSigningKey;
    }

    /**
     * Get the bech32 encoded extended signing key
     * @return String bech32 encoded extended signing key or null if extended signing key is null
     */
    public String bech32ExtendedSigningKey() {
        if (extendedSigningKey == null) return null;
        return bech32ExtendedSigningKey(extendedSigningKey);
    }

    /**
     * Get the extended verification key
     * @return byte[] extended verification key
     */
    public byte[] extendedVerificationKey() {
        return extendedVerificationKey;
    }

    /**
     * Get the bech32 encoded extended verification key
     * @return String bech32 encoded extended verification key or null if extended verification key is null
     */
    public String bech32ExtendedVerificationKey() {
        if (extendedVerificationKey == null) return null;
        return bech32ExtendedVerificationKey(extendedVerificationKey);
    }

    /**
     * Get verification key hash (Blake2b224 hash)
     * @return byte[] verification key hash
     */
    public byte[] verificationKeyHash() {
        if (verificationKey == null) return null;
        return Blake2bUtil.blake2bHash224(verificationKey);
    }

    /**
     * Get the bech32 encoded verification key hash (Blake2b224 hash)
     * @return String bech32 encoded verification key hash or null if verification key hash is null
     */
    public String bech32VerificationKeyHash() {
        return bech32VerificationKeyHash(verificationKeyHash());
    }

    /**
     * Generates a CIP-129 Committee Cold ID by utilizing the verification key hash.
     *
     * @return A bech32 encoded string representing the Committee Cold ID derived from the verification key hash.
     */
    public String id() {
        return GovId.ccColdFromKeyHash(verificationKeyHash());
    }

    //-- static methods to get bech32 encoded keys from key bytes

    /**
     * Converts a script hash to bech32 committee cold key id
     *
     * @param scriptHash the hex string representation of the script hash
     * @return the script ID derived from the given script hash
     */
    public static String scriptId(String scriptHash) {
        return scriptId(HexUtil.decodeHexString(scriptHash));
    }

    /**
     * Converts a script hash to bech32 committee cold key id
     *
     * @param scriptHash the script hash represented as a byte array
     * @return the script ID derived from the given script hash
     */
    public static String scriptId(byte[] scriptHash) {
        return GovId.ccColdFromScriptHash(scriptHash);
    }

    /**
     * Get the bech32 encoded script hash
     * @param scriptHash script hash
     * @return String bech32 encoded script hash
     */
    public static String bech32ScriptHash(String scriptHash) {
        return bech32ScriptHash(HexUtil.decodeHexString(scriptHash));
    }

    /**
     * Get the bech32 encoded script hash
     * @param scriptHash script hash
     * @return String bech32 encoded script hash
     */
    public static String bech32ScriptHash(byte[] scriptHash) {
        return Bech32.encode(scriptHash, CC_COLD_SCRIPT);
    }

    /**
     * Get the bech32 encoded verification key hash
     * @param verificationKeyHash verification key hash
     * @return String bech32 encoded verification key hash
     */
    public static String bech32VerificationKeyHash(String verificationKeyHash) {
        return bech32VerificationKeyHash(HexUtil.decodeHexString(verificationKeyHash));
    }

    /**
     * Get the bech32 encoded verification key hash
     * @param verificationKeyHash verification key hash
     * @return String bech32 encoded verification key hash
     */
    public static String bech32VerificationKeyHash(byte[] verificationKeyHash) {
        return Bech32.encode(verificationKeyHash, CC_COLD_VKH);
    }


    /**
     * Get the bech32 encoded signing key
     * @param signingKey signing key
     * @return String bech32 encoded signing key
     */
    public static String bech32SigningKey(byte[] signingKey) {
        return Bech32.encode(signingKey, CC_COLD_KEY);
    }

    /**
     * Get the bech32 encoded verification key
     * @param verificationKey verification key
     * @return String bech32 encoded verification key
     */
    public static String bech32VerificationKey(byte[] verificationKey) {
        return Bech32.encode(verificationKey, CC_COLD_VK);
    }

    /**
     * Get the bech32 encoded extended signing key
     * @param extendedSigningKey extended signing key
     * @return String bech32 encoded extended signing key
     */
    public static String bech32ExtendedSigningKey(byte[] extendedSigningKey) {
        return Bech32.encode(extendedSigningKey, CC_COLD_XSK);
    }

    /**
     * Get the bech32 encoded extended verification key
     * @param extendedVerificationKey extended verification key
     * @return String bech32 encoded extended verification key
     */
    public static String bech32ExtendedVerificationKey(byte[] extendedVerificationKey) {
        return Bech32.encode(extendedVerificationKey, CC_COLD_XVK);
    }

}
