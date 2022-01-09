package com.bloxbean.cardano.client.transaction.model;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.model.Utxo;
import lombok.*;

import java.math.BigInteger;
import java.util.List;

/**
 * For payment transaction both in ADA (Lovelace) or Native tokens
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction extends TransactionRequest {
    private String unit;
    private BigInteger amount;

    @Builder
    public PaymentTransaction(Account sender, String receiver, BigInteger fee, List<Account> additionalWitnessAccounts,
                              List<Utxo> utxosToInclude, String unit, BigInteger amount, String datumHash) {
        super(sender, receiver, fee, additionalWitnessAccounts, utxosToInclude, datumHash);
        this.unit = unit;
        this.amount = amount;
    }

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
