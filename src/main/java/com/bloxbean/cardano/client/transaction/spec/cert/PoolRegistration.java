package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bloxbean.cardano.client.transaction.util.CborSerializationUtil.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class PoolRegistration implements Certificate {
    private final CertificateType type = CertificateType.POOL_REGISTRATION;

    /**
     * 28 bytes pool key hash (verification key hash)
     * Example:
     *  HexUtil.decodeHexString(KeyGenUtil.getKeyHash(poolVerificationKey))
     */
    private byte[] operator;

    /**
     * VRF verification key hash (32 bytes)
     * Example:
     *  String vrfVkeyCbor = "58404682ed74c2ae...";
     *  Blake2bUtil.blake2bHash256(KeyGenCborUtil.cborToBytes(vrfVkeyCbor))
     */
    private byte[] vrfKeyHash;

    private BigInteger pledge;
    private BigInteger cost;
    private UnitInterval margin;

    /**
     * Reward address in hex
     * Example:
     *  HexUtil.encodeHexString(stakeAddress.getBytes())
     */
    private String rewardAccount;

    /**
     * poolowners addr keyhash (28 bytes)
     * Example:
     *  Set.of(HexUtil.encodeHexString(account.stakeHdKeyPair().getPublicKey().getKeyHash()))
     */
    private Set<String> poolOwners;
    private List<Relay> relays;

    /**
     * Pool metadata url
     */
    private String poolMetadataUrl;

    /**
     * Pool metadata hash (32 bytes)
     */
    private String poolMetadataHash;

    @Override
    public Array serialize() throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(3));
        array.add(new ByteString(operator));
        array.add(new ByteString(vrfKeyHash));
        array.add(new UnsignedInteger(pledge));
        array.add(new UnsignedInteger(cost));

        try {
            array.add(new RationalNumber(
                    new UnsignedInteger(margin.getNumerator()), new UnsignedInteger(margin.getDenominator())));
        } catch (CborException e) {
            throw new CborSerializationException("Serialization error", e);
        }

        array.add(new ByteString(HexUtil.decodeHexString(rewardAccount)));

        //pool owners
        Array poolOwnersArr = new Array();
        poolOwners.stream().forEach(poolOwner -> {
            poolOwnersArr.add(new ByteString(HexUtil.decodeHexString(poolOwner)));
        });
        array.add(poolOwnersArr);

        //relays
        Array relayArr = new Array();
        if (relays != null) {
            for (Relay relay : relays) {
                relayArr.add(relay.serialize());
            }
        }
        array.add(relayArr);

        //pool metadata
        if (poolMetadataHash == null) {
            array.add(SimpleValue.NULL);
        } else {
            Array poolMetadataArray = new Array();
            poolMetadataArray.add(new UnicodeString(poolMetadataUrl));
            poolMetadataArray.add(new ByteString(HexUtil.decodeHexString(poolMetadataHash)));

            array.add(poolMetadataArray);
        }

        return array;
    }

    public static PoolRegistration deserialize(DataItem di) throws CborDeserializationException {
        Array poolRegistrationArr = (Array) di;

        List<DataItem> dataItemList = poolRegistrationArr.getDataItems();
        if (dataItemList == null || dataItemList.size() != 10) {
            throw new CborDeserializationException("PoolRegistration deserialization failed. Invalid number of DataItem(s) : "
                    + (dataItemList != null ? String.valueOf(dataItemList.size()) : null));
        }

        UnsignedInteger type = (UnsignedInteger) dataItemList.get(0);
        if (type == null || type.getValue().intValue() != 3)
            throw new CborDeserializationException("PoolRegistration deserialization failed. Invalid type : "
                    + type != null ? String.valueOf(type.getValue().intValue()) : null);

        //List<DataItem> paramDataItems = ((Array)dataItemList.get(1)).getDataItems();
        byte[] operator = toBytes(dataItemList.get(1));
        byte[] vrfKeyHash = toBytes(dataItemList.get(2));
        BigInteger pledge = getBigInteger(dataItemList.get(3));
        BigInteger cost = getBigInteger(dataItemList.get(4));

        RationalNumber marginDI = (RationalNumber) dataItemList.get(5);
        UnitInterval margin = new UnitInterval(getBigInteger(marginDI.getNumerator()), getBigInteger(marginDI.getDenominator()));
        String rewardAccount = toHex(dataItemList.get(6));

        //Pool Owners0
        Set<String> poolOwners = new HashSet<>();
        List<DataItem> poolOwnersDataItems = ((Array) dataItemList.get(7)).getDataItems();
        for (DataItem poolOwnerDI : poolOwnersDataItems) {
            poolOwners.add(toHex(poolOwnerDI));
        }

        //Relays
        List<Relay> relays = new ArrayList<>();
        try {
            List<DataItem> relaysDataItems = ((Array) dataItemList.get(8)).getDataItems();
            for (DataItem relayDI : relaysDataItems) {
                relays.add(deserializeRelay(relayDI));
            }
        } catch (Exception e) {
            throw new CborDeserializationException("Deserialization error", e);
        }

        //pool metadata
        DataItem poolMetaDataDI = dataItemList.get(9);
        String metadataUrl = null;
        String metadataHash = null;
        if (poolMetaDataDI != SimpleValue.NULL) {
            List<DataItem> poolMetadataDataItems = ((Array) poolMetaDataDI).getDataItems();
            metadataUrl = toUnicodeString(poolMetadataDataItems.get(0));
            metadataHash = toHex(poolMetadataDataItems.get(1));
        }

        PoolRegistration poolRegistration = PoolRegistration.builder()
                .operator(operator)
                .vrfKeyHash(vrfKeyHash)
                .pledge(pledge)
                .cost(cost)
                .margin(margin)
                .rewardAccount(rewardAccount)
                .poolOwners(poolOwners)
                .relays(relays)
                .poolMetadataUrl(metadataUrl)
                .poolMetadataHash(metadataHash)
                .build();

        return poolRegistration;
    }

    private static Relay deserializeRelay(DataItem relayDI) throws CborDeserializationException, CborException {
        List<DataItem> relayItems = ((Array) relayDI).getDataItems();
        int type = toInt(relayItems.get(0));

        int port = 0;
        String dns = null;

        if (type == 0) { //Single host addr
            Inet4Address ipv4 = null;
            Inet6Address ipv6 = null;

            DataItem itemDI = relayItems.get(1);
            port = itemDI != SimpleValue.NULL ? toInt(itemDI) : null;

            itemDI = relayItems.get(2);
            if (itemDI != SimpleValue.NULL) {
                byte[] ipv4Bytes = toBytes(itemDI);
                try {
                    ipv4 = (Inet4Address) Inet4Address.getByAddress(ipv4Bytes);
                } catch (Exception ex) {
                    throw new CborDeserializationException("Unable to convert byte[] to ipv4 address, {}, cbor: {}" +
                            HexUtil.encodeHexString(CborSerializationUtil.serialize(relayDI)), ex);
                }
            }

            //ipv6
            itemDI = relayItems.get(3);
            if (itemDI != SimpleValue.NULL) {
                byte[] ipv6Bytes = toBytes(itemDI);
                try {
                    ipv6 = (Inet6Address) Inet6Address.getByAddress(ipv6Bytes);
                } catch (Exception ex) {
                    throw new CborDeserializationException("Unable to convert byte[] to ipv6 address, {}, cbor: {}" +
                            HexUtil.encodeHexString(CborSerializationUtil.serialize(relayDI)), ex);
                }
            }

            return new SingleHostAddr(port, ipv4, ipv6);
        } else if (type == 1) {
            DataItem itemDI = relayItems.get(1);
            port = itemDI != SimpleValue.NULL ? toInt(itemDI) : null;

            itemDI = relayItems.get(2);
            dns = itemDI != SimpleValue.NULL ? toUnicodeString(itemDI) : null;

            return new SingleHostName(port, dns);
        } else if (type == 2) {
            DataItem itemDI = relayItems.get(1);
            dns = itemDI != SimpleValue.NULL ? toUnicodeString(itemDI) : null;

            return new MultiHostName(dns);
        }
        throw new CborDeserializationException("Relay deserialization failed. Invalid type : " + type);
    }

    @Override
    public String toString() {
        return "PoolRegistration{" +
                "type=" + type +
                ", operator=" + (operator != null ? HexUtil.encodeHexString(operator) : null) +
                ", vrfKeyHash=" + (vrfKeyHash != null ? HexUtil.encodeHexString(vrfKeyHash) : null) +
                ", pledge=" + pledge +
                ", cost=" + cost +
                ", margin=" + margin +
                ", rewardAccount='" + rewardAccount + '\'' +
                ", poolOwners=" + poolOwners +
                ", relays=" + relays +
                ", poolMetadataUrl='" + poolMetadataUrl + '\'' +
                ", poolMetadataHash='" + poolMetadataHash + '\'' +
                '}';
    }

}
