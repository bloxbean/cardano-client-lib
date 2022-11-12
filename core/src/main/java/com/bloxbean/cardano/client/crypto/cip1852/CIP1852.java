package com.bloxbean.cardano.client.crypto.cip1852;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyGenerator;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;

public class CIP1852 {

    public HdKeyPair getKeyPairFromMnemonic(String mnemonicPhrase, DerivationPath derivationPath) {
        try {
            byte[] entropy = MnemonicCode.INSTANCE.toEntropy(mnemonicPhrase);

            return getKeyPairFromEntropy(entropy, derivationPath);
        } catch (Exception ex) {
            throw new CryptoException("Mnemonic to KeyPair generation failed", ex);
        }
    }

    public HdKeyPair getKeyPairFromEntropy(byte[] entropy, DerivationPath derivationPath) {
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeyPair = hdKeyGenerator.getRootKeyPairFromEntropy(entropy);

        HdKeyPair purposeKey = hdKeyGenerator.getChildKeyPair(rootKeyPair, derivationPath.getPurpose().getValue(), derivationPath.getPurpose().isHarden());
        HdKeyPair coinTypeKey = hdKeyGenerator.getChildKeyPair(purposeKey, derivationPath.getCoinType().getValue(), derivationPath.getCoinType().isHarden());
        HdKeyPair accountKey = hdKeyGenerator.getChildKeyPair(coinTypeKey, derivationPath.getAccount().getValue(), derivationPath.getAccount().isHarden());
        HdKeyPair roleKey = hdKeyGenerator.getChildKeyPair(accountKey, derivationPath.getRole().getValue(), derivationPath.getRole().isHarden());

        HdKeyPair indexKey = hdKeyGenerator.getChildKeyPair(roleKey, derivationPath.getIndex().getValue(), derivationPath.getIndex().isHarden());
        return indexKey;
    }

    public HdKeyPair getKeyPairFromAccountKey(byte[] accountKey, DerivationPath derivationPath) {
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();

        HdKeyPair accountKeyPair = hdKeyGenerator.getAccountKeyPairFromSecretKey(accountKey,  derivationPath);
        HdKeyPair roleKey = hdKeyGenerator.getChildKeyPair(accountKeyPair, derivationPath.getRole().getValue(), derivationPath.getRole().isHarden());

        HdKeyPair indexKey = hdKeyGenerator.getChildKeyPair(roleKey, derivationPath.getIndex().getValue(), derivationPath.getIndex().isHarden());
        return indexKey;
    }

}
