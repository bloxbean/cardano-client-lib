package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.transaction.raw.RawBlsKey;
import com.bloxbean.cardano.client.util.serializers.ByteArrayToHexSerializer;
import com.bloxbean.cardano.client.util.serializers.HexToByteArrayDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The BLS key of a Dijkstra pool registration, {@code [bls_pubkey : bytes .size 96, bls_possession_proof : bytes .size
 * 48]}. Lengths are not checked: the ledger judges them.
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlsKey {
    /** The 96-byte public key. */
    @JsonSerialize(using = ByteArrayToHexSerializer.class)
    @JsonDeserialize(using = HexToByteArrayDeserializer.class)
    private byte[] publicKey;

    /** The 48-byte proof of possession of its private key. */
    @JsonSerialize(using = ByteArrayToHexSerializer.class)
    @JsonDeserialize(using = HexToByteArrayDeserializer.class)
    private byte[] proofOfPossession;

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @return {@code [public key, proof of possession]}
     */
    public Array serialize() {
        Array array = new Array();
        array.add(new ByteString(publicKey));
        array.add(new ByteString(proofOfPossession));
        return array;
    }

    static BlsKey from(RawBlsKey raw) {
        return new BlsKey(raw.publicKey(), raw.proofOfPossession());
    }
}
