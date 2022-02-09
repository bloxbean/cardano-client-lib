package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

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
        if (name != null && !name.isEmpty()) {
            //Check if caller has provided a hex string as asset name
            if (name.startsWith("0x")) {
                assetNameBytes = HexUtil.decodeHexString(name.substring(2));
            } else {
                assetNameBytes = name.getBytes(StandardCharsets.UTF_8);
            }
        } else {
            assetNameBytes = new byte[0];
        }
        return assetNameBytes;
    }

    /**
     * Asset name as hex.
     * When comparing two assets, hex value of the name should be compared.
     * @return
     */
    @JsonIgnore
    public String getNameAsHex() {
        byte[] bytes = getNameAsBytes();
        if (bytes == null)
            return null;
        else
            return HexUtil.encodeHexString(bytes, true);
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

    /**
     * returns a new asset that is the sum of this and that (asset passed as parameter)
     * @param that
     * @return a new Asset as sum of this value and the one passed as parameter
     */
    public Asset plus(Asset that) {
        if (!Arrays.equals(getNameAsBytes(), that.getNameAsBytes())) {
            throw new IllegalArgumentException("Trying to add Assets with different name");
        }
        return Asset.builder().name(getName()).value(getValue().add(that.getValue())).build();
    }

    /**
     * returns a new asset that is a subtraction of this and that (asset passed as parameter)
     * @param that
     * @return a new Asset as subtract of this value and the one passed as parameter
     */
    public Asset minus(Asset that) {
        if (!Arrays.equals(getNameAsBytes(), that.getNameAsBytes())) {
            throw new IllegalArgumentException("Trying to add Assets with different name");
        }
        return Asset.builder().name(getName()).value(getValue().subtract(that.getValue())).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Asset asset = (Asset) o;
        return Arrays.equals(getNameAsBytes(), asset.getNameAsBytes()) && Objects.equals(value, asset.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }
}
