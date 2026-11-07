package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An entry of a {@code multiasset} map, {@code policy_id => {+ asset_name => quantity}}, of a value or of a mint.
 *
 * @param policyId the policy id's bytes, of any length
 * @param assets   the assets, one per name in the order of the names' first entries
 */
public record RawMultiAsset(byte[] policyId, List<Asset> assets) {
    /** The {@code multiasset} of a value. */
    static final RecordSpec VALUE = table("multiasset", FieldType.UINT, FieldType.POSITIVE_COIN);
    /** The {@code mint} rule. */
    static final RecordSpec MINT = table("mint", FieldType.INT64, FieldType.NONZERO_INT64);

    // Keyed by policy, then by asset name; a quantity has one type to Babbage and another from Conway.
    private static RecordSpec table(String name, FieldType toBabbage, FieldType fromConway) {
        RecordSpec assets = RecordSpec.entries(name + " assets", FieldType.ASSET_NAME,
                FieldSpec.of(0, toBabbage, false, Era.Mary, Era.Babbage),
                FieldSpec.of(0, fromConway, false, Era.Conway, null));
        return RecordSpec.entries(name, FieldType.HASH28,
                FieldSpec.of(0, FieldType.NONEMPTY_MAP, false, Era.Mary, null, assets));
    }

    /**
     * @param name     the asset name's bytes, of any length
     * @param quantity the quantity: an integer or a bignum, or for a view read as the models read one, null for an item
     *                 that is no number
     */
    public record Asset(byte[] name, BigInteger quantity) {
    }

    /**
     * Reads one entry as the models read it, see {@link RawValue#lenient(CborSpan)}.
     *
     * @param policyId the policy id as encoded
     * @param assets   the map of its assets as encoded
     * @return the view
     * @throws CborRuntimeException if the policy id or an asset name is not a byte string, or the assets not a map
     */
    public static RawMultiAsset lenient(CborSpan policyId, CborSpan assets) {
        return read(policyId, assets, true);
    }

    // One entry per policy and per asset name: see RawItems.distinct.
    static List<RawMultiAsset> read(CborSpan map, boolean lenient) {
        List<Map.Entry<CborSpan, CborSpan>> entries = RawItems.distinct(map, "multiasset map");
        List<RawMultiAsset> multiAssets = new ArrayList<>(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries)
            multiAssets.add(read(entry.getKey(), entry.getValue(), lenient));
        return multiAssets;
    }

    private static RawMultiAsset read(CborSpan policyId, CborSpan assets, boolean lenient) {
        List<Map.Entry<CborSpan, CborSpan>> entries = RawItems.distinct(assets, "map of assets");
        List<Asset> read = new ArrayList<>(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries)
            read.add(new Asset(RawItems.bytes(entry.getKey()), RawItems.integer(entry.getValue(), lenient)));
        return new RawMultiAsset(RawItems.bytes(policyId), read);
    }
}
