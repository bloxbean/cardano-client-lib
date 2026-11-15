package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.raw.RawPoolMetadata;
import com.bloxbean.cardano.client.transaction.raw.RawRelay;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.serializers.ByteArrayToHexSerializer;
import com.bloxbean.cardano.client.util.serializers.HexToByteArrayDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.*;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.*;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.createArray;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.requireDijkstra;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PoolRegistration implements Certificate {
    private final CertificateType type = CertificateType.POOL_REGISTRATION;

    /**
     * 28 bytes pool key hash (verification key hash)
     * Example:
     *  HexUtil.decodeHexString(KeyGenUtil.getKeyHash(poolVerificationKey))
     */
    @JsonSerialize(using = ByteArrayToHexSerializer.class)
    @JsonDeserialize(using = HexToByteArrayDeserializer.class)
    private byte[] operator;

    /**
     * VRF verification key hash (32 bytes)
     * Example:
     *  String vrfVkeyCbor = "58404682ed74c2ae...";
     *  Blake2bUtil.blake2bHash256(KeyGenCborUtil.cborToBytes(vrfVkeyCbor))
     */
    @JsonSerialize(using = ByteArrayToHexSerializer.class)
    @JsonDeserialize(using = HexToByteArrayDeserializer.class)
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

    /**
     * The pool's BLS key, written after the VRF key hash, and only in Dijkstra.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BlsKey blsKey;

    /**
     * The constructor of the fields before Dijkstra, kept for source and binary compatibility; use the builder for the
     * BLS key.
     */
    @SuppressWarnings("java:S107") // the old signature, kept for callers
    public PoolRegistration(byte[] operator, byte[] vrfKeyHash, BigInteger pledge, BigInteger cost, UnitInterval margin,
                            String rewardAccount, Set<String> poolOwners, List<Relay> relays, String poolMetadataUrl,
                            String poolMetadataHash) {
        this(operator, vrfKeyHash, pledge, cost, margin, rewardAccount, poolOwners, relays, poolMetadataUrl,
                poolMetadataHash, null);
    }

    @Override
    @JsonIgnore
    public Array serialize(Era era) throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(3));
        array.add(new ByteString(operator));
        array.add(new ByteString(vrfKeyHash));
        if (blsKey != null) {
            requireDijkstra(era, "PoolRegistration.blsKey");
            array.add(blsKey.serialize());
        }
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
        Array poolOwnersArr = createArray(era);
        //a set, in Haskell's order for its KeyHash
        poolOwners.stream()
                .map(HexUtil::decodeHexString)
                .sorted(Arrays::compareUnsigned)
                .forEach(poolOwner -> poolOwnersArr.add(new ByteString(poolOwner)));
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
        return from(RawCertificate.lenient(CertKind.POOL_REGISTRATION, CborSerializationUtil.span(di)));
    }

    static PoolRegistration from(RawCertificate cert) throws CborDeserializationException {
        RawModels.exact(cert);
        Set<String> poolOwners = new HashSet<>();
        for (byte[] poolOwner : cert.poolOwners())
            poolOwners.add(HexUtil.encodeHexString(poolOwner));

        List<Relay> relays = new ArrayList<>();
        try {
            for (RawRelay relay : cert.relays())
                relays.add(relay(relay));
        } catch (Exception e) {
            throw new CborDeserializationException("Deserialization error", e);
        }

        RawPoolMetadata metadata = cert.poolMetadata().orElse(null);
        return PoolRegistration.builder()
                .operator(cert.poolKeyHash())
                .vrfKeyHash(cert.vrfKeyHash())
                .pledge(cert.pledge())
                .cost(cert.cost())
                .margin(cert.margin())
                .rewardAccount(HexUtil.encodeHexString(cert.rewardAccount()))
                .poolOwners(poolOwners)
                .relays(relays)
                .poolMetadataUrl(metadata != null ? metadata.url() : null)
                .poolMetadataHash(metadata != null ? HexUtil.encodeHexString(metadata.hash()) : null)
                .blsKey(cert.blsKey().map(BlsKey::from).orElse(null))
                .build();
    }

    private static Relay relay(RawRelay relay) throws CborDeserializationException {
        switch (relay.kind()) {
            case SINGLE_HOST_ADDR:
                return new SingleHostAddr(relay.port().orElse(null), address(relay.ipv4(), Inet4Address.class),
                        address(relay.ipv6(), Inet6Address.class));
            case SINGLE_HOST_NAME:
                return new SingleHostName(relay.port().orElse(null), relay.dnsName().orElse(null));
            default:
                return new MultiHostName(relay.dnsName().orElse(null));
        }
    }

    private static <T extends InetAddress> T address(Optional<byte[]> bytes, Class<T> type)
            throws CborDeserializationException {
        if (bytes.isEmpty())
            return null;
        try {
            return type.cast(InetAddress.getByAddress(bytes.get()));
        } catch (Exception ex) {
            throw new CborDeserializationException("Unable to convert byte[] to " + type.getSimpleName() + ": "
                    + HexUtil.encodeHexString(bytes.get()), ex);
        }
    }

    /**
     * Deserialize a PoolRegistration cert from cbor hex string
     * @param cborHex cbor hex string
     * @return PoolRegistration
     * @throws CborRuntimeException when deserialization fails
     */
    public static PoolRegistration deserialize(String cborHex) {
        DataItem dataItem = CborSerializationUtil.deserialize(HexUtil.decodeHexString(cborHex));
        try {
            return deserialize(dataItem);
        } catch (CborDeserializationException e) {
            throw new CborRuntimeException(e);
        }
    }

    /**
     * Get Bech32 pool id
     */
    @JsonIgnore
    public String getBech32PoolId() {
        Objects.requireNonNull(operator, "Operator cannot be null");
        return StakePoolId.fromHexPoolId(HexUtil.encodeHexString(operator)).getBech32PoolId();
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
                ", blsKey=" + blsKey +
                '}';
    }

}
