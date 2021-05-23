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
            //name is stored in hex in ledger. Try Hex decode first. If fails, try string.getBytes (used in mint transaction from client)
            if(name.startsWith("0x")) {
                assetNameBytes = HexUtil.decodeHexString(name.substring(2));
            } else {
                try {
                    assetNameBytes = HexUtil.decodeHexString(name); //If hex decoding fails, then try string.getbytes
                } catch (Exception e) {
                    assetNameBytes = name.getBytes(StandardCharsets.UTF_8);
                }
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
