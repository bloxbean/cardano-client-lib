package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.function.TxBuilder;

public class OutputMergers {

    /**
     * Function to merge outputs for an address into one output. This is useful when you have multiple outputs for same address.
     * This function will merge all outputs into one output and remove the old outputs. Only outputs
     * with given address, but no datumHash, inlineDatum and scriptRef will be merged.
     * <p>
     * Merging outputs after a wallet shaper ({@link com.bloxbean.cardano.client.function.walletshape.WalletShaper})
     * undoes the shaping; QuickTx logs a warning when both are used.
     * @param address
     * @return TxBuilder
     */
    public static TxBuilder mergeOutputsForAddress(String address) {
        return new OutputMerger(address);
    }
}
