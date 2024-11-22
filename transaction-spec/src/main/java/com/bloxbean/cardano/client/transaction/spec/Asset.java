package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Asset {

    private String name;
    private BigInteger value;

    @JsonIgnore
    public byte[] getNameAsBytes() {
        return nameToBytes(name);
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
    public Asset add(Asset that) {
        if (!Arrays.equals(getNameAsBytes(), that.getNameAsBytes())) {
            throw new IllegalArgumentException("Trying to add Assets with different name");
        }
        return Asset.builder().name(getNameAsHex()).value(getValue().add(that.getValue())).build();
    }

    /**
     * Returns a new asset that is the sum of this asset and the provided asset.
     *
     * @deprecated
     * <p>Use {@link #add(Asset)} instead</p>
     *
     * @param that the asset to be added to this asset
     * @return a new Asset representing the sum of this asset and the provided asset
     */
    @Deprecated(since = "0.6.3")
    public Asset plus(Asset that) {
        return this.add(that);
    }

    /**
     * returns a new asset that is a subtraction of this and that (asset passed as parameter)
     * @param that
     * @return a new Asset as subtract of this value and the one passed as parameter
     */
    public Asset subtract(Asset that) {
        if (!Arrays.equals(getNameAsBytes(), that.getNameAsBytes())) {
            throw new IllegalArgumentException("Trying to add Assets with different name");
        }
        return Asset.builder().name(getName()).value(getValue().subtract(that.getValue())).build();
    }

    /**
     * Returns a new asset that is a subtraction of this asset and the provided asset.
     * @deprecated
     * <p>Use {@link #subtract(Asset)} instead</p>
     *
     * @param that the asset to be subtracted from this asset
     * @return a new Asset representing the difference between this asset and the provided asset
     */
    @Deprecated(since = "0.6.3")
    public Asset minus(Asset that) {
        return this.subtract(that);
    }

    public boolean hasName(String assetName) {
        byte[] assetNameBytes = nameToBytes(assetName);
        byte[] existingAssetNameBytes = nameToBytes(name);

        //check if both byte array are same
        return Arrays.equals(assetNameBytes, existingAssetNameBytes);
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
        return Objects.hash(Arrays.hashCode(getNameAsBytes()), value);
    }

    private static byte[] nameToBytes(String assetName) {
        byte[] assetNameBytes = null;
        if (assetName != null && !assetName.isEmpty()) {
            //Check if caller has provided a hex string as asset name
            if (assetName.startsWith("0x")) {
                try {
                    assetNameBytes = HexUtil.decodeHexString(assetName.substring(2));
                } catch (IllegalArgumentException e) {
                    // name is not actually a hex string
                    assetNameBytes = assetName.getBytes(StandardCharsets.UTF_8);
                }
            } else {
                assetNameBytes = assetName.getBytes(StandardCharsets.UTF_8);
            }
        } else {
            assetNameBytes = new byte[0];
        }
        return assetNameBytes;
    }
}
