package com.bloxbean.cardano.client.quicktx;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
class DepositRefundContext {
    private String address;
    private DepositRefundType depositType;
    private int count;

    public DepositRefundContext(String address, DepositRefundType depositType) {
        this.address = address;
        this.depositType = depositType;
        this.count = 1;
    }

}
