package com.bloxbean.cardano.client.transaction.model;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.math.BigInteger;
import java.util.List;

/**
 * This class is used while minting a new native token
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MintTransaction extends TransactionRequest {

    private List<MultiAsset> mintAssets;
    @JsonIgnore
    private Policy policy;

    @Builder
    public MintTransaction(Account sender, String receiver, BigInteger fee, List<Account> additionalWitnessAccounts,
                           List<Utxo> utxosToInclude, String datumHash, List<MultiAsset> mintAssets,
                           @Deprecated NativeScript policyScript,
                           @Deprecated List<SecretKey> policyKeys, Policy policy) {
        super(sender, receiver, fee, additionalWitnessAccounts, utxosToInclude, datumHash);
        this.mintAssets = mintAssets;
        if (policy != null) {
            this.policy = policy;
        } else {
            this.policy = new Policy(policyScript, policyKeys);
        }
    }
}
