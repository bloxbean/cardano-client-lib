package com.bloxbean.cardano.client.crypto.cip1852;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyGenerator;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;

/**
 * CIP1852 helper class
 */
public class CIP1852 {

    /**
     * Get HdKeyPair from mnemonic phrase
     * @param mnemonicPhrase mnemonic phrase
     * @param derivationPath derivation path
     * @return HdKeyPair
     */
    public HdKeyPair getKeyPairFromMnemonic(String mnemonicPhrase, DerivationPath derivationPath) {
        try {
            byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonicPhrase);

            return getKeyPairFromEntropy(entropy, derivationPath);
        } catch (Exception ex) {
            throw new CryptoException("Mnemonic to KeyPair generation failed", ex);
        }
    }

    /**
     * Generates the root HdKeyPair from the given mnemonic phrase.
     *
     * @param mnemonicPhrase the mnemonic phrase used to generate the HdKeyPair
     * @return the root HdKeyPair derived from the provided mnemonic phrase
     * @throws CryptoException if the mnemonic phrase cannot be converted to entropy or if
     *         the key pair generation fails
     */
    public HdKeyPair getRootKeyPairFromMnemonic(String mnemonicPhrase) {
        var hdKeyGenerator = new HdKeyGenerator();
        try {
            byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonicPhrase);

            return hdKeyGenerator.getRootKeyPairFromEntropy(entropy);
        } catch (Exception ex) {
            throw new CryptoException("Mnemonic to KeyPair generation failed", ex);
        }
    }

    /**
     * Get HdKeyPair from entropy
     * @param entropy entropy
     * @param derivationPath derivation path
     * @return HdKeyPair
     */
    public HdKeyPair getKeyPairFromEntropy(byte[] entropy, DerivationPath derivationPath) {
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromEntropy(entropy);

        HdKeyPair purposeKey = hdKeyGenerator.getChildKeyPair(rootKeyPair, derivationPath.getPurpose().getValue(), derivationPath.getPurpose().isHarden());
        HdKeyPair coinTypeKey = hdKeyGenerator.getChildKeyPair(purposeKey, derivationPath.getCoinType().getValue(), derivationPath.getCoinType().isHarden());
        HdKeyPair accountKey = hdKeyGenerator.getChildKeyPair(coinTypeKey, derivationPath.getAccount().getValue(), derivationPath.getAccount().isHarden());
        HdKeyPair roleKey = hdKeyGenerator.getChildKeyPair(accountKey, derivationPath.getRole().getValue(), derivationPath.getRole().isHarden());

        return hdKeyGenerator.getChildKeyPair(roleKey, derivationPath.getIndex().getValue(), derivationPath.getIndex().isHarden());
    }

    /**
     * Get HdKeyPair from account key
     * @param accountKey account key
     * @param derivationPath derivation path
     * @return HdKeyPair
     */
    public HdKeyPair getKeyPairFromAccountKey(byte[] accountKey, DerivationPath derivationPath) {
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();

        HdKeyPair accountKeyPair = hdKeyGenerator.getAccountKeyPairFromSecretKey(accountKey,  derivationPath);
        HdKeyPair roleKey = hdKeyGenerator.getChildKeyPair(accountKeyPair, derivationPath.getRole().getValue(), derivationPath.getRole().isHarden());

        return hdKeyGenerator.getChildKeyPair(roleKey, derivationPath.getIndex().getValue(), derivationPath.getIndex().isHarden());
    }

    /**
     * Get HdPublicKey from account public key
     * @param accountPubKey account public key
     * @param derivationPath derivation path
     * @return HdPublicKey
     */
    public HdPublicKey getPublicKeyFromAccountPubKey(byte[] accountPubKey, DerivationPath derivationPath) {
        HdPublicKey accountHdPubKey = HdPublicKey.fromBytes(accountPubKey);

        return getPublicKeyFromAccountPubKey(accountHdPubKey, derivationPath.getRole().getValue(), derivationPath.getIndex().getValue());
    }

    /**
     * Derives a hierarchical deterministic (HD) public key for a specific role and index
     * from the given account-level public key.
     *
     * @param accountPubKey the account-level public key as a byte array
     * @param role the specific role in the derivation path for the key
     * @param index the index number in the derivation path for the key
     * @return the derived HD public key for the specified role and index
     * @throws CryptoException if the account-level public key is invalid
     */
    public HdPublicKey getPublicKeyFromAccountPubKey(byte[] accountPubKey, int role, int index) {
        HdPublicKey accountHdPubKey = HdPublicKey.fromBytes(accountPubKey);

        return getPublicKeyFromAccountPubKey(accountHdPubKey, role, index);
    }

    /**
     * Derives a hierarchical deterministic (HD) public key for a specific role and index
     * from the given account-level HD public key.
     *
     * @param accountHdPubKey the account-level HD public key
     * @param role the specific role in the derivation path for the key
     * @param index the index number in the derivation path for the key
     * @return the derived HD public key for the specified role and index
     */
    public static HdPublicKey getPublicKeyFromAccountPubKey(HdPublicKey accountHdPubKey, int role, int index) {
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdPublicKey roleHdPubKey = hdKeyGenerator.getChildPublicKey(accountHdPubKey, role);

        return hdKeyGenerator.getChildPublicKey(roleHdPubKey, index);
    }

    /**
     * Generates an HdKeyPair derived from the given root key and a specified derivation path.
     *
     * @param rootKey the root key represented as a byte array
     * @param derivationPath the hierarchical deterministic (HD) derivation path used to derive the key pair
     * @return the HdKeyPair derived from the provided root key and derivation path
     */
    public HdKeyPair getKeyPairFromRootKey(byte[] rootKey, DerivationPath derivationPath) {
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getKeyPairFromSecretKey(rootKey, HdKeyGenerator.MASTER_PATH);

        HdKeyPair purposeKey = hdKeyGenerator.getChildKeyPair(rootKeyPair, derivationPath.getPurpose().getValue(), derivationPath.getPurpose().isHarden());
        HdKeyPair coinTypeKey = hdKeyGenerator.getChildKeyPair(purposeKey, derivationPath.getCoinType().getValue(), derivationPath.getCoinType().isHarden());
        HdKeyPair accountKey = hdKeyGenerator.getChildKeyPair(coinTypeKey, derivationPath.getAccount().getValue(), derivationPath.getAccount().isHarden());
        HdKeyPair roleKey = hdKeyGenerator.getChildKeyPair(accountKey, derivationPath.getRole().getValue(), derivationPath.getRole().isHarden());

        return hdKeyGenerator.getChildKeyPair(roleKey, derivationPath.getIndex().getValue(), derivationPath.getIndex().isHarden());
    }

    /**
     * Get an HdKeyPair from the given root key using the master derivation path.
     *
     * @param rootKey the root key represented as a byte array
     * @return the HdKeyPair derived from the provided root key and the master derivation path
     */
    public HdKeyPair getRootKeyPairFromRootKey(byte[] rootKey) {
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        return hdKeyGenerator.getKeyPairFromSecretKey(rootKey, HdKeyGenerator.MASTER_PATH);
    }

}
