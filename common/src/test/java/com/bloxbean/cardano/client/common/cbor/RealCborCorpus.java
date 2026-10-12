package com.bloxbean.cardano.client.common.cbor;

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

/**
 * The real transactions and blocks (mainnet, preprod, preview; every era) committed under
 * {@code transaction-spec/src/test/resources/cbor}, as raw CBOR.
 */
final class RealCborCorpus {
    record Item(String name, byte[] cbor) {
        @Override
        public String toString() {
            return name;
        }
    }

    private RealCborCorpus() {
    }

    static List<Item> all() {
        List<Item> items = new ArrayList<>();
        for (JsonNode tx : read("real-txs.json", false).get("txs"))
            items.add(new Item("tx " + tx.get("network").asText() + " " + tx.get("txHash").asText(), cbor(tx)));
        JsonNode trigger = read("preprod-trigger-tx.json", false);
        items.add(new Item("tx preprod trigger " + trigger.get("txHash").asText(), cbor(trigger)));
        addBlocks(items, read("real-blocks.json", false));
        JsonNode more = read("corpus-blocks.json.gz", true);
        if (more != null)
            addBlocks(items, more);
        return items;
    }

    /**
     * Whether the item nests more than 1,000 levels deep. The recursive code that the differential tests compare with
     * (cbor-java, the old encoder and models) may or may not overflow the stack on such input, depending on the JIT, so
     * it is no oracle there; the depth tests cover such input.
     */
    static boolean tooDeepForRecursiveCode(byte[] cbor) {
        return anyItem(cbor, (span, depth) -> depth > 1_000);
    }

    /**
     * @return whether the item holds an indefinite-length (chunked) byte or text string
     */
    static boolean hasChunkedStrings(byte[] cbor) {
        return anyItem(cbor, (span, depth) -> (span.majorType() == 2 || span.majorType() == 3) && span.isIndefinite());
    }

    // Visits every item, iteratively, with its nesting depth, until the test holds.
    private static boolean anyItem(byte[] cbor, java.util.function.BiPredicate<CborSpan, Integer> test) {
        Deque<CborSpan> spans = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        spans.push(CborSpan.of(cbor));
        depths.push(1);
        while (!spans.isEmpty()) {
            CborSpan span = spans.pop();
            int depth = depths.pop();
            while (span.tag() != -1)
                span = span.untag();
            if (test.test(span, depth))
                return true;
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

    private static void addBlocks(List<Item> items, JsonNode file) {
        for (JsonNode block : file.get("blocks"))
            items.add(new Item("block " + block.get("network").asText() + " " + block.get("era").asText() + " "
                    + block.get("blockHeight").asLong(), cbor(block)));
    }

    private static byte[] cbor(JsonNode node) {
        return decodeHexString(node.get("cbor").asText());
    }

    private static JsonNode read(String resource, boolean optional) {
        try (InputStream in = RealCborCorpus.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                if (optional)
                    return null;
                throw new IllegalStateException("missing test resource " + resource);
            }
            return new ObjectMapper().readTree(resource.endsWith(".gz") ? new GZIPInputStream(in) : in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
