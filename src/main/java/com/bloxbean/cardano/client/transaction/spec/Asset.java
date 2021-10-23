package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Asset {
    private String name;
    private BigInteger value;

    @JsonIgnore
    public byte[] getNameAsBytes() {
        byte[] assetNameBytes = null;
        if(name != null && !name.isEmpty()) {
            //Check if caller has provided a hex string as asset name
            if(name.startsWith("0x")) {
                assetNameBytes = HexUtil.decodeHexString(name.substring(2));
            } else {
                assetNameBytes = name.getBytes(StandardCharsets.UTF_8);
            }
        } else {
            assetNameBytes = new byte[0];
        }
        return assetNameBytes;
    }

    @Override
    public String toString() {
        try {
            return "Asset{" +
                    "name=" + name +
                    ", value=" + value +
                    '}';
        } catch (Exception e) {
            return "Asset { Error : " + e.getMessage() + " }";
        }
    }
}
