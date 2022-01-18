package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.address.*;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicException;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Create and manage secrets, and perform account-based work such as signing transactions.
 */
public class Account {
    @JsonIgnore
    private String mnemonic;
    @JsonIgnore
    private String secretKey;
    private String baseAddress;
    private String changeAddress;
    private String enterpriseAddress;
    private String stakeAddress;
    private Network network;

    @JsonIgnore
    private DerivationPath derivationPath;

    /**
     * Create a new random mainnet account.
     */
    public Account() {
        this(Networks.mainnet(), 0);
    }

    /**
     * Create a new random mainnet account at index
     *
     * @param index
     */
    public Account(int index) {
        this(Networks.mainnet(), index);
    }

    /**
     * Create a new random account for the network
     *
     * @param network
     */
    public Account(Network network) {
        this(network, 0);
    }

    /**
     * Create a new random account for the network at index
     *
     * @param network
     * @param index
     */
    public Account(Network network, int index) {
        this(network, DerivationPath.createExternalAddressDerivationPath(index), Words.TWENTY_FOUR);
    }

    /**
     * Create a new random account for the network and derivation path
     *
     * @param network
     * @param derivationPath
     */
    public Account(Network network, DerivationPath derivationPath, Words noOfWords) {
        this.network = network;
        this.derivationPath = derivationPath;
        generateNew(noOfWords);
    }

    /**
     * Create a mainnet account from a mnemonic
     *
     * @param mnemonic
     */
    public Account(String mnemonic) {
        this(Networks.mainnet(), mnemonic, 0);
    }

    /**
     * Create a mainnet account from a mnemonic at index
     *
     * @param mnemonic
     */
    public Account(String mnemonic, int index) {
        this(Networks.mainnet(), mnemonic, index);
    }

    /**
     * Create a account for the network from a mnemonic
     *
     * @param network
     * @param mnemonic
     */
    public Account(Network network, String mnemonic) {
        this(network, mnemonic, 0);
    }

    /**
     * Create an account for the network, mnemonic at given index
     *
     * @param network
     * @param mnemonic
     * @param index
     */
    public Account(Network network, String mnemonic, int index) {
        this(network, mnemonic, DerivationPath.createExternalAddressDerivationPath(index));
    }

    /**
     * Crate an account for the network from mnemonic at index
     *
     * @param network
     * @param mnemonic
     * @param derivationPath
     */
    public Account(Network network, String mnemonic, DerivationPath derivationPath) {
        this.network = network;
        this.mnemonic = mnemonic;
        this.secretKey = null;
        this.derivationPath = derivationPath;
        validateMnemonic();
        baseAddress();
    }

    /**
     * Create an account for the network and derivation path from a private key
     *
     * @param network
     * @param derivationPath
     * @param secretKey is a private key of 192 bytes or 256 bytes (with pubkey and chaincode) encoded in a base64 String
     */
    public Account(Network network, DerivationPath derivationPath, String secretKey) {
        this.network = network;
        this.mnemonic = null;
        if (secretKey.length() == 192)
            this.secretKey = secretKey;
        else
            this.secretKey = secretKey.substring(0,128) + secretKey.substring(192,256);
        this.derivationPath = derivationPath;
        baseAddress();
    }

    /**
     * @return string a 24 word mnemonic
     */
    public String mnemonic() {
        return mnemonic;
    }

    /**
     * @return baseAddress at index
     */
    public String baseAddress() {
        if (baseAddress == null || baseAddress.isEmpty()) {
            HdKeyPair paymentKeyPair = getExternalKeyPair();
            HdKeyPair stakeKeyPair = getStakeKeyPair();

            Address address = AddressService.getInstance().getBaseAddress(paymentKeyPair.getPublicKey(), stakeKeyPair.getPublicKey(), network);
            baseAddress = address.toBech32();
        }

        return baseAddress;
    }

    /**
     * @return changeAddress at index
     */
    public String changeAddress() {
        if (changeAddress == null || changeAddress.isEmpty()) {
            HdKeyPair changeKeyPair = getChangeKeyPair();
            HdKeyPair stakeKeyPair = getStakeKeyPair();

            Address address = AddressService.getInstance().getBaseAddress(changeKeyPair.getPublicKey(), stakeKeyPair.getPublicKey(), network);
            changeAddress = address.toBech32();
        }

        return changeAddress;
    }

    /**
     * @return enterpriseAddress at index
     */
    public String enterpriseAddress() {
        if (enterpriseAddress == null || enterpriseAddress.isEmpty()) {
            HdKeyPair paymentKeyPair = getHdKeyPair();
            Address address = AddressService.getInstance().getEntAddress(paymentKeyPair.getPublicKey(), network);
            enterpriseAddress = address.toBech32();
        }

        return enterpriseAddress;
    }

    /**
     * @return Reward (stake) address
     */
    public String stakeAddress() {
        if (stakeAddress == null || stakeAddress.isEmpty()) {
            HdKeyPair stakeKeyPair = getStakeKeyPair();
            Address address = AddressService.getInstance().getRewardAddress(stakeKeyPair.getPublicKey(), network);
            stakeAddress = address.toBech32();
        }

        return stakeAddress;
    }

    /**
     * Get private key in bech32 format
     * @return
     */
    @JsonIgnore
    public String getBech32PrivateKey() {
        HdKeyPair hdKeyPair = getHdKeyPair();
        return hdKeyPair.getPrivateKey().toBech32();
    }

    /**
     * Get private key bytes
     * @return
     */
    @JsonIgnore
    public byte[] privateKeyBytes() {
        HdKeyPair hdKeyPair = getHdKeyPair();
        return hdKeyPair.getPrivateKey().getKeyData();
    }

    /**
     * Get public key bytes
     * @return
     */
    @JsonIgnore
    public byte[] publicKeyBytes() {
        return getHdKeyPair().getPublicKey().getKeyData();
    }

    /**
     * Get Hd key pair
     * @return
     */
    @JsonIgnore
    public HdKeyPair hdKeyPair() {
        return getHdKeyPair();
    }

    /**
     * @deprecated Use {@link Account#sign(Transaction)}
     * @param txnHex
     * @return
     * @throws CborSerializationException
     */
    @Deprecated
    public String sign(String txnHex) throws CborSerializationException {
        if (txnHex == null || txnHex.length() == 0)
            throw new CborSerializationException("Invalid transaction hash");

        try {
            Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(txnHex));
            transaction = sign(transaction);
            return transaction.serializeToHex();
        } catch (CborDeserializationException e) {
            throw new CborSerializationException("Error in Cbor deserialization", e);
        }
    }

    /**
     * Sign a transaction object with this account
     * @param transaction
     * @return
     */
    public Transaction sign(Transaction transaction) {
        return TransactionSigner.INSTANCE.sign(transaction, getExternalKeyPair());
    }

    private void generateNew(Words noOfWords) {
        String mnemonic = null;
        try {
            mnemonic = MnemonicCode.INSTANCE.createMnemonic(noOfWords).stream().collect(Collectors.joining(" "));
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException("Mnemonic generation failed", e);
        }
        this.mnemonic = mnemonic;
        baseAddress();
    }

    private void validateMnemonic() {
        if (mnemonic == null) {
            throw new AddressRuntimeException("Mnemonic cannot be null");
        }

        mnemonic = mnemonic.replaceAll("\\s+", " ");
        String[] words = mnemonic.split("\\s+");

        try {
            MnemonicCode.INSTANCE.check(Arrays.asList(words));
        } catch (MnemonicException e) {
            throw new AddressRuntimeException("Invalid mnemonic phrase", e);
        }
    }

    private HdKeyPair getHdKeyPair() {
        HdKeyPair hdKeyPair;
        if (mnemonic == null || mnemonic.trim().length() == 0) {
            hdKeyPair = new CIP1852().getKeyPairFromAccountKey(this.secretKey, derivationPath);
        }
        else {
            hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, derivationPath);
        }
        return hdKeyPair;
    }

    public HdKeyPair getExternalKeyPair() {
        HdKeyPair hdKeyPair;
        DerivationPath externalDerivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(derivationPath.getAccount().getValue());
        if (mnemonic == null || mnemonic.trim().length() == 0) {
            hdKeyPair = new CIP1852().getKeyPairFromAccountKey(this.secretKey, externalDerivationPath);
        }
        else {
            hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, externalDerivationPath);
        }

        return hdKeyPair;
    }

    private HdKeyPair getChangeKeyPair() {
        HdKeyPair hdKeyPair;
        DerivationPath internalDerivationPath = DerivationPath.createInternalAddressDerivationPathForAccount(derivationPath.getAccount().getValue());
        if (mnemonic == null || mnemonic.trim().length() == 0) {
            hdKeyPair = new CIP1852().getKeyPairFromAccountKey(this.secretKey, internalDerivationPath);
        }
        else {
            hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, internalDerivationPath);
        }

        return hdKeyPair;
    }

    private HdKeyPair getStakeKeyPair() {
        HdKeyPair hdKeyPair;
        DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPathForAccount(derivationPath.getAccount().getValue());
        if (mnemonic == null || mnemonic.trim().length() == 0) {
            hdKeyPair = new CIP1852().getKeyPairFromAccountKey(this.secretKey, stakeDerivationPath);
        } else {
            hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);
        }

        return hdKeyPair;
    }

    @Override
    public String toString() {
        try {
            return baseAddress();
        } catch (Exception e) {
            return null;
        }
    }
}
