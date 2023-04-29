package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.crypto.Bech32;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.Getter;

@Getter
public class StakePoolId {
    private byte[] poolKeyHash;

    public StakePoolId(byte[] poolKeyHash) {
        this.poolKeyHash = poolKeyHash;
    }

    public static StakePoolId fromHexPoolId(String poolId) {
        byte[] poolIdBytes = HexUtil.decodeHexString(poolId);
        return new StakePoolId(poolIdBytes);
    }

    public static StakePoolId fromBech32PoolId(String poolId) {
        byte[] poolIdBytes = Bech32.decode(poolId).data;
        return new StakePoolId(poolIdBytes);
    }
}
