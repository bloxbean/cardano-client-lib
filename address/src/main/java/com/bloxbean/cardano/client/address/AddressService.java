package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.script.Script;
import com.google.common.primitives.Bytes;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.address.util.AddressEncoderDecoderUtil.*;
import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash224;

@Slf4j
public class AddressService {

    private static AddressService instance;

    private AddressService() {

    }

    public static AddressService getInstance() {
        if (instance == null) {
            synchronized (AddressService.class) {
                if (instance == null)
                    instance = new AddressService();
            }
        }

        return instance;
    }

    //header: 0000....
    public Address getBaseAddress(HdPublicKey paymentKey, HdPublicKey delegationKey, Network networkInfo) {
        if (paymentKey == null || delegationKey == null)
            throw new AddressRuntimeException("paymentkey and delegationKey cannot be null");

        byte[] paymentKeyHash = paymentKey.getKeyHash();
        byte[] delegationKeyHash = delegationKey.getKeyHash();

        byte headerType = 0b0000_0000;

        return getAddress(paymentKeyHash, delegationKeyHash, headerType, networkInfo, AddressType.Base);
    }

    //header: 0001....
    public Address getBaseAddress(Script paymentKey, HdPublicKey delegationKey, Network networkInfo) throws CborSerializationException {
        if (paymentKey == null || delegationKey == null)
            throw new AddressRuntimeException("paymentkey and delegationKey cannot be null");

        byte[] paymentKeyHash = paymentKey.getScriptHash();
        byte[] delegationKeyHash = delegationKey.getKeyHash();

        byte headerType = 0b0001_0000;

        return getAddress(paymentKeyHash, delegationKeyHash, headerType, networkInfo, AddressType.Base);
    }

    //header: 0010....
    public Address getBaseAddress(HdPublicKey paymentKey, Script delegationKey, Network networkInfo) throws CborSerializationException {
        if (paymentKey == null || delegationKey == null)
            throw new AddressRuntimeException("paymentkey and delegationKey cannot be null");

        byte[] paymentKeyHash = paymentKey.getKeyHash();
        byte[] delegationKeyHash = delegationKey.getScriptHash();

        byte headerType = 0b0010_0000;

        return getAddress(paymentKeyHash, delegationKeyHash, headerType, networkInfo, AddressType.Base);
    }

    //header: 0011....
    public Address getBaseAddress(Script paymentKey, Script delegationKey, Network networkInfo) throws CborSerializationException {
        if (paymentKey == null || delegationKey == null)
            throw new AddressRuntimeException("paymentkey and delegationKey cannot be null");

        byte[] paymentKeyHash = paymentKey.getScriptHash();
        byte[] delegationKeyHash = delegationKey.getScriptHash();

        byte headerType = 0b0011_0000;

        return getAddress(paymentKeyHash, delegationKeyHash, headerType, networkInfo, AddressType.Base);
    }

    //TODO -- Implement Pointer address
    //header: 0100....
    public Address getPointerAddress(HdPublicKey paymentKey, Pointer delegationPointer, Network networkInfo) {
        if (paymentKey == null || delegationPointer == null)
            throw new AddressRuntimeException("paymentkey and delegationKey cannot be null");

        byte[] paymentKeyHash = paymentKey.getKeyHash();
        byte[] delegationPointerHash = BytesUtil.merge(variableNatEncode(delegationPointer.slot),
                variableNatEncode(delegationPointer.txIndex), variableNatEncode(delegationPointer.certIndex));

        byte headerType = 0b0100_0000;
        return getAddress(paymentKeyHash, delegationPointerHash, headerType, networkInfo, AddressType.Ptr);
    }

    //header: 0101....
    public Address getPointerAddress(Script paymentKey, Pointer delegationPointer, Network networkInfo) throws CborSerializationException {
        if (paymentKey == null || delegationPointer == null)
            throw new AddressRuntimeException("paymentkey and delegationKey cannot be null");

        byte[] paymentKeyHash = paymentKey.getScriptHash();
        byte[] delegationPointerHash = BytesUtil.merge(variableNatEncode(delegationPointer.slot),
                variableNatEncode(delegationPointer.txIndex), variableNatEncode(delegationPointer.certIndex));

        byte headerType = 0b0101_0000;
        return getAddress(paymentKeyHash, delegationPointerHash, headerType, networkInfo, AddressType.Ptr);
    }

    //header: 0110....
    public Address getEntAddress(HdPublicKey paymentKey, Network networkInfo)  {
        if (paymentKey == null)
            throw new AddressRuntimeException("paymentkey cannot be null");

        byte[] paymentKeyHash = paymentKey.getKeyHash();

        byte headerType = 0b0110_0000;

        return getAddress(paymentKeyHash, null, headerType, networkInfo, AddressType.Enterprise);
    }

    //header: 0111....
    public Address getEntAddress(Script paymentKey, Network networkInfo) throws CborSerializationException {
        if (paymentKey == null)
            throw new AddressRuntimeException("paymentkey cannot be null");

        byte[] paymentKeyHash = paymentKey.getScriptHash();

        byte headerType = 0b0111_0000;

        return getAddress(paymentKeyHash, null, headerType, networkInfo, AddressType.Enterprise);
    }

    //header: 1110....
    public Address getRewardAddress(HdPublicKey stakeKey, Network networkInfo)  {
        if (stakeKey == null)
            throw new AddressRuntimeException("stakeKey cannot be null");

        byte[] stakeKeyHash = stakeKey.getKeyHash();

        int headerType = 0b1110_0000;

        return getAddress(null, stakeKeyHash, (byte) headerType, networkInfo, AddressType.Reward);
    }

    //header: 1111....
    public Address getRewardAddress(Script stakeKey, Network networkInfo) throws CborSerializationException {
        if (stakeKey == null)
            throw new AddressRuntimeException("stakeKey cannot be null");

        byte[] stakeKeyHash = stakeKey.getScriptHash();

        int headerType = 0b1111_0000;

        return getAddress(null, stakeKeyHash, (byte) headerType, networkInfo, AddressType.Reward);
    }

    private Address getAddress(byte[] paymentKeyHash, byte[] stakeKeyHash, byte headerKind, Network networkInfo, AddressType addressType) {
        NetworkId network = getNetworkId(networkInfo);

        //get prefix
        String prefix = getPrefixHeader(addressType) + getPrefixTail(network);

        //get header
        byte header = getAddressHeader(headerKind, networkInfo, addressType);
        byte[] addressArray = getAddressBytes(paymentKeyHash, stakeKeyHash, addressType, header);

        return new Address(prefix, addressArray);
    }

    private byte[] getAddressBytes(byte[] paymentKeyHash, byte[] stakeKeyHash, AddressType addressType, byte header) {
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
     * @param address
     * @param publicKey
     * @return true or false
     */
    public boolean verifyAddress(@NonNull Address address, byte[] publicKey) {
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
            byte[] stakeKeyHash = getStakeKeyHash(address).orElse(null); //Get stakekeyhash from existing address
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
    public Address getStakeAddress(@NonNull Address address) {
        if (AddressType.Base != address.getAddressType())
            throw new AddressRuntimeException(
                    String.format("Stake address can't be derived. Required address type: Base Address, Found: %s ",
                            address.getAddressType()));

        byte[] stakeKeyHash = getStakeKeyHash(address)
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
     * Get StakeKeyHash from {@link Address}
     * @param address
     * @return stake key hash
     */
    public Optional<byte[]> getStakeKeyHash(Address address) {
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
            case Ptr:
                stakeKeyHash = new byte[addressBytes.length - 1 - 28];
                System.arraycopy(addressBytes, 1 + 28, stakeKeyHash, 0, stakeKeyHash.length);
                break;
            default:
                throw new AddressRuntimeException("StakeKeyHash can't be found for address type : " + addressType);
        }

        return Optional.ofNullable(stakeKeyHash);
    }

    /**
     * Get PaymentKeyHash from {@link Address}
     * @param address
     * @return payment key hash
     */
    public Optional<byte[]> getPaymentKeyHash(Address address) {
        AddressType addressType = address.getAddressType();
        byte[] addressBytes = address.getBytes();

        byte[] paymentKeyHash;
        switch (addressType) {
            case Base:
            case Enterprise:
                paymentKeyHash = new byte[28];
                System.arraycopy(addressBytes, 1, paymentKeyHash, 0, paymentKeyHash.length);
                break;
            case Reward:
                paymentKeyHash = null;
                break;
            default: {
                throw new AddressRuntimeException("Unable to get payment key hash for address type: " + addressType);
            }
        }
        return Optional.ofNullable(paymentKeyHash);
    }

    private byte[] variableNatEncode(long num) {
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
