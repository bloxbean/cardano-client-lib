package com.bloxbean.cardano.client.transaction.util;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RealCborFixtures} for tests in other packages.
 */
public final class RealCborFixturesAccess {
    public record Tx(String name, String txHash, byte[] cbor) {
    }

    private RealCborFixturesAccess() {
    }

    /**
     * @return the committed transactions (with the trigger) and the transactions reassembled from the committed blocks
     */
    public static List<Tx> allTxsAndBlockTxs() {
        List<Tx> txs = new ArrayList<>();
        RealCborFixtures.allTxs().forEach(tx -> txs.add(new Tx(tx.toString(), tx.txHash(), tx.cbor())));
        RealCborFixtures.blockTxs().forEach(tx -> txs.add(new Tx("block " + tx, tx.txHash(), tx.cbor())));
        return txs;
    }
}
