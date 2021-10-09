package com.bloxbean.cardano.client.transaction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDetailsParams {
    private long ttl;
    private long validityStartInterval;
}
