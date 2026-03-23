package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@NoArgsConstructor
@AllArgsConstructor
@MetadataType
public class PoolFeeConfig {
    @MetadataField(key = "swap_fee") BigInteger swapFee;
    @MetadataField(key = "protocol_fee") BigInteger protocolFee;
}
