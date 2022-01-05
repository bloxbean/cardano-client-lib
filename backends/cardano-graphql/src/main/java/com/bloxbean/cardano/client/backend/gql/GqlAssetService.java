package com.bloxbean.cardano.client.backend.gql;

import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.gql.AssetQuery;
import okhttp3.OkHttpClient;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public class GqlAssetService extends BaseGqlService implements AssetService {

    public GqlAssetService(String gqlUrl) {
        super(gqlUrl);
    }

    public GqlAssetService(String gqlUrl, Map<String, String> headers) {
        super(gqlUrl, headers);
    }

    public GqlAssetService(String gqlUrl, OkHttpClient client) {
        super(gqlUrl, client);
    }

    @Override
    public Result<Asset> getAsset(String unit) {
        AssetQuery query = new AssetQuery(unit);
        AssetQuery.Data data = execute(query);
        if (data == null)
            return (Result<Asset>) Result.error("No asset found for assetId: " + unit);

        List<AssetQuery.Asset> assets = data.assets();
        if (assets.size() == 0)
            return (Result<Asset>) Result.error("No asset found for assetId: " + unit);

        AssetQuery.Asset gqlAsset = assets.get(0);
        Asset asset = new Asset();
        asset.setPolicyId(String.valueOf(gqlAsset.policyId()));
        asset.setAssetName(String.valueOf(gqlAsset.assetName()));
        asset.setFingerprint(String.valueOf(gqlAsset.fingerprint()));

        BigInteger quantity = BigInteger.ZERO;
        String initialMintHash;
        try {
            for (AssetQuery.TokenMint tokenMint : gqlAsset.tokenMints()) {
                quantity = quantity.add(new BigInteger(tokenMint.quantity()));
            }
            initialMintHash = String.valueOf(gqlAsset.tokenMints().get(0).transaction().hash());
            asset.setQuantity(quantity.toString());
            asset.setInitialMintTxHash(initialMintHash);
        } catch (Exception ignored) {
        }

        return processSuccessResult(asset);
    }

    @Override
    public Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page, OrderEnum order) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page, OrderEnum order) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page) {
        throw new UnsupportedOperationException();
    }
}
