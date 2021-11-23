package com.bloxbean.cardano.client.transaction.model;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.model.Utxo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class TransactionRequest {
    protected Account sender;
    protected String receiver;
    protected BigInteger fee;

    //Optional - parameter for now. Can be used in future to add multiple witness accounts to a transaction
    private List<Account> additionalWitnessAccounts;

    //Optional - Utxos to include to the transaction
    private List<Utxo> utxosToInclude;

    //Datumhash - Required when receiver is a script
    private String datumHash;
}
