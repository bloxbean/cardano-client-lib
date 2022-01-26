package com.bloxbean.cardano.client.address.util;

import com.bloxbean.cardano.client.address.AddressType;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.transaction.spec.NetworkId;

public class AddressEncoderDecoderUtil {

    public static String getPrefixHeader(AddressType addressType) throws AddressRuntimeException {
        String prefixHead;
        switch (addressType) {
            case Base:
                prefixHead = "addr";
                break;
            case Reward:
                prefixHead = "stake";
                break;
            case Enterprise:
                prefixHead = "addr";
                break;
            case Ptr:
                prefixHead = "addr";
                break;
            default:
                throw new AddressRuntimeException("Unknown address type");
        }

        return prefixHead;
    }

    public static String getPrefixTail(NetworkId network) {
        String prefixTail;
        switch (network) {
            case TESTNET:
                prefixTail = "_test";
                break;
            case MAINNET:
                prefixTail = "";
                break;
            default:
                throw new AddressRuntimeException("Unknown network type - " + network);
        }

        return prefixTail;
    }

    public static NetworkId getNetworkId(Network networkInfo) {
        NetworkId network;
        if (Networks.mainnet().equals(networkInfo)) {
            network = NetworkId.MAINNET;
        } else if (Networks.testnet().equals(networkInfo)) {
            network = NetworkId.TESTNET;
        } else {
            throw new AddressRuntimeException("Unknown network type - " + networkInfo);
        }
        return network;
    }

    public static byte getAddressHeader(byte headerKind, Network network, AddressType addressType) {
        return (byte) (headerKind | network.getNetworkId() & 0xF);
    }

    public static AddressType readAddressType(byte[] addressBytes) {
        byte header = addressBytes[0];

        AddressType addressType;
        switch ((header & 0xF0) >> 4) {
            case 0b0000: //base
            case 0b0001:
            case 0b0010:
            case 0b0011:
                addressType = AddressType.Base;
                break;
            case 0b0100: //pointer
            case 0b0101:
                addressType = AddressType.Ptr;
                break;
            case 0b0110: //enterprise
            case 0b0111:
                addressType = AddressType.Enterprise;
                break;
            case 0b1110: //reward
            case 0b1111:
                addressType = AddressType.Reward;
                break;
            case 0b1000: //byron
                addressType = AddressType.Byron;
                break;
            default:
                throw new AddressRuntimeException("Unknown address type");
        }

        return addressType;
    }

    public static Network readNetworkType(byte[] addressBytes) {
        byte header = addressBytes[0];

        Network network;
        switch (header & 0x0f) {
            case 0x00:
                network = Networks.testnet();
                break;
            case 0x01:
                network = Networks.mainnet();
                break;
            default:
                throw new AddressRuntimeException("Unknown network type");
        }

        return network;
    }

}
