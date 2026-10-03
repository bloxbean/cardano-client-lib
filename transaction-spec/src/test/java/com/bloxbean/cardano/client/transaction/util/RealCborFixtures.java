package com.bloxbean.cardano.client.transaction.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;

/**
 * Real transactions and blocks committed under {@code src/test/resources/cbor}: mainnet Shelley to Conway, preprod and
 * preview, and the preprod trigger of ADR 0001 (tx {@code f90dce57…24c9} in block 5183974, a 5,383-level native script).
 */
final class RealCborFixtures {
    record Tx(String network, String era, long blockHeight, String txHash, byte[] cbor) {
        @Override
        public String toString() {
            return network + " " + era + " " + txHash;
        }
    }

    record Block(String network, String era, long blockHeight, String blockHash, List<String> txHashes, byte[] cbor) {
        @Override
        public String toString() {
            return network + " " + era + " block " + blockHeight;
        }
    }

    static final String TRIGGER_NATIVE_SCRIPT_HASH = "ff3efca65569f6b0b868a3d34abdb1ad8eccf745e0da71fa94fb4f18";
    static final int TRIGGER_NATIVE_SCRIPT_LEVELS = 5383;

    private RealCborFixtures() {
    }

    static List<Tx> txs() {
        List<Tx> txs = new ArrayList<>();
        for (JsonNode node : read("cbor/real-txs.json").get("txs"))
            txs.add(tx(node));
        return txs;
    }

    static Tx trigger() {
        return tx(read("cbor/preprod-trigger-tx.json"));
    }

    /**
     * @return the real transactions followed by the trigger transaction
     */
    static List<Tx> allTxs() {
        List<Tx> txs = txs();
        txs.add(trigger());
        return txs;
    }

    static List<Block> blocks() {
        List<Block> blocks = new ArrayList<>();
        for (JsonNode node : read("cbor/real-blocks.json").get("blocks")) {
            List<String> txHashes = new ArrayList<>();
            node.get("txHashes").forEach(hash -> txHashes.add(hash.asText()));
            blocks.add(new Block(node.get("network").asText(), node.get("era").asText(), node.get("blockHeight").asLong(),
                    node.get("blockHash").asText(), txHashes, decodeHexString(node.get("cbor").asText())));
        }
        return blocks;
    }

    private static Tx tx(JsonNode node) {
        return new Tx(node.get("network").asText(), node.get("era").asText(), node.get("blockHeight").asLong(),
                node.get("txHash").asText(), decodeHexString(node.get("cbor").asText()));
    }

    private static JsonNode read(String resource) {
        try (InputStream in = RealCborFixtures.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null)
                throw new IllegalStateException("missing test resource " + resource);
            return new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
