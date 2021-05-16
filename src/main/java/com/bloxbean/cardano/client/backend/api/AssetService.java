package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.Result;

public interface AssetService {
    /**
     *
     * @param unit Concatenation of the policy_id and hex-encoded asset_name
     * @return
     * @throws ApiException
     */
    public Result<Asset> getAsset(String unit) throws ApiException;
}
