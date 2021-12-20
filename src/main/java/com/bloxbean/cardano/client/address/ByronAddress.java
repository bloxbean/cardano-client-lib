package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.crypto.Base58;
import com.bloxbean.cardano.client.crypto.exception.AddressFormatException;

import static com.bloxbean.cardano.client.address.util.AddressEncoderDecoderUtil.readAddressType;

public class ByronAddress {
    private byte[] bytes;
    private String address;

    public ByronAddress(String address) {
        this.bytes = Base58.decode(address);
        AddressType addressType = readAddressType(this.bytes);

        if (addressType == null || !AddressType.Byron.equals(addressType)) {
            throw new AddressFormatException("Invalid Byron address");
        }

        this.address = address;
    }

    public ByronAddress(byte[] bytes) {
        this.bytes = bytes;
        this.address = Base58.encode(bytes);
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String toBase58() {
        return address;
    }

    public String getAddress() {
        return address;
    }
}
