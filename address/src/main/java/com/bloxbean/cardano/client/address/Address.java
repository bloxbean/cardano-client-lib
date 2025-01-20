package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;

import java.util.Optional;

import static com.bloxbean.cardano.client.address.util.AddressEncoderDecoderUtil.*;

/**
 * Address class represents Shelley address
 */
public class Address {
    public static final String ADDR_VKH_PREFIX = "addr_vkh";
    private String prefix;
    private byte[] bytes;
    private String address;
    private AddressType addressType;
    private Network network;

    //Optional
    private DerivationPath derivationPath;

    /**
     * Create Address from a byte array
     * @param prefix Address prefix
     * @param bytes Address bytes
     */
    public Address(String prefix, byte[] bytes) {
        this.prefix = prefix;
        this.bytes = bytes;

        this.addressType = readAddressType(this.bytes);
        this.network = readNetworkType(this.bytes);
    }

    /**
     * Create Address from a Bech32 address
     * @param address Bech32 address
     */
    public Address(String address) {
        if (address == null || address.isEmpty())
            throw new AddressRuntimeException("Address cannot be null or empty");

        this.address = address;
        Bech32.Bech32Data bech32Data = Bech32.decode(address);
        this.bytes = bech32Data.data;
        this.prefix = bech32Data.hrp;

        this.addressType = readAddressType(this.bytes);
        this.network = readNetworkType(this.bytes);
    }

    public Address(String address, DerivationPath derivationPath) {
        this(address);
        this.derivationPath = derivationPath;
    }

    /**
     * Create Address from a byte array
     * @param addressBytes
     */
    public Address(byte[] addressBytes) {
        if (addressBytes == null)
            throw new AddressRuntimeException("Address cannot be null or empty");

        this.bytes = addressBytes;
        this.addressType = readAddressType(this.bytes);
        this.network = readNetworkType(this.bytes);

        this.prefix = getPrefixHeader(addressType) + getPrefixTail(getNetworkId(this.network));
    }

    public byte[] getBytes() {
        return bytes;
    }

    /**
     * Returns Bech32 encoded address
     * @return Bech32 encoded address
     */
    public String toBech32() {
        if (address == null || address.isEmpty()) {
            address = Bech32.encode(bytes, prefix);
        }
        return address;
    }

    /**
     * Returns address prefix
     * @return address prefix
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Returns Bech32 encoded address
     * @return Bech32 encoded address
     */
    public String getAddress() {
        return toBech32();
    }

    /**
     * Returns AddressType
     * @return AddressType
     */
    public AddressType getAddressType() {
        return addressType;
    }

    /**
     * Returns Network
     * @return Network
     */
    public Network getNetwork() {
        return network;
    }

    /**
     * Get StakeKeyHash or ScriptHash from delegation part of a Shelley {@link Address}
     * @return StakeKeyHash or ScriptHash. For Pointer address, delegationPointerHash
     */
    public Optional<byte[]> getDelegationCredentialHash() {
        return AddressProvider.getDelegationCredentialHash(this);
    }

    /**
     * Get PaymentCredential from {@link Address}
     * @return payment key hash or script hash
     */
    public Optional<byte[]> getPaymentCredentialHash() {
        return AddressProvider.getPaymentCredentialHash(this);
    }

    /**
     * Get delegation credential from {@link Address}
     * @return Credential if address has a delegation path, otherwise empty
     */
    public Optional<Credential> getDelegationCredential() {
        return AddressProvider.getDelegationCredential(this);
    }

    /**
     * Get payment credential from {@link Address}
     * @return Credential if address has a payment path, otherwise empty
     */
    public Optional<Credential> getPaymentCredential() {
        return AddressProvider.getPaymentCredential(this);
    }

    /**
     * Check if payment part of a Shelley address is PubkeyHash
     * @return true if PubkeyHash, otherwise false
     */
    public boolean isPubKeyHashInPaymentPart() {
        return AddressProvider.isPubKeyHashInPaymentPart(this);
    }

    /**
     * Check if payment part of a Shelley address is ScriptHash
     * @return true if ScriptHash, otherwise false
     */
    public boolean isScriptHashInPaymentPart() {
        return AddressProvider.isScriptHashInPaymentPart(this);
    }

    /**
     * Check if delegation part of a Shelley address is StakeKeyHash
     * @return true if StakeKeyHash, otherwise false
     */
    public boolean isStakeKeyHashInDelegationPart() {
        return AddressProvider.isStakeKeyHashInDelegationPart(this);
    }

    /**
     * Check if delegation part of a Shelley address is ScriptHash
     * @return true if ScriptHash, otherwise false
     */
    public boolean isScriptHashInDelegationPart() {
        return AddressProvider.isScriptHashInDelegationPart(this);
    }

    /**
     * Retrieves the Bech32-encoded address verification key hash of the payment credential
     * associated with the address, if available.
     *
     *
     * @return An {@link Optional} containing the Bech32-encoded verification key hash
     *         if the payment credential hash is available, or an empty {@link Optional}
     *         if the payment credential hash is absent.
     */
    public Optional<String> getBech32VerificationKeyHash() {
        return getPaymentCredentialHash()
                .map(paymentCred -> Bech32.encode(paymentCred, ADDR_VKH_PREFIX));
    }

    public Optional<DerivationPath> getDerivationPath() {
        return Optional.ofNullable(derivationPath);
    }

}
