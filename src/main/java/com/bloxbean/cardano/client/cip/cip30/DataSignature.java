package com.bloxbean.cardano.client.cip.cip30;

import com.bloxbean.cardano.client.cip.cip8.COSEKey;
import com.bloxbean.cardano.client.cip.cip8.COSESign1;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonFieldWriter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.*;
import lombok.experimental.Accessors;

import static com.bloxbean.cardano.client.cip.cip30.CIP30Constant.*;

@Data
@Accessors(fluent = true)
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class DataSignature implements JsonFieldWriter {

    //cbor COSESign1
    private String signature;
    //cbor COSEKey
    private String key;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @Setter(AccessLevel.NONE)
    private COSESign1 coseSign1;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @Setter(AccessLevel.NONE)
    private COSEKey coseKey;

    public DataSignature(@NonNull String signature, @NonNull String key) {
        this.signature(signature);
        this.key(key);
    }

    public DataSignature signature(@NonNull String signature) {
        this.signature = signature;
        this.coseSign1 = COSESign1.deserialize(HexUtil.decodeHexString(signature));

        return this;
    }

    public DataSignature key(@NonNull String key) {
        this.key = key;
        this.coseKey = COSEKey.deserialize(HexUtil.decodeHexString(key));

        return this;
    }

    /**
     * Get bytes of the address set in "address" header of COSE_Sign1
     * @return address bytes
     */
    public byte[] address() {
        if (coseSign1 != null)
            return coseSign1.headers()._protected().getAsHeaderMap().otherHeaderAsBytes(ADDRESS_KEY);
        else
            return null;
    }

    /**
     * Get crv header of COSE_Key
     * @return crv
     */
    public Integer crv() {
        if (coseKey != null)
            return (int)coseKey.otherHeaderAsLong(CRV_KEY);
        else
            return null;
    }

    /**
     * Get x header/public key bytes of COSE_Key
     * @return public key bytes of the key used to sign the Sig_Structure
     */
    public byte[] x() {
        if (coseKey != null)
            return coseKey.otherHeaderAsBytes(X_KEY);
        else
            return null;
    }

    public static DataSignature from(String json) throws JsonProcessingException {
        return mapper.readValue(json, DataSignature.class);
    }
}
