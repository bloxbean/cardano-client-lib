package com.bloxbean.cardano.hdwallet.utxosupplier;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.hdwallet.Wallet;

import java.util.List;

public interface WalletUtxoSupplier extends UtxoSupplier {

    /**
     * Returns all Utxos for provided wallets
     * @param wallet
     * @return
     */
    List<Utxo> getAll(Wallet wallet);

    /**
     * Returns all UTXOs for a specific address of a wallet m/1852'/1815'/{account}'/0/{index}
     * @param wallet
     * @param account
     * @param index
     * @return
     */
    List<Utxo> getUtxosForAccountAndIndex(Wallet wallet, int account, int index);

    /**
     * Returns all UTXOs for a specific address m/1852'/1815'/{account}'/0/{index}
     * @param account
     * @param index
     * @return
     */
    List<Utxo> getUtxosForAccountAndIndex(int account, int index);
}
