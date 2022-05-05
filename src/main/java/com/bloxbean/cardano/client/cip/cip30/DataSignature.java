package com.bloxbean.cardano.client.cip.cip30;

import com.bloxbean.cardano.client.cip.cip8.COSEKey;
import com.bloxbean.cardano.client.cip.cip8.COSESign1;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonFieldWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSignature implements JsonFieldWriter {
    //cbor COSESign1
    private String signature;
    //cbor COSEKey
    private String key;

    public COSESign1 getCOSESign1() {
        if (signature != null)
            return COSESign1.deserialize(HexUtil.decodeHexString(signature));
        else
            return null;
    }

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
