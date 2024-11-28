package com.bloxbean.cardano.client.governance.keys;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPrivateKey;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.governance.cip105.DRepId;
import lombok.NonNull;

/**
 * DRep key Bech32 encoding
 * CIP 105 - https://github.com/cardano-foundation/CIPs/tree/master/CIP-0105
 */
public class DRepKey {
    public static final String DREP_SK = "drep_sk";
    private static final String DREP_VK = "drep_vk";
    private static final String DREP_XSK = "drep_xsk";
    private static final String DREP_XVK = "drep_xvk";

    private byte[] signingKey;
    private byte[] verificationKey;

    private byte[] extendedSigningKey;
    private byte[] extendedVerificationKey;

    private DRepKey(@NonNull HdKeyPair hdKeyPair) {
        this(hdKeyPair.getPrivateKey(), hdKeyPair.getPublicKey());
    }

    private DRepKey(HdPrivateKey hdPrivateKey, HdPublicKey hdPublicKey) {
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
     * Create a DRepKey object from HdKeyPair
     * @param hdKeyPair HdKeyPair
     * @return DRepKey
     */
    public static DRepKey from(HdKeyPair hdKeyPair) {
        return new DRepKey(hdKeyPair);
    }

    /**
     * Create a DRepKey object from HdPrivateKey
     * @param hdPrivateKey HdPrivateKey
     * @return DRepKey
     */
    public static DRepKey from(HdPrivateKey hdPrivateKey) {
        return new DRepKey(hdPrivateKey, null);
    }

    /**
     * Create a DRepKey object from HdPublicKey
     * @param hdPublicKey HdPublicKey
     * @return DRepKey
     */
    public static DRepKey from(HdPublicKey hdPublicKey) {
        return new DRepKey(null, hdPublicKey);
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
     * @return bech32 encoded signing key
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
     * @return bech32 encoded verification key
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
     * @return bech32 encoded extended signing key
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
     * @return bech32 encoded extended verification key
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
     * Get the DRepId
     * @return DRepId
     */
    public String dRepId() {
        if (verificationKey == null) return null;
        return drepId(verificationKeyHash());
    }

    //-- static methods to generate bech32 encoding directly from key bytes

    /**
     * Get DRep Script Id from script hash
     * @param scriptHash script hash
     * @return bech32 encoded script hash
     */
    public static String dRepScriptId(String scriptHash) {
        return DRepId.fromScriptHash(scriptHash);
    }

    /**
     * Get DRep Script Id from script hash
     * @param scriptHash script hash
     * @return bech32 encoded script hash
     */
    public static String dRepScriptId(byte[] scriptHash) {
        return DRepId.fromScriptHash(scriptHash);
    }

    /**
     * Get drepId from key hash
     * @param keyHash key hash
     * @return drepId
     */
    public static String drepId(byte[] keyHash) {
        return DRepId.fromKeyHash(keyHash);
    }

    /**
     * Get the bech32 encoded signing key
     * @param signingKey signing key
     * @return bech32 encoded signing key
     */
    public static String bech32SigningKey(byte[] signingKey) {
        return Bech32.encode(signingKey, DREP_SK);
    }

    /**
     * Get the bech32 encoded verification key
     * @param verificationKey verification key
     * @return bech32 encoded verification key
     */
    public static String bech32VerificationKey(byte[] verificationKey) {
        return Bech32.encode(verificationKey, DREP_VK);
    }

    /**
     * Get the bech32 encoded extended signing key
     * @param extendedSigningKey extended signing key
     * @return bech32 encoded extended signing key
     */
    public static String bech32ExtendedSigningKey(byte[] extendedSigningKey) {
        return Bech32.encode(extendedSigningKey, DREP_XSK);
    }

    /**
     * Get the bech32 encoded extended verification key
     * @param extendedVerificationKey extended verification key
     * @return bech32 encoded extended verification key
     */
    public static String bech32ExtendedVerificationKey(byte[] extendedVerificationKey) {
        return Bech32.encode(extendedVerificationKey, DREP_XVK);
    }
}
