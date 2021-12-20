package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;

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

}
