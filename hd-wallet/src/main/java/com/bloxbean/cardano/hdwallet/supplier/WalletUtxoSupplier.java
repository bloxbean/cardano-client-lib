package com.bloxbean.cardano.hdwallet.supplier;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.hdwallet.model.WalletUtxo;

import java.util.List;

public interface WalletUtxoSupplier extends UtxoSupplier {

    /**
     * Returns all Utxos for provided wallets
     * @return
     */
    List<WalletUtxo> getAll();

    /**
     * Returns all UTXOs for a specific address m/1852'/1815'/{account}'/0/{index}
     * @param account
     * @param index
     * @return
     */
    List<WalletUtxo> getUtxosForAccountAndIndex(int account, int index);
}
