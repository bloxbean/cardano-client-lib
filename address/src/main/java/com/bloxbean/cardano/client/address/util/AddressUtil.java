package com.bloxbean.cardano.client.address.util;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressType;
import com.bloxbean.cardano.client.address.ByronAddress;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;

public class AddressUtil {

    /**
     * Check if shelley or byron addresses are valid
     *
     * @param addr - shelley or byron era address
     * @return true if address is valid, false otherwise
     */
    public static boolean isValidAddress(String addr) {
        try {
            addressToBytes(addr);

            return true;
        } catch (AddressExcepion e) {
            return false;
        } catch (AddressRuntimeException e) {
            return false;
        }
    }

    /**
     * Convert a Shelley or Byron address to bytes
     * @param address
     * @return
     * @throws AddressExcepion
     */
    public static byte[] addressToBytes(String address) throws AddressExcepion {
        if (address == null)
            return null;

        if (address.startsWith("addr") || address.startsWith("stake")) { //Shelley address
            Address addressObj = new Address(address);
            return addressObj.getBytes();
        } else { //Try for byron address
            ByronAddress byronAddress = new ByronAddress(address);
            return byronAddress.getBytes();
        }
    }

    /**
     * Convert a Byron address bytes to Base58 Byron address string
     * @param bytes
     * @return
     * @throws AddressExcepion
     */
    public static String bytesToBase58Address(byte[] bytes) throws AddressExcepion { //byron address
        AddressType addressType = AddressEncoderDecoderUtil.readAddressType(bytes);
        if (AddressType.Byron.equals(addressType)) {
            ByronAddress byronAddress = new ByronAddress(bytes);
            return byronAddress.toBase58();
        } else {
            throw new AddressExcepion("Not a Byron address");
        }
    }

    /**
     * Convert byte[] to a Shelley or Byron address
     * @param bytes
     * @return
     * @throws AddressExcepion
     */
    public static String bytesToAddress(byte[] bytes) throws AddressExcepion {
        AddressType addressType = AddressEncoderDecoderUtil.readAddressType(bytes);
        if (AddressType.Byron.equals(addressType)) {
            ByronAddress byronAddress = new ByronAddress(bytes);
            return byronAddress.toBase58();
        } else {
            Address address = new Address(bytes);
            return address.toBech32();
        }
    }


}
