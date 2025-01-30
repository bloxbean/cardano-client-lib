package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.crypto.MnemonicUtil;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.governance.keys.CommitteeColdKey;
import com.bloxbean.cardano.client.governance.keys.CommitteeHotKey;
import com.bloxbean.cardano.client.governance.keys.DRepKey;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Optional;

/**
 * Create and manage secrets, and perform account-based work such as signing transactions.
 */
public class Account {
    @JsonIgnore
    private String mnemonic;
    @JsonIgnore
    private byte[] accountKey; //Pvt key at account level m/1852'/1815'/x
    @JsonIgnore
    private byte[] rootKey; //Pvt key at root level m/

    private String baseAddress;
    private String changeAddress;
    private String enterpriseAddress;
    private String stakeAddress;
    private String drepId;
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
        this.mnemonic = MnemonicUtil.generateNew(noOfWords);
        baseAddress();
    }

    /**
     * Create a mainnet account from a mnemonic
     *
     * @param mnemonic
     * @deprecated Use factory method `createFromMnemonic` to create an Account from mnemonic
     */
    @Deprecated(since = "0.7.0-beta2")
    public Account(String mnemonic) {
        this(Networks.mainnet(), mnemonic, 0);
    }

    /**
     * Create a mainnet account from a mnemonic at index
     *
     * @param mnemonic
     * @deprecated Use factory method `createFromMnemonic` to create an Account from mnemonic
     */
    @Deprecated(since = "0.7.0-beta2")
    public Account(String mnemonic, int index) {
        this(Networks.mainnet(), mnemonic, index);
    }

    /**
     * Create a account for the network from a mnemonic
     *
     * @param network
     * @param mnemonic
     * @deprecated Use factory method `createFromMnemonic` to create an Account from mnemonic
     */
    @Deprecated(since = "0.7.0-beta2")
    public Account(Network network, String mnemonic) {
        this(network, mnemonic, 0);
    }

    /**
     * Create an account for the network, mnemonic at given index
     *
     * @param network
     * @param mnemonic
     * @param index
     * @deprecated Use factory method `createFromMnemonic` to create an Account from mnemonic
     */
    @Deprecated(since = "0.7.0-beta2")
    public Account(Network network, String mnemonic, int index) {
        this(network, mnemonic, DerivationPath.createExternalAddressDerivationPath(index));
    }

    /**
     * Crate an account for the network from mnemonic at index
     *
     * @param network
     * @param mnemonic
     * @param derivationPath
     * @deprecated Use factory method `createFromMnemonic` to create an Account from mnemonic
     */
    @Deprecated(since = "0.7.0-beta2")
    public Account(Network network, String mnemonic, DerivationPath derivationPath) {
        this.network = network;
        this.mnemonic = mnemonic;
        this.accountKey = null;
        this.derivationPath = derivationPath;
        MnemonicUtil.validateMnemonic(this.mnemonic);
        baseAddress();
    }

    /**
     * Create an account from a private key for a specified network for account = 0, index = 0
     *
     * @param network
     * @param accountKey accountKey is a private key of 96 bytes (with priv key and chaincode) at account level
     * @deprecated Use factory method `createFromAccountKey` to create an Account from account level key
     */
    @Deprecated(since = "0.7.0-beta2")
    public Account(Network network, byte[] accountKey) {
        this(network, accountKey, 0, 0);
    }

    /**
     * Create an account from a private key for a specified network
     *
     * @param network
     * @param accountKey is a private key of 96 bytes (with priv key and chaincode) at account level
     * @param account account
     * @param index address index
     * @deprecated Use factory method `createFromAccountKey` to create an Account from account level key
     */
    @Deprecated(since = "0.7.0-beta2")
    public Account(Network network, byte[] accountKey, int account, int index) {
        this.network = network;
        this.mnemonic = null;
        if (accountKey.length == 96)
            this.accountKey = accountKey;
        else
            throw new AccountException("Invalid length (Account Private Key): " + accountKey.length);

        this.derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
        this.derivationPath.getIndex().setValue(index);

        baseAddress();
    }

    private Account(Network network, String mnemonic, byte[] rootKey, byte[] accountKey, DerivationPath derivationPath) {
        this.network = network;
        this.derivationPath = derivationPath;

        if (mnemonic != null && !mnemonic.isEmpty()) {
            this.mnemonic = mnemonic;
            this.accountKey = null;
            MnemonicUtil.validateMnemonic(this.mnemonic);
        } else if (rootKey != null && rootKey.length > 0) {
            this.mnemonic = null;
            this.accountKey = null;

            if (rootKey.length == 96)
                this.rootKey = rootKey;
            else
                throw new AccountException("Invalid length (Root Pvt Key): " + rootKey.length);
        } else if (accountKey != null && accountKey.length > 0) {
            this.mnemonic = null;
            if (accountKey.length == 96)
                this.accountKey = accountKey;
            else
                throw new AccountException("Invalid length (Account Private Key): " + accountKey.length);
        }

        baseAddress();
    }

    /**
     * Creates an Account object from a given mnemonic phrase at m/1852'/1815'/0/0/0
     *
     * @param network the network for the account. Possible values: Networks.mainnet() or Networks.testnet()
     * @param mnemonic the mnemonic phrase used to generate the account
     * @return an Account object generated from the mnemonic phrase
     */
    public static Account createFromMnemonic(Network network, String mnemonic) {
        return createFromMnemonic(network, mnemonic, 0, 0);
    }

    /**
     * Creates an Account instance from the given mnemonic, network at derivation path m/1852'/1815'/account/0/index
     *
     * @param network the network for the account. Possible values: Networks.mainnet() or Networks.testnet()
     * @param mnemonic the mnemonic phrase used for generating the account.
     * @param account the account number in the derivation path.
     * @param index the index for the address in the derivation path.
     * @return an Account object generated from the mnemonic phrase
     */
    public static Account createFromMnemonic(Network network, String mnemonic, int account, int index) {
        var derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
        derivationPath.getIndex().setValue(index);

        return createFromMnemonic(network, mnemonic, derivationPath);
    }

    /**
     * Creates an Account instance from the provided mnemonic at given derivation path
     *
     * @param network the network for the account. Possible values: Networks.mainnet() or Networks.testnet()
     * @param mnemonic the mnemonic phrase used to generate the account
     * @param derivationPath the derivation path used for key generation
     * @return an Account object generated from the mnemonic phrase
     */
    public static Account createFromMnemonic(Network network, String mnemonic, DerivationPath derivationPath) {
        return new Account(network, mnemonic, null, null, derivationPath);
    }

    /**
     * Creates an Account instance from a root key at derivation path: m/1852'/1815'/0/0/0
     *
     * @param network The network for the account. Possible values: Networks.mainnet() or Networks.testnet()
     * @param rootKey the root key used to derive the account
     * @return a new Account object derived from the provided root key
     */
    public static Account createFromRootKey(Network network, byte[] rootKey) {
        return createFromRootKey(network, rootKey, 0, 0);
    }

    /**
     * Creates an Account instance using the provided network, rootKey, account number, and index
     * at derivation path m/1852'/1815'/account/0/index
     *
     * @param network the network for the account. Possible values: Networks.mainnet() or Networks.testnet()
     * @param rootKey the root key used to derive the account. Possible values: Networks.mainnet() or Networks.testnet()
     * @param account the account number used in the derivation path.
     * @param index the index used in the derivation path.
     * @return A new Account object derived from the specified root key
     */
    public static Account createFromRootKey(Network network, byte[] rootKey, int account, int index) {
        var derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
        derivationPath.getIndex().setValue(index);

        return createFromRootKey(network, rootKey, derivationPath);
    }

    /**
     * Creates an Account instance from the provided root key and derivation path.
     *
     * @param network the network for the account. Possible values: Networks.mainnet() or Networks.testnet()
     * @param rootKey the root key used to derive the account.
     * @param derivationPath the derivation path used for key generation.
     * @return A new Account object derived from the provided root key
     */
    public static Account createFromRootKey(Network network, byte[] rootKey, DerivationPath derivationPath) {
        return new Account(network, null, rootKey, null, derivationPath);
    }

    /**
     * Creates an Account instance using the provided account level key and at address index = 0
     *
     * @param network the network for the account. Possible values: Networks.mainnet() or Networks.testnet()
     * @param accountKey the account key used to derive the account
     * @return A new Account object derived from the provided account level key
     */
    public static Account createFromAccountKey(Network network, byte[] accountKey) {
        return createFromAccountKey(network, accountKey, 0, 0);
    }

    /**
     * Creates an Account instance from the given account key, network, account number, and index. (m/1852'/1815'/account/0/index)
     *
     * @param network the network for the account. Possible values: Networks.mainnet() or Networks.testnet()
     * @param accountKey the account key byte array
     * @param account the account number
     * @param index the index value for the account derivation
     * @return an Account object created using account key
     */
    public static Account createFromAccountKey(Network network, byte[] accountKey, int account, int index) {
        var derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
        derivationPath.getIndex().setValue(index);

        return createFromAccountKey(network, accountKey, derivationPath);
    }

    /**
     * Creates an Account instance from the given account key, network, derivation path
     *
     * @param network the network for the account. Possible values: Networks.mainnet() or Networks.testnet()
     * @param accountKey the account key byte array
     * @param derivationPath
     * @return an Account object created using account key
     */
    public static Account createFromAccountKey(Network network, byte[] accountKey, DerivationPath derivationPath) {
        return new Account(network, null, null, accountKey, derivationPath);
    }

    /**
     * @return string a 24 word mnemonic or null if the Account is derived from root key or account key
     */
    public String mnemonic() {
        return mnemonic;
    }

    /**
     * @return baseAddress at index
     */
    public String baseAddress() {
        if (baseAddress == null || baseAddress.isEmpty()) {
            HdKeyPair paymentKeyPair = getHdKeyPair();
            HdKeyPair stakeKeyPair = getStakeKeyPair();

            Address address = AddressProvider.getBaseAddress(paymentKeyPair.getPublicKey(), stakeKeyPair.getPublicKey(), network);
            baseAddress = address.toBech32();
        }

        return baseAddress;
    }

    /**
     * @return baseAddress at index
     */
    public String baseAddressAsBase16() {
        if (baseAddress == null || baseAddress.isEmpty()) {
            baseAddress();
        }

        Address address = new Address(baseAddress, derivationPath);
        return HexUtil.encodeHexString(address.getBytes());
    }

    /**
     * @return changeAddress at index = 0
     */
    public String changeAddress() {
        if (changeAddress == null || changeAddress.isEmpty()) {
            HdKeyPair changeKeyPair = getChangeKeyPair();
            HdKeyPair stakeKeyPair = getStakeKeyPair();

            Address address = AddressProvider.getBaseAddress(changeKeyPair.getPublicKey(), stakeKeyPair.getPublicKey(), network);
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
            Address address = AddressProvider.getEntAddress(paymentKeyPair.getPublicKey(), network);
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
            Address address = AddressProvider.getRewardAddress(stakeKeyPair.getPublicKey(), network);
            stakeAddress = address.toBech32();
        }

        return stakeAddress;
    }

    /**
     * @return baseAddress at index
     */
    @JsonIgnore
    public Address getBaseAddress() {
        if (baseAddress == null || baseAddress.isEmpty()) {
            baseAddress();
        }

        Address address = new Address(baseAddress, derivationPath);
        return address;
    }

    /**
     * @return enterpriseAddress at index
     */
    @JsonIgnore
    public Address getEnterpriseAddress() {
        if (enterpriseAddress == null || enterpriseAddress.isEmpty()) {
            enterpriseAddress();
        }

        Address address = new Address(enterpriseAddress, derivationPath);
        return address;
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
     * Get Hd key pair for stake address
     * @return
     */
    @JsonIgnore
    public HdKeyPair stakeHdKeyPair() {
        return getStakeKeyPair();
    }

    /**
     * Get Hd key pair for change address
     * @return
     */
    @JsonIgnore
    public HdKeyPair changeHdKeyPair() {
        return getChangeKeyPair();
    }

    /**
     * Get Hd key pair for DRep keys
     * @return
     */
    @JsonIgnore
    public HdKeyPair drepHdKeyPair() {
        return getDRepKeyPair();
    }

    /**
     * CIP-129 compatible drep id
     * @return bech32 encoded drep id (CIP-129)
     */
    public String drepId() {
        if (drepId == null || drepId.isEmpty()) {
            drepId = DRepKey.from(drepHdKeyPair()).dRepId();
        }

        return drepId;
    }

    /**
     * Generates and returns the legacy drep id (CIP-105 Deprecated version).
     * The identifier is derived from the DRep HD key pair.
     *
     * @return the legacy DRep id (CIP 105 Deprecated version) in string format.
     */
    public String legacyDRepId() {
        return DRepKey.from(drepHdKeyPair()).legacyDRepId();
    }

    /**
     * Get {@link DRepKey} from DRep Hd key pair {@link HdKeyPair}
     * @return DRepKey
     */
    public DRepKey drepKey() {
        return DRepKey.from(drepHdKeyPair());
    }

    public Credential drepCredential() {
        var drepHdKeyPair = drepHdKeyPair();
        return Credential.fromKey(drepHdKeyPair.getPublicKey().getKeyHash());
    }

    /**
     * Get Hd key pair for Constitutional Committee Cold Keys
     * @return HdKeyPair
     */
    @JsonIgnore
    public HdKeyPair committeeColdKeyPair() {
        return getCommitteeColdKeyPair();
    }

    /**
     * Get {@link CommitteeColdKey} from Constitutional Committee Cold Key Hd key pair
     * @return CommitteeColdKey
     */
    public CommitteeColdKey committeeColdKey() {
        return CommitteeColdKey.from(committeeColdKeyPair());
    }

    /**
     * Get Credential for Constitutional Committee Cold Keys
     * @return Credential
     */
    public Credential committeeColdCredential() {
        var ccColdHdKeyPair = committeeColdKeyPair();
        return Credential.fromKey(ccColdHdKeyPair.getPublicKey().getKeyHash());
    }

    /**
     * Get Hd key pair for Constitutional Committee Hot Keys
     * @return
     */
    @JsonIgnore
    public HdKeyPair committeeHotKeyPair() {
        return getCommitteeHotKeyPair();
    }

    /**
     * Get {@link CommitteeHotKey} from Constitutional Committee Hot Key Hd key pair
     * @return CommitteeHotKey
     */
    public CommitteeHotKey committeeHotKey() {
        return CommitteeHotKey.from(committeeHotKeyPair());
    }

    /**
     * Get Credential for Constitutional Committee Hot Keys
     * @return Credential
     */
    public Credential committeeHotCredential() {
        var ccHotHdKeyPair = committeeHotKeyPair();
        return Credential.fromKey(ccHotHdKeyPair.getPublicKey().getKeyHash());
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
        return TransactionSigner.INSTANCE.sign(transaction, getHdKeyPair());
    }

    /**
     * Sign a transaction object with stake key
     * @param transaction
     * @return Signed Transaction
     */
    public Transaction signWithStakeKey(Transaction transaction) {
        return TransactionSigner.INSTANCE.sign(transaction, getStakeKeyPair());
    }

    /**
     * Sign a transaction object with DRep key
     * @param transaction Transaction
     * @return Signed Transaction
     */
    public Transaction signWithDRepKey(Transaction transaction) {
        return TransactionSigner.INSTANCE.sign(transaction, getDRepKeyPair());
    }

    /**
     * Sign a transaction object with Constitutional Committee Cold Key
     * @param transaction Transaction
     * @return Signed Transaction
     */
    public Transaction signWithCommitteeColdKey(Transaction transaction) {
        return TransactionSigner.INSTANCE.sign(transaction, getCommitteeColdKeyPair());
    }

    /**
     * Sign a transaction object with Constitutional Committee Hot Key
     * @param transaction Transaction
     * @return Signed Transaction
     */
    public Transaction signWithCommitteeHotKey(Transaction transaction) {
        return TransactionSigner.INSTANCE.sign(transaction, getCommitteeHotKeyPair());
    }

    public Optional<HdKeyPair> getRootKeyPair() {
        if (mnemonic != null && !mnemonic.isEmpty()) {
            return Optional.of(new CIP1852().getRootKeyPairFromMnemonic(mnemonic));
        } else if (rootKey != null && rootKey.length > 0) {
            return Optional.of(new CIP1852().getRootKeyPairFromRootKey(rootKey));
        } else
            return Optional.empty();
    }

    private HdKeyPair getHdKeyPair() {
        return getHdKeyPairFromDerivationPath(derivationPath);
    }

    private HdKeyPair getChangeKeyPair() {
        DerivationPath internalDerivationPath = DerivationPath.createInternalAddressDerivationPathForAccount(derivationPath.getAccount().getValue());
        return getHdKeyPairFromDerivationPath(internalDerivationPath);
    }

    private HdKeyPair getStakeKeyPair() {
        DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPathForAccount(derivationPath.getAccount().getValue());
        return getHdKeyPairFromDerivationPath(stakeDerivationPath);
    }

    private HdKeyPair getDRepKeyPair() {
        DerivationPath drepDerivationPath = DerivationPath.createDRepKeyDerivationPathForAccount(derivationPath.getAccount().getValue());
        return getHdKeyPairFromDerivationPath(drepDerivationPath);
    }

    private HdKeyPair getCommitteeColdKeyPair() {
        DerivationPath ccColdDerivationPath =
                DerivationPath.createCommitteeColdKeyDerivationPathForAccount(derivationPath.getAccount().getValue());

        return getHdKeyPairFromDerivationPath(ccColdDerivationPath);
    }

    private HdKeyPair getCommitteeHotKeyPair() {
        DerivationPath ccHotDerivationPath =
                DerivationPath.createCommitteeHotKeyDerivationPathForAccount(derivationPath.getAccount().getValue());

        return getHdKeyPairFromDerivationPath(ccHotDerivationPath);
    }

    private HdKeyPair getHdKeyPairFromDerivationPath(DerivationPath derivationPath) {
        HdKeyPair hdKeyPair;
        if (mnemonic != null && !mnemonic.isEmpty()) {
            hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, derivationPath);
        } else if (accountKey != null && accountKey.length > 0) {
            hdKeyPair = new CIP1852().getKeyPairFromAccountKey(this.accountKey, derivationPath);
        } else if (rootKey != null && rootKey.length > 0) {
            hdKeyPair = new CIP1852().getKeyPairFromRootKey(this.rootKey, derivationPath);
        } else {
            throw new AccountException("HDKeyPair derivation failed. Only one of mnemonic, rootKey, or accountKey should be set.");
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
