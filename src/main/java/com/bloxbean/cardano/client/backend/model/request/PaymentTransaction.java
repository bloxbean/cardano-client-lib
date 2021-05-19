package com.bloxbean.cardano.client.backend.model.request;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.transaction.model.MultiAsset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {
    private Account sender;
    private String receiver;
    private String unit;
    private BigInteger amount;
    private BigInteger fee;
    private List<MultiAsset> mintAssets;
}
