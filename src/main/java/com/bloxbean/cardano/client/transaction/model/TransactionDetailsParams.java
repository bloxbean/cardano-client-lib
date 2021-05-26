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

    //Protocol params
    private BigInteger minUtxoValue;

    public BigInteger getMinUtxoValue() {
        if(minUtxoValue != null && !minUtxoValue.equals(BigInteger.ZERO))
            return minUtxoValue;
        else
            return ONE_ADA;
    }
}
