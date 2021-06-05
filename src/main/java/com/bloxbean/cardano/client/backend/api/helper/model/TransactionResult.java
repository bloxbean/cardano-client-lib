package com.bloxbean.cardano.client.backend.api.helper.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResult {
    private byte[] signedTxn;
    private String transactionId;
}
