package com.bloxbean.cardano.client.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Basic transaction information from the blockchain.
 * <p>
 * Contains essential details about a transaction's inclusion in the chain,
 * useful for tracking, confirmation monitoring, and general queries.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionInfo {
    private String txHash;
    private Long blockHeight;
    private String blockHash;
    private Long blockTime;
    private Long slot;
}
