package com.bloxbean.cardano.client.transaction.model;

import com.bloxbean.cardano.client.account.Account;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * This class is used while minting a new native token
 */
public class MintTransaction {
    private Account sender;
    private String receiver;
    private BigInteger fee;
    private List<MultiAsset> mintAssets;

    @JsonIgnore
    private NativeScript policyScript;
    @JsonIgnore
    private SecretKey policyKey;
}
