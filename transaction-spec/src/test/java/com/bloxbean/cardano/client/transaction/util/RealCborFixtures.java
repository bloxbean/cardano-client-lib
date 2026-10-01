package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;

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

    /**
     * @return the committed blocks: one per era, the trigger block, and the larger corpus when present
     */
    static List<Block> blocks() {
        List<Block> blocks = new ArrayList<>();
        addBlocks(blocks, read("cbor/real-blocks.json"));
        JsonNode corpus = readOptional("cbor/corpus-blocks.json.gz");
        if (corpus != null)
            addBlocks(blocks, corpus);
        return blocks;
    }

    /**
     * Every transaction of every block, reassembled from the block's slices as it was submitted:
     * {@code [body, witnesses, isValid, aux / null]} from Alonzo, {@code [body, witnesses, aux / null]} before.
     */
    static List<Tx> blockTxs() {
        List<Tx> txs = new ArrayList<>();
        for (Block block : blocks()) {
            CborSpan envelope = CborSpan.of(block.cbor());
            int era = (int) envelope.get(0).asLong();
            List<CborSpan> parts = envelope.get(1).items();
            List<CborSpan> bodies = parts.get(1).items();
            List<CborSpan> witnesses = parts.get(2).items();
            java.util.Map<Long, CborSpan> aux = new java.util.HashMap<>();
            parts.get(3).entries().forEach(e -> aux.put(e.getKey().asLong(), e.getValue()));
            java.util.Set<Long> invalid = new java.util.HashSet<>();
            if (era >= 5)
                parts.get(4).items().forEach(index -> invalid.add(index.asLong()));
            for (int i = 0; i < bodies.size(); i++) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                out.write(era >= 5 ? 0x84 : 0x83);
                out.writeBytes(bodies.get(i).bytes());
                out.writeBytes(witnesses.get(i).bytes());
                if (era >= 5)
                    out.write(invalid.contains((long) i) ? 0xf4 : 0xf5);
                out.writeBytes(aux.containsKey((long) i) ? aux.get((long) i).bytes() : new byte[]{(byte) 0xf6});
                String txHash = encodeHexString(Blake2bUtil.blake2bHash256(bodies.get(i).bytes()));
                txs.add(new Tx(block.network(), block.era(), block.blockHeight(), txHash, out.toByteArray()));
            }
        }
        return txs;
    }

    /**
     * Whether the item nests more than 1,000 levels deep. The recursive code that the differential tests compare with
     * (cbor-java, the old encoder and models) may or may not overflow the stack on such input, depending on the JIT, so
     * it is no oracle there; the depth tests cover such input.
     */
    static boolean tooDeepForRecursiveCode(byte[] cbor) {
        Deque<CborSpan> spans = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        spans.push(CborSpan.of(cbor));
        depths.push(1);
        while (!spans.isEmpty()) {
            CborSpan span = spans.pop();
            int depth = depths.pop();
            if (depth > 1_000)
                return true;
            while (span.tag() != -1)
                span = span.untag();
            List<CborSpan> children = new ArrayList<>();
            if (span.majorType() == 4) {
                children.addAll(span.items());
            } else if (span.majorType() == 5) {
                span.entries().forEach(entry -> {
                    children.add(entry.getKey());
                    children.add(entry.getValue());
                });
            }
            for (CborSpan child : children) {
                spans.push(child);
                depths.push(depth + 1);
            }
        }
        return false;
    }

    private static void addBlocks(List<Block> blocks, JsonNode file) {
        for (JsonNode node : file.get("blocks")) {
            List<String> txHashes = new ArrayList<>();
            node.get("txHashes").forEach(hash -> txHashes.add(hash.asText()));
            blocks.add(new Block(node.get("network").asText(), node.get("era").asText(), node.get("blockHeight").asLong(),
                    node.get("blockHash").asText(), txHashes, decodeHexString(node.get("cbor").asText())));
        }
    }

    private static Tx tx(JsonNode node) {
        return new Tx(node.get("network").asText(), node.get("era").asText(), node.get("blockHeight").asLong(),
                node.get("txHash").asText(), decodeHexString(node.get("cbor").asText()));
    }

    private static JsonNode read(String resource) {
        JsonNode node = readOptional(resource);
        if (node == null)
            throw new IllegalStateException("missing test resource " + resource);
        return node;
    }

    private static JsonNode readOptional(String resource) {
        try (InputStream in = RealCborFixtures.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null)
                return null;
            return new ObjectMapper().readTree(resource.endsWith(".gz") ? new GZIPInputStream(in) : in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
