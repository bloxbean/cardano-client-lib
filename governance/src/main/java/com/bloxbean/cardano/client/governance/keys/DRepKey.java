package com.bloxbean.cardano.client.governance.keys;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPrivateKey;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.governance.GovId;
import com.bloxbean.cardano.client.governance.LegacyDRepId;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.NonNull;

/**
 * DRep key Bech32 encoding
 * CIP 105 - https://github.com/cardano-foundation/CIPs/tree/master/CIP-0105
 *
 * This class also returns DRep Ids in CIP-129 format.
 */
public class DRepKey {
    public  static final String DREP_SK = "drep_sk";
    private static final String DREP_VK = "drep_vk";
    private static final String DREP_XSK = "drep_xsk";
    private static final String DREP_XVK = "drep_xvk";
    private static final String DREP_VKH = "drep_vkh";
    private static final String DREP_SCRIPT = "drep_script";

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
     * Get the Bech32 encoded verification key hash
     * If the verification key is not set, the method returns null.
     *
     * @return bech32 encoded verification key hash
     */
    public String bech32VerificationKeyHash() {
        if  (verificationKey == null) return null;

        return bech32VerificationKeyHash(verificationKeyHash());
    }

    /**
     * Get the DRepId (CIP-129)
     * @return DRepId
     */
    public String dRepId() {
        if (verificationKey == null) return null;
        return drepId(verificationKeyHash());
    }

    /**
     * Get CIP 105 (deprecated) compatible drep id
     *
     * @deprecated Use drepId() instead
     *
     * @return bech32 encoded drep id (CIP 105 deprecatd version)
     */
    @Deprecated(since = "0.6.3")
    public String legacyDRepId() {
        if (verificationKey == null) return null;
        return LegacyDRepId.fromKeyHash(verificationKeyHash());
    }

    //-- static methods to generate bech32 encoding directly from key bytes

    /**
     * Get DRep Script Id from script hash (CIP-129)
     * @param scriptHash script hash
     * @return bech32 encoded script hash
     */
    public static String dRepScriptId(String scriptHash) {
        return GovId.drepFromScriptHash(HexUtil.decodeHexString(scriptHash));
    }

    /**
     * Get DRep Script Id from script hash (CIP-129)
     * @param scriptHash script hash
     * @return bech32 encoded script hash
     */
    public static String dRepScriptId(byte[] scriptHash) {
        return GovId.drepFromScriptHash(scriptHash);
    }

    /**
     * Get drepId from key hash (CIP-129)
     * @param keyHash key hash
     * @return drepId
     */
    public static String drepId(byte[] keyHash) {
        return GovId.drepFromKeyHash(keyHash);
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

    /**
     * Get the bech32 encoded verification key hash
     *
     * @param keyHash the byte array representing the verification key hash to encode
     * @return a Bech32 encoded string of the verification key hash
     */
    public static String bech32VerificationKeyHash(byte[] keyHash) {
        return Bech32.encode(keyHash, DREP_VKH);
    }

    /**
     * Get the bech32 encoded script hash
     *
     * @param scriptHash the byte array representing the script hash to encode
     * @return a Bech32 encoded string of the script hash
     */
    public static String bech32ScriptHash(byte[] scriptHash) {
        return Bech32.encode(scriptHash, DREP_SCRIPT);
    }

    /**
     * Encodes the verification key hash in Bech32 format.
     *
     * @param verificationKey the verification key from which the hash is computed
     * @return a Bech32 encoded string representation of the verification key hash
     */
    public static String bech32VerificationKeyHash(VerificationKey verificationKey) {
        byte[] keyHash = Blake2bUtil.blake2bHash224(verificationKey.getBytes());
        return Bech32.encode(keyHash, DREP_VKH);
    }

}
