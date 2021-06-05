package com.bloxbean.cardano.client.transaction.model;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * This class is used while minting a new native token
 */
public class MintTransaction extends TransactionRequest {

    private List<MultiAsset> mintAssets;
    @JsonIgnore
    private NativeScript policyScript;
    @JsonIgnore
    private List<SecretKey> policyKeys;

    @Builder
    public MintTransaction(Account sender, String receiver, BigInteger fee, List<Account> additionalWitnessAccounts,
                           List<Utxo> utxosToInclude, List<MultiAsset> mintAssets, NativeScript policyScript, List<SecretKey> policyKeys) {
        super(sender, receiver, fee, additionalWitnessAccounts, utxosToInclude);
        this.mintAssets = mintAssets;
        this.policyScript = policyScript;
        this.policyKeys = policyKeys;
    }

}
