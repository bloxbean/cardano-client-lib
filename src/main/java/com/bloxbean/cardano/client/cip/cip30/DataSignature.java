package com.bloxbean.cardano.client.cip.cip30;

import com.bloxbean.cardano.client.cip.cip8.COSEKey;
import com.bloxbean.cardano.client.cip.cip8.COSESign1;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonFieldWriter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class DataSignature implements JsonFieldWriter {

    //cbor COSESign1
    private String signature;
    //cbor COSEKey
    private String key;

    @JsonIgnore
    public COSESign1 getCOSESign1() {
        if (signature != null)
            return COSESign1.deserialize(HexUtil.decodeHexString(signature));
        else
            return null;
    }

    @JsonIgnore
    public COSEKey getCOSEKey() {
        if (key != null)
            return COSEKey.deserialize(HexUtil.decodeHexString(key));
        else
            return null;
    }

    public static DataSignature from(String json) throws JsonProcessingException {
        return mapper.readValue(json, DataSignature.class);
    }
}
