package com.bloxbean.cardano.client.address;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Base58;
import com.bloxbean.cardano.client.crypto.exception.AddressFormatException;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Byron era (bootstrap) address.
 * <p>
 * The address is validated the way the Byron ledger decodes it: the CBOR envelope, the CRC32 checksum, the
 * 28-byte root, the attributes and the address type are all checked. Any network is accepted; use
 * {@link #getNetwork()} or {@link #getProtocolMagic()} to restrict it.
 */
public class ByronAddress {
    private final byte[] bytes;
    private final String address;
    private final byte[] root;
    private final byte[] derivationPath;
    private final Long protocolMagic;
    private final ByronAddressType byronType;

    /**
     * Create a ByronAddress from its Base58 encoding
     *
     * @param address Base58 encoded Byron address
     * @throws AddressFormatException if the address is not a valid Byron address
     */
    public ByronAddress(String address) {
        this(decodeBase58(address), address);
    }

    /**
     * Create a ByronAddress from its bytes
     *
     * @param bytes Byron address bytes
     * @throws AddressFormatException if the bytes are not a valid Byron address
     */
    public ByronAddress(byte[] bytes) {
        this(bytes, null);
    }

    private ByronAddress(byte[] bytes, String address) {
        ByronAddressDecoder.Decoded decoded = ByronAddressDecoder.decode(bytes);

        this.bytes = bytes.clone();
        this.address = address != null ? address : Base58.encode(bytes);
        this.root = decoded.root;
        this.derivationPath = decoded.derivationPath;
        this.protocolMagic = decoded.protocolMagic;
        this.byronType = decoded.type;
    }

    private static byte[] decodeBase58(String address) {
        if (address == null)
            throw new AddressFormatException("Invalid Byron address: null");

        return Base58.decode(address);
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

    /**
     * Get the address root, the 28-byte Blake2b-224 hash of the address type, spending data and attributes
     *
     * @return address root
     */
    public byte[] getRoot() {
        return root.clone();
    }

    /**
     * Get the address type
     *
     * @return {@link ByronAddressType#VerKey} or {@link ByronAddressType#Redeem}
     */
    public ByronAddressType getByronType() {
        return byronType;
    }

    /**
     * Get the protocol magic stored in the address attributes. Mainnet addresses don't carry one.
     *
     * @return protocol magic, or empty for a mainnet address
     */
    public OptionalLong getProtocolMagic() {
        return protocolMagic != null ? OptionalLong.of(protocolMagic) : OptionalLong.empty();
    }

    /**
     * Get the network of this address, derived from its protocol magic.
     *
     * @return {@link Networks#mainnet()} if the address has no protocol magic, the known network with a matching
     * protocol magic, or a testnet {@link Network} with the address's protocol magic otherwise
     */
    public Network getNetwork() {
        if (protocolMagic == null)
            return Networks.mainnet();

        for (Network network : new Network[]{Networks.testnet(), Networks.preprod(), Networks.preview()}) {
            if (network.getProtocolMagic() == protocolMagic)
                return network;
        }

        return new Network(Networks.testnet().getNetworkId(), protocolMagic);
    }

    /**
     * Get the encrypted HD derivation path stored in the address attributes. Only addresses of legacy random
     * wallets (e.g. Daedalus) carry one.
     *
     * @return encrypted derivation path payload, or empty if the address doesn't have one
     */
    public Optional<byte[]> getDerivationPath() {
        return Optional.ofNullable(derivationPath).map(byte[]::clone);
    }
}
