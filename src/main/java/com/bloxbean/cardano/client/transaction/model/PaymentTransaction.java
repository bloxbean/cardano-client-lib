package com.bloxbean.cardano.client.transaction.model;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
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
/**
 * For payment transaction both in ADA (Lovelace) or Native tokens
 */
public class PaymentTransaction {
    private Account sender;
    private String receiver;
    private String unit;
    private BigInteger amount;
    private BigInteger fee;
    private List<MultiAsset> mintAssets;

    //Optional parameter for now. Can be used in future to add multiple witness accounts to a transaction
    private List<Account> additionalWitnessAccounts;

    public BigInteger getFee() {
        if(fee != null)
            return fee;
        else
            return BigInteger.ZERO;
    }

    public BigInteger getAmount() {
        if(amount != null)
            return amount;
        else
            return BigInteger.ZERO;
    }
}
