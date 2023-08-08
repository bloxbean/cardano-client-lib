package com.bloxbean.cardano.client.transaction.model;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Utxo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(since = "0.5.0")
public abstract class TransactionRequest {
    protected Account sender;
    protected String receiver;
    protected BigInteger fee;

    //Optional - parameter for now. Can be used in future to add multiple witness accounts to a transaction
    protected List<Account> additionalWitnessAccounts;

    //Optional - Utxos to include to the transaction
    protected List<Utxo> utxosToInclude;

    //Datumhash - Required when receiver is a script
    protected String datumHash;
}
