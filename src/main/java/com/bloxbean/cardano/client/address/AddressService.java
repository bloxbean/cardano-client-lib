package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.bip32.key.HdPublicKey;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.transaction.spec.NetworkId;

import static com.bloxbean.cardano.client.address.AddressEncoderDecoderUtil.*;

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

    public Address getAddress(HdPublicKey payment, HdPublicKey stake, Network networkInfo, AddressType addressType) {
        NetworkId network = getNetworkId(networkInfo);

        byte[] paymentEncoded = null;
        if (payment != null)
            paymentEncoded = KeyGenUtil.blake2bHash224(payment.getKeyData());

        byte[] stakeEncoded = null;
        if (stake != null)
            stakeEncoded = KeyGenUtil.blake2bHash224(stake.getKeyData());

        //get prefix
        String prefix = getPrefixHeader(addressType) + getPrefixTail(network);

        //get header
        byte header = getAddressHeader(networkInfo, addressType);
        //get body
        byte[] addressArray;
        switch (addressType) {
            case Base:
                addressArray = new byte[1 + paymentEncoded.length + stakeEncoded.length];
                addressArray[0] = header;
                System.arraycopy(paymentEncoded, 0, addressArray, 1, paymentEncoded.length);
                System.arraycopy(stakeEncoded, 0, addressArray, paymentEncoded.length + 1, stakeEncoded.length);
                break;
            case Enterprise:
                addressArray = new byte[1 + paymentEncoded.length];
                addressArray[0] = header;
                System.arraycopy(paymentEncoded, 0, addressArray, 1, paymentEncoded.length);
                break;
            case Reward:
                addressArray = new byte[1 + stakeEncoded.length];
                addressArray[0] = header;
                System.arraycopy(stakeEncoded, 0, addressArray, 1, stakeEncoded.length);
                break;
            default:
                throw new AddressRuntimeException("Unknown address type");
        }

        return new Address(prefix, addressArray);
    }
}
