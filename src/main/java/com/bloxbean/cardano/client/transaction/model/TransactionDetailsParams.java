package com.bloxbean.cardano.client.transaction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.common.CardanoConstants.ONE_ADA;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDetailsParams {
    private long ttl;
    private long validityStartInterval;

    private BigInteger minLovelaceForMultiAsset = ONE_ADA.multiply(BigInteger.valueOf(2));
    private BigInteger minLovelaceInOuput = ONE_ADA;
}
