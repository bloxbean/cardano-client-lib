package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.script.Script;
import com.google.common.primitives.Bytes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.bloxbean.cardano.client.address.util.AddressEncoderDecoderUtil.*;

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

        return new Address(prefix, addressArray);
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
