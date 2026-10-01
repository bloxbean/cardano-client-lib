package com.bloxbean.cardano.client.transaction.util;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RealCborFixtures} for tests in other packages.
 */
public final class RealCborFixturesAccess {
    public record Tx(String name, String network, String era, String txHash, byte[] cbor) {
    }

    public record Block(String name, String network, String era, String blockHash, List<String> txHashes, byte[] cbor) {
    }

    public static final String TRIGGER_NATIVE_SCRIPT_HASH = RealCborFixtures.TRIGGER_NATIVE_SCRIPT_HASH;
    public static final int TRIGGER_NATIVE_SCRIPT_LEVELS = RealCborFixtures.TRIGGER_NATIVE_SCRIPT_LEVELS;

    private RealCborFixturesAccess() {
    }

    /**
     * @return the preprod trigger transaction of ADR 0001, with its 5,383-level witness native script
     */
    public static Tx trigger() {
        return tx(RealCborFixtures.trigger(), "");
    }

    /**
     * @return the committed transactions (with the trigger) and the transactions reassembled from the committed blocks
     */
    public static List<Tx> allTxsAndBlockTxs() {
        List<Tx> txs = new ArrayList<>();
        RealCborFixtures.allTxs().forEach(tx -> txs.add(tx(tx, "")));
        RealCborFixtures.blockTxs().forEach(tx -> txs.add(tx(tx, "block ")));
        return txs;
    }

    /**
     * @return the committed blocks, each in its {@code [era, block]} envelope
     */
    public static List<Block> blocks() {
        List<Block> blocks = new ArrayList<>();
        RealCborFixtures.blocks().forEach(block -> blocks.add(new Block(block.toString(), block.network(), block.era(),
                block.blockHash(), block.txHashes(), block.cbor())));
        return blocks;
    }

    private static Tx tx(RealCborFixtures.Tx tx, String prefix) {
        return new Tx(prefix + tx, tx.network(), tx.era(), tx.txHash(), tx.cbor());
    }
}
