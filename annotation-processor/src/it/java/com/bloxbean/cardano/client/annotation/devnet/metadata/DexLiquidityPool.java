package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@MetadataType(label = 1000)
public record DexLiquidityPool(
        @MetadataField(key = "pool_id", required = true) String poolId,
        @MetadataField(key = "token_a", required = true) TokenInfo tokenA,
        @MetadataField(key = "token_b", required = true) TokenInfo tokenB,
        @MetadataField(key = "reserve_a") BigInteger reserveA,
        @MetadataField(key = "reserve_b") BigInteger reserveB,
        @MetadataField(key = "total_lp") BigInteger totalLpTokens,
        PoolFeeConfig fees,
        @MetadataField(key = "last_action") LpAction lastAction,
        @MetadataField(key = "recent_actions") List<LpAction> recentActions,
        @MetadataField(key = "provider_shares") Map<String, BigInteger> providerShares,
        @MetadataField(adapter = EpochAdapter.class) Instant updatedAt,
        @MetadataField(key = "pool_datum", enc = MetadataFieldType.STRING_BASE64) byte[] poolDatum
) {}
