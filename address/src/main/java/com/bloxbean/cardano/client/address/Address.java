package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;

import java.util.Optional;

import static com.bloxbean.cardano.client.address.util.AddressEncoderDecoderUtil.*;

public class Address {
    private String prefix;
    private byte[] bytes;
    private String address;
    private AddressType addressType;
    private Network network;

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

    public String toBech32() {
        if (address == null || address.isEmpty()) {
            address = Bech32.encode(bytes, prefix);
        }
        return address;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getAddress() {
        return toBech32();
    }

    public AddressType getAddressType() {
        return addressType;
    }

    public Network getNetwork() {
        return network;
    }

    /**
     * Get StakeKeyHash or ScriptHash from delegation part of a Shelley {@link Address}
     * @return StakeKeyHash or ScriptHash. For Pointer address, delegationPointerHash
     */
    public Optional<byte[]> getDelegationCredential() {
        return AddressProvider.getDelegationCredential(this);
    }

    /**
     * Get PaymentCredential from {@link Address}
     * @return payment key hash or script hash
     */
    public Optional<byte[]> getPaymentCredential() {
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
}
