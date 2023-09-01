package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.spec.Script;
import com.google.common.primitives.Bytes;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.bloxbean.cardano.client.address.util.AddressEncoderDecoderUtil.*;
import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash224;

/**
 * Utility class to generate various types of Shelley addresses.
 */
@Slf4j
public class AddressProvider {

    private static final byte BASE_PAYMENT_KEY_STAKE_KEY_HEADER_TYPE = 0b0000_0000;
    private static final byte BASE_PAYMENT_SCRIPT_STAKE_KEY_HEADER_TYPE = 0b0001_0000;
    private static final byte BASE_PAYMENT_KEY_STAKE_SCRIPT_HEADER_TYPE = 0b0010_0000;
    private static final byte BASE_PAYMENT_SCRIPT_STAKE_SCRIPT_HEADER_TYPE = 0b0011_0000;
    private static final byte PTR_PAYMENT_KEY_STAKE_PTR_HEADER_TYPE = 0b0100_0000;
    private static final byte PTR_PAYMENT_SCRIPT_STAKE_PTR_HEADER_TYPE = 0b0101_0000;
    private static final byte ENT_PAYMENT_KEY_HEADER_TYPE = 0b0110_0000;
    private static final byte ENT_PAYMENT_SCRIPT_HEADER_TYPE = 0b0111_0000;
    private static final byte RWD_STAKE_KEY_HEDER_TYPE = (byte)0b1110_0000;
    private static final byte RWD_STAKE_SCRIPT_HEADER_TYPE = (byte)0b1111_0000;

    /**
     * Returns base address from payment key and delegation key
     * @param paymentKey HdPublicKey
     * @param delegationKey HdPublicKey
     * @param networkInfo Network
     * @return Base address
     * @throws AddressRuntimeException
     */
    //header: 0000....
    public static Address getBaseAddress(HdPublicKey paymentKey, HdPublicKey delegationKey, Network networkInfo) {
        if (paymentKey == null || delegationKey == null)
            throw new AddressRuntimeException("paymentkey and delegationKey cannot be null");

        byte[] paymentKeyHash = paymentKey.getKeyHash();
        byte[] delegationKeyHash = delegationKey.getKeyHash();

        return getAddress(paymentKeyHash, delegationKeyHash, BASE_PAYMENT_KEY_STAKE_KEY_HEADER_TYPE, networkInfo, AddressType.Base);
    }

    /**
     * Returns base address from script in payment part and delegation key
     * @param paymentScript Script
     * @param delegationKey HdPublicKey
     * @param networkInfo   Network
     * @return Base address
     * @throws AddressRuntimeException
     */
    //header: 0001....
    public static Address getBaseAddress(Script paymentScript, HdPublicKey delegationKey, Network networkInfo) {
        if (paymentScript == null || delegationKey == null)
            throw new AddressRuntimeException("paymentScript and delegationKey cannot be null");

        byte[] paymentScriptHash;
        try {
            paymentScriptHash = paymentScript.getScriptHash();
        } catch (CborSerializationException e) {
            throw new AddressRuntimeException("Unable to get script hash from payment script", e);
        }
        byte[] delegationKeyHash = delegationKey.getKeyHash();

        byte headerType = BASE_PAYMENT_SCRIPT_STAKE_KEY_HEADER_TYPE;

        return getAddress(paymentScriptHash, delegationKeyHash, headerType, networkInfo, AddressType.Base);
    }

    /**
     * Returns base address from payment key and delegation script
     * @param paymentKey payment key
     * @param delegationScript delegation script
     * @param networkInfo Network
     * @return Base address
     * @throws AddressRuntimeException
     */
    //header: 0010....
    public static Address getBaseAddress(HdPublicKey paymentKey, Script delegationScript, Network networkInfo) {
        if (paymentKey == null || delegationScript == null)
            throw new AddressRuntimeException("paymentkey and delegationScript cannot be null");

        byte[] paymentKeyHash = paymentKey.getKeyHash();
        byte[] delegationScriptHash;
        try {
            delegationScriptHash = delegationScript.getScriptHash();
        } catch (CborSerializationException e) {
            throw new AddressRuntimeException("Unable to get script hash from delegation script", e);
        }

        byte headerType = BASE_PAYMENT_KEY_STAKE_SCRIPT_HEADER_TYPE;

        return getAddress(paymentKeyHash, delegationScriptHash, headerType, networkInfo, AddressType.Base);
    }

    /**
     * Returns base address from payment script and delegation script
     * @param paymentScript payment script
     * @param delegationScript delegation script
     * @param networkInfo Network
     * @return Base address
     * @throws AddressRuntimeException
     */
    //header: 0011....
    public static Address getBaseAddress(Script paymentScript, Script delegationScript, Network networkInfo) {
        if (paymentScript == null || delegationScript == null)
            throw new AddressRuntimeException("paymentScript and delegationScript cannot be null");

        byte[] paymentScriptHash;
        byte[] delegationScriptHash;
        try {
            paymentScriptHash = paymentScript.getScriptHash();
            delegationScriptHash = delegationScript.getScriptHash();
        } catch (CborSerializationException e) {
            throw new RuntimeException(e);
        }

        byte headerType = BASE_PAYMENT_SCRIPT_STAKE_SCRIPT_HEADER_TYPE;

        return getAddress(paymentScriptHash, delegationScriptHash, headerType, networkInfo, AddressType.Base);
    }

    /**
     * Returns base address from payment credential and delegation credential.
     * Payment credential can be either verification key hash or script hash.
     * Delegation credential can be either verification key hash or script hash.
     * @param paymentCredential payment credential
     * @param delegationCredential delegation credential
     * @param networkInfo network
     * @return Base address
     * @throws AddressRuntimeException
     */
    public static Address getBaseAddress(Credential paymentCredential, Credential delegationCredential, Network networkInfo) {
        if (paymentCredential == null || delegationCredential == null)
            throw new AddressRuntimeException("paymentCredential and delegationCredential cannot be null");

        if (paymentCredential.getType() == CredentialType.Key
                && delegationCredential.getType() == CredentialType.Key) {
            return getAddress(paymentCredential.getBytes(), delegationCredential.getBytes(),
                    BASE_PAYMENT_KEY_STAKE_KEY_HEADER_TYPE, networkInfo, AddressType.Base);
        } else if (paymentCredential.getType() == CredentialType.Script
                && delegationCredential.getType() == CredentialType.Key) {
            return getAddress(paymentCredential.getBytes(), delegationCredential.getBytes(),
                    BASE_PAYMENT_SCRIPT_STAKE_KEY_HEADER_TYPE, networkInfo, AddressType.Base);
        } else if (paymentCredential.getType() == CredentialType.Key
                && delegationCredential.getType() == CredentialType.Script) {
            return getAddress(paymentCredential.getBytes(), delegationCredential.getBytes(),
                    BASE_PAYMENT_KEY_STAKE_SCRIPT_HEADER_TYPE, networkInfo, AddressType.Base);
        } else if (paymentCredential.getType() == CredentialType.Script
                && delegationCredential.getType() == CredentialType.Script) {
            return getAddress(paymentCredential.getBytes(), delegationCredential.getBytes(),
                    BASE_PAYMENT_SCRIPT_STAKE_SCRIPT_HEADER_TYPE, networkInfo, AddressType.Base);
        } else
            throw new AddressRuntimeException("Invalid credential type, should be either Key or Script. Payment Credential: "
                    + paymentCredential + ", Delegation Credential: " + delegationCredential);
    }

    /**
     * Returns pointer address from payment key and delegation pointer
     * @param paymentKey payment key
     * @param delegationPointer delegation pointer
     * @param networkInfo Network
     * @return Pointer address
     * @throws AddressRuntimeException
     */
    //header: 0100....
    public static Address getPointerAddress(HdPublicKey paymentKey, Pointer delegationPointer, Network networkInfo) {
        if (paymentKey == null || delegationPointer == null)
            throw new AddressRuntimeException("paymentkey and delegationKey cannot be null");

        byte[] paymentKeyHash = paymentKey.getKeyHash();
        byte[] delegationPointerHash = BytesUtil.merge(variableNatEncode(delegationPointer.slot),
                variableNatEncode(delegationPointer.txIndex), variableNatEncode(delegationPointer.certIndex));

        byte headerType = PTR_PAYMENT_KEY_STAKE_PTR_HEADER_TYPE;
        return getAddress(paymentKeyHash, delegationPointerHash, headerType, networkInfo, AddressType.Ptr);
    }

    /**
     * Returns pointer address from payment script and delegation pointer
     * @param paymentScript payment script
     * @param delegationPointer delegation pointer
     * @param networkInfo Network
     * @return Pointer address
     * @throws AddressRuntimeException
     */
    //header: 0101....
    public static Address getPointerAddress(Script paymentScript, Pointer delegationPointer, Network networkInfo) {
        if (paymentScript == null || delegationPointer == null)
            throw new AddressRuntimeException("paymentScript and delegationKey cannot be null");

        byte[] paymentScriptHash;
        try {
            paymentScriptHash = paymentScript.getScriptHash();
        } catch (CborSerializationException e) {
            throw new AddressRuntimeException("Unable to get script hash from payment script", e);
        }
        byte[] delegationPointerHash = BytesUtil.merge(variableNatEncode(delegationPointer.slot),
                variableNatEncode(delegationPointer.txIndex), variableNatEncode(delegationPointer.certIndex));

        byte headerType = PTR_PAYMENT_SCRIPT_STAKE_PTR_HEADER_TYPE;
        return getAddress(paymentScriptHash, delegationPointerHash, headerType, networkInfo, AddressType.Ptr);
    }

    /**
     * Returns pointer address from payment credential and delegation pointer.
     * Payment credential can be either verification key hash or script hash.
     * @param paymentCredential payment credential
     * @param delegationPointer delegation pointer
     * @param networkInfo network
     * @return Pointer address
     * @throws AddressRuntimeException
     */
    public static Address getPointerAddress(Credential paymentCredential, Pointer delegationPointer, Network networkInfo) {
        if (paymentCredential == null || delegationPointer == null)
            throw new AddressRuntimeException("paymentCredential and delegationPointer cannot be null");

        byte[] delegationPointerHash = BytesUtil.merge(variableNatEncode(delegationPointer.slot),
                variableNatEncode(delegationPointer.txIndex), variableNatEncode(delegationPointer.certIndex));

        switch (paymentCredential.getType()) {
            case Key:
                return getAddress(paymentCredential.getBytes(), delegationPointerHash,
                        PTR_PAYMENT_KEY_STAKE_PTR_HEADER_TYPE, networkInfo, AddressType.Ptr);
            case Script:
                return getAddress(paymentCredential.getBytes(), delegationPointerHash,
                        PTR_PAYMENT_SCRIPT_STAKE_PTR_HEADER_TYPE, networkInfo, AddressType.Ptr);
            default:
                throw new AddressRuntimeException("Invalid credential type, should be either Key or Script. Payment Credential: "
                        + paymentCredential + ", Delegation Pointer: " + delegationPointer);
        }
    }

    /**
     * Returns enterprise address from payment key
     * @param paymentKey payment key
     * @param networkInfo network
     * @return Enterprise address
     * @throws AddressRuntimeException
     */
    //header: 0110....
    public static Address getEntAddress(HdPublicKey paymentKey, Network networkInfo)  {
        if (paymentKey == null)
            throw new AddressRuntimeException("paymentkey cannot be null");

        byte[] paymentKeyHash = paymentKey.getKeyHash();

        byte headerType = ENT_PAYMENT_KEY_HEADER_TYPE;

        return getAddress(paymentKeyHash, null, headerType, networkInfo, AddressType.Enterprise);
    }

    /**
     * Returns enterprise address from payment script
     * @param paymentScript payment script
     * @param networkInfo network
     * @return Enterprise address
     * @throws AddressRuntimeException
     */
    //header: 0111....
    public static Address getEntAddress(Script paymentScript, Network networkInfo) {
        if (paymentScript == null)
            throw new AddressRuntimeException("paymentScript cannot be null");

        byte[] paymentScriptHash;
        try {
            paymentScriptHash = paymentScript.getScriptHash();
        } catch (CborSerializationException e) {
            throw new AddressRuntimeException("Unable to get script hash from payment script", e);
        }

        byte headerType = ENT_PAYMENT_SCRIPT_HEADER_TYPE;

        return getAddress(paymentScriptHash, null, headerType, networkInfo, AddressType.Enterprise);
    }

    /**
     * Returns enterprise address from payment credential.
     * Payment credential can be either verification key hash or script hash.
     * @param paymentCredential payment credential
     * @param networkInfo network
     * @return Enterprise address
     * @throws AddressRuntimeException
     */
    public static Address getEntAddress(@NonNull Credential paymentCredential, Network networkInfo) {
        switch (paymentCredential.getType()) {
            case Key:
                return getAddress(paymentCredential.getBytes(), null,
                        ENT_PAYMENT_KEY_HEADER_TYPE, networkInfo, AddressType.Enterprise);
            case Script:
                return getAddress(paymentCredential.getBytes(), null,
                        ENT_PAYMENT_SCRIPT_HEADER_TYPE, networkInfo, AddressType.Enterprise);
            default:
                throw new AddressRuntimeException("Invalid credential type, should be either Key or Script. Payment Credential: "
                        + paymentCredential);
        }
    }

    /**
     * Returns reward address from delegation key
     * @param delegationKey Delegation/Stake key
     * @param networkInfo network
     * @return Reward address
     * @throws AddressRuntimeException
     */
    //header: 1110....
    public static Address getRewardAddress(HdPublicKey delegationKey, Network networkInfo)  {
        if (delegationKey == null)
            throw new AddressRuntimeException("stakeKey cannot be null");

        byte[] stakeKeyHash = delegationKey.getKeyHash();

        byte headerType = RWD_STAKE_KEY_HEDER_TYPE;

        return getAddress(null, stakeKeyHash, headerType, networkInfo, AddressType.Reward);
    }

    /**
     * Returns reward address from delegation script
     * @param delegationScript Delegation/Stake script
     * @param networkInfo network
     * @return Reward address
     * @throws AddressRuntimeException
     */
    //header: 1111....
    public static Address getRewardAddress(Script delegationScript, Network networkInfo) {
        if (delegationScript == null)
            throw new AddressRuntimeException("delegationScript cannot be null");

        byte[] stakeScriptHash;
        try {
            stakeScriptHash = delegationScript.getScriptHash();
        } catch (CborSerializationException e) {
            throw new AddressRuntimeException("Unable to get script hash from delegation script", e);
        }

        byte headerType = RWD_STAKE_SCRIPT_HEADER_TYPE;

        return getAddress(null, stakeScriptHash, headerType, networkInfo, AddressType.Reward);
    }

    /**
     * Returns reward address from stake credential.
     * Stake credential can be either verification key hash or script hash.
     * @param stakeCredential Stake credential
     * @param networkInfo network
     * @return Reward address
     * @throws AddressRuntimeException
     */
    public static Address getRewardAddress(@NonNull Credential stakeCredential, Network networkInfo) {
        switch (stakeCredential.getType()) {
            case Key:
                return getAddress(null, stakeCredential.getBytes(),
                        RWD_STAKE_KEY_HEDER_TYPE, networkInfo, AddressType.Reward);
            case Script:
                return getAddress(null, stakeCredential.getBytes(),
                        RWD_STAKE_SCRIPT_HEADER_TYPE, networkInfo, AddressType.Reward);
            default:
                throw new AddressRuntimeException("Invalid credential type, should be either Key or Script. Stake Credential: "
                        + stakeCredential);
        }
    }

    public static Address getAddress(byte[] paymentKeyHash, byte[] stakeKeyHash, byte headerKind, Network networkInfo, AddressType addressType) {
        NetworkId network = getNetworkId(networkInfo);

        //get prefix
        String prefix = getPrefixHeader(addressType) + getPrefixTail(network);

        //get header
        byte header = getAddressHeader(headerKind, networkInfo, addressType);
        byte[] addressArray = getAddressBytes(paymentKeyHash, stakeKeyHash, addressType, header);

        return new Address(prefix, addressArray);
    }

    private static byte[] getAddressBytes(byte[] paymentKeyHash, byte[] stakeKeyHash, AddressType addressType, byte header) {
        //get body
        byte[] addressArray;
        switch (addressType) {
            case Base:
                addressArray = new byte[1 + paymentKeyHash.length + stakeKeyHash.length];
                addressArray[0] = header;
                System.arraycopy(paymentKeyHash, 0, addressArray, 1, paymentKeyHash.length);
                System.arraycopy(stakeKeyHash, 0, addressArray, paymentKeyHash.length + 1, stakeKeyHash.length);
                break;
            case Enterprise:
                addressArray = new byte[1 + paymentKeyHash.length];
                addressArray[0] = header;
                System.arraycopy(paymentKeyHash, 0, addressArray, 1, paymentKeyHash.length);
                break;
            case Reward:
                addressArray = new byte[1 + stakeKeyHash.length];
                addressArray[0] = header;
                System.arraycopy(stakeKeyHash, 0, addressArray, 1, stakeKeyHash.length);
                break;
            case Ptr:
                addressArray = new byte[1 + paymentKeyHash.length + stakeKeyHash.length];
                addressArray[0] = header;
                System.arraycopy(paymentKeyHash, 0, addressArray, 1, paymentKeyHash.length);
                System.arraycopy(stakeKeyHash, 0, addressArray, paymentKeyHash.length + 1, stakeKeyHash.length);
                break;
            default:
                throw new AddressRuntimeException("Unknown address type");
        }
        return addressArray;
    }

    /**
     * Verify the provided address with publicKey
     * Reconstruct the address from public key and then compare it with the provided address
     * @param address address
     * @param publicKey public key bytes
     * @return true or false
     */
    public static boolean verifyAddress(@NonNull Address address, byte[] publicKey) {
        String prefix = address.getPrefix();
        AddressType addressType = address.getAddressType();
        byte[] addressBytes = address.getBytes();
        byte header = addressBytes[0];

        byte[] newAddressBytes;
        Address newAddress;
        if (AddressType.Reward.equals(addressType)) {
            byte[] stakeKeyHash = blake2bHash224(publicKey); //Get keyhash from publickey (stake credential)
            newAddressBytes = getAddressBytes(null, stakeKeyHash, addressType, header);
        } else {
            byte[] stakeKeyHash = getDelegationCredentialHash(address).orElse(null); //Get stakekeyhash from existing address
            byte[] paymentKeyHash = blake2bHash224(publicKey); //calculate keyhash from public key
            newAddressBytes = getAddressBytes(paymentKeyHash, stakeKeyHash, addressType, header);
        }

        newAddress = new Address(prefix, newAddressBytes);

        if (log.isDebugEnabled()) {
            log.debug("Address to compare : " + address.toBech32());
            log.debug("Address derived from pub key : " + newAddress.toBech32());
        }

        return newAddress.toBech32().equals(address.toBech32());
    }

    /**
     * Get stake address from a base address
     * @param address BaseAddress
     * @return stake address
     * @throws AddressRuntimeException
     */
    public static Address getStakeAddress(@NonNull Address address) {
        if (AddressType.Base != address.getAddressType())
            throw new AddressRuntimeException(
                    String.format("Stake address can't be derived. Required address type: Base Address, Found: %s ",
                            address.getAddressType()));

        byte[] stakeKeyHash = getDelegationCredentialHash(address)
                .orElseThrow(() -> new AddressRuntimeException("StakeKeyHash was not found for the address"));

        AddressType addressType = AddressType.Reward; //target type
        byte[] addressBytes = address.getBytes(); //existing add bytes
        byte header = addressBytes[0]; //existing header
        int stakeHeader;

        if ((header & (1 << 5)) > 0) { //Check if 5th bit is set
            //script hash
            stakeHeader = 0b1111_0000;
        } else {
            //stake key hash
            stakeHeader = 0b1110_0000;
        }

        int network = header & 0b0000_1111; //reset everything except network id bits
        stakeHeader = stakeHeader | network;

        byte[] rewardAddressBytes = getAddressBytes(null, stakeKeyHash, addressType, (byte) stakeHeader);

        return new Address(rewardAddressBytes);
    }

    /**
     * Get stake address from CIP-1852's extended account public key (Bech32 encoded) with prefix "acct_xvk" or "xpub"
     *
     * @param accountPubKey extended account public key (Bech32 encoded) with prefix "acct_xvk" or "xpub"
     * @param network network
     * @return stake address
     */
    public static Address getStakeAddressFromAccountPublicKey(String accountPubKey, Network network) {
        Objects.requireNonNull(accountPubKey, "accountPubKey cannot be null");
        Objects.requireNonNull(network, "network cannot be null");

        if (!accountPubKey.startsWith("acct_xvk") && !accountPubKey.startsWith("xpub")) {
            throw new IllegalArgumentException("Invalid account public key. Must start with 'acct_xvk' or 'xpub'");
        }

        byte[] accountPubKeyBytes = Bech32.decode(accountPubKey).data;

        return getStakeAddressFromAccountPublicKey(accountPubKeyBytes, network);
    }

    /**
     * Get stake address from CIP-1852's extended account public key. Ed25519 public key with chain code.
     *
     * @param accountPubKeyBytes extended account public key. Ed25519 public key with chain code.
     * @param network network
     * @return stake address
     */
    public static Address getStakeAddressFromAccountPublicKey(byte[] accountPubKeyBytes, Network network) {
        Objects.requireNonNull(accountPubKeyBytes, "accountPubKeyBytes cannot be null");
        Objects.requireNonNull(network, "network cannot be null");

        if (accountPubKeyBytes.length != 64) {
            throw new IllegalArgumentException("Invalid account public key");
        }

        HdPublicKey stakeHdPubKey = new CIP1852().getPublicKeyFromAccountPubKey(accountPubKeyBytes, DerivationPath.createStakeAddressDerivationPath());
        return getRewardAddress(stakeHdPubKey, network);
    }

    /**
     * Get StakeKeyHash or ScriptHash from delegation part of a Shelley {@link Address}
     * @param address
     * @return StakeKeyHash or ScriptHash. For Pointer address, delegationPointerHash
     */
    public static Optional<byte[]> getDelegationCredentialHash(Address address) {
        AddressType addressType = address.getAddressType();
        byte[] addressBytes = address.getBytes();

        byte[] stakeKeyHash;
        switch (addressType) {
            case Base:
                stakeKeyHash = new byte[28];
                System.arraycopy(addressBytes, 1 + 28, stakeKeyHash, 0, stakeKeyHash.length);
                break;
            case Enterprise:
                stakeKeyHash = null;
                break;
            case Reward:
                stakeKeyHash = new byte[28];
                System.arraycopy(addressBytes, 1, stakeKeyHash, 0, stakeKeyHash.length);
                break;
            case Ptr: //TODO -- Remove if not required
                stakeKeyHash = new byte[addressBytes.length - 1 - 28];
                System.arraycopy(addressBytes, 1 + 28, stakeKeyHash, 0, stakeKeyHash.length);
                break;
            default:
                throw new AddressRuntimeException("DelegationHash can't be found for address type : " + addressType);
        }

        return Optional.ofNullable(stakeKeyHash);
    }

    /**
     * Get PaymentCredential hash from {@link Address}
     * @param address
     * @return payment key hash or script hash
     */
    public static Optional<byte[]> getPaymentCredentialHash(Address address) {
        AddressType addressType = address.getAddressType();
        byte[] addressBytes = address.getBytes();

        byte[] paymentKeyHash;
        switch (addressType) {
            case Base:
            case Enterprise:
            case Ptr:
                paymentKeyHash = new byte[28];
                System.arraycopy(addressBytes, 1, paymentKeyHash, 0, paymentKeyHash.length);
                break;
            case Reward:
                paymentKeyHash = null;
                break;
            default: {
                throw new AddressRuntimeException("Unable to get payment key hash for address type: " + addressType + ", address=" + address.getAddress());
            }
        }
        return Optional.ofNullable(paymentKeyHash);
    }

    /**
     * Get delegation credential from {@link Address}
     * @param address Shelley address
     * @return Credential if address has a delegation part, else empty
     */
    public static Optional<Credential> getDelegationCredential(Address address) {
        Objects.requireNonNull(address, "address cannot be null");
        if (isStakeKeyHashInDelegationPart(address)) {
            return getDelegationCredentialHash(address)
                    .map(hash -> Credential.fromKey(hash));
        } else if (isScriptHashInDelegationPart(address)) {
            return getDelegationCredentialHash(address)
                    .map(hash -> Credential.fromScript(hash));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get payment credential from {@link Address}
     * @param address Shelley address
     * @return Credential if address has a payment part, else empty
     */
    public static Optional<Credential> getPaymentCredential(Address address) {
        Objects.requireNonNull(address, "address cannot be null");
        if (isPubKeyHashInPaymentPart(address)) {
            return getPaymentCredentialHash(address)
                    .map(hash -> Credential.fromKey(hash));
        } else if (isScriptHashInPaymentPart(address)) {
            return getPaymentCredentialHash(address)
                    .map(hash -> Credential.fromScript(hash));
        }  else {
            return Optional.empty();
        }
    }

    /**
     * Check if payment part of a Shelley address is PubkeyHash
     * @param address
     * @return true if PubkeyHash, otherwise false
     */
    public static boolean isPubKeyHashInPaymentPart(@NonNull Address address) {
        AddressType addressType = address.getAddressType();
        if (addressType != AddressType.Base && addressType != AddressType.Enterprise
                && addressType != AddressType.Ptr) {
            if (log.isDebugEnabled())
                log.warn("Method not supported for address type=" + addressType + ", address=" + address.getAddress());
            return false;
        }

        byte[] addressBytes = address.getBytes();
        byte header = addressBytes[0];
        if ((header & (1 << 4)) == 0) { //Check if 4th bit is not set. If not set, it's pubkey hash
            return true;
        } else
            return false;
    }

    /**
     * Check if payment part of a Shelley address is ScriptHash
     * @param address
     * @return true if ScriptHash, otherwise false
     */
    public static boolean isScriptHashInPaymentPart(@NonNull Address address) {
        AddressType addressType = address.getAddressType();
        if (addressType != AddressType.Base && addressType != AddressType.Enterprise
                && addressType != AddressType.Ptr) {
            if (log.isDebugEnabled())
                log.warn("Method not supported for address type=" + addressType + ", address=" + address.getAddress());
            return false;
        }

        return !isPubKeyHashInPaymentPart(address);
    }

    /**
     * Check if delegation part of a Shelley address is StakeKeyHash
     * @param address
     * @return true if StakeKeyHash, otherwise false
     */
    public static boolean isStakeKeyHashInDelegationPart(@NonNull Address address) {
        AddressType addressType = address.getAddressType();
        if (addressType != AddressType.Base && addressType != AddressType.Ptr
                && addressType != AddressType.Reward) {
            if (log.isDebugEnabled())
                log.warn("Method not supported for address type=" + addressType + ", address=" + address.getAddress());
            return false;
        }

        byte[] addressBytes = address.getBytes();
        byte header = addressBytes[0];
        if (addressType == AddressType.Reward) {
            if ((header & (1 << 4)) == 0) { //Check if 4th bit is not set. If not set, it's stakekey hash
                return true;
            } else
                return false;
        } else { //For Base, Ptr
            if ((header & (1 << 5)) == 0) { //Check if 5th bit is not set. If not set, it's stakekey hash
                return true;
            } else
                return false;
        }
    }

    /**
     * Check if delegation part of a Shelley address is ScriptHash
     * @param address
     * @return true if ScriptHash, otherwise false
     */
    public static boolean isScriptHashInDelegationPart(@NonNull Address address) {
        AddressType addressType = address.getAddressType();
        if (addressType != AddressType.Base && addressType != AddressType.Ptr
                && addressType != AddressType.Reward) {
            if (log.isDebugEnabled())
                log.warn("Method not supported for address type=" + addressType + ", address=" + address.getAddress());
            return false;
        }

        return !isStakeKeyHashInDelegationPart(address);
    }

    private static byte[] variableNatEncode(long num) {
        List<Byte> output = new ArrayList<>();
        output.add((byte)(num & 0x7F));

        num /= 128;
        while(num > 0) {
            output.add((byte)((num & 0x7F) | 0x80));
            num /= 128;
        }
        Collections.reverse(output);

        return Bytes.toArray(output);
    }
}
