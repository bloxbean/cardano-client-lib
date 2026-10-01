package com.bloxbean.cardano.client.transaction.util;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RealCborFixtures} for tests in other packages.
 */
public final class RealCborFixturesAccess {
    public record Tx(String name, String txHash, byte[] cbor) {
    }

    public static final String TRIGGER_NATIVE_SCRIPT_HASH = RealCborFixtures.TRIGGER_NATIVE_SCRIPT_HASH;
    public static final int TRIGGER_NATIVE_SCRIPT_LEVELS = RealCborFixtures.TRIGGER_NATIVE_SCRIPT_LEVELS;

    private RealCborFixturesAccess() {
    }

    /**
     * @return the preprod trigger transaction of ADR 0001, with its 5,383-level witness native script
     */
    public static Tx trigger() {
        RealCborFixtures.Tx tx = RealCborFixtures.trigger();
        return new Tx(tx.toString(), tx.txHash(), tx.cbor());
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
