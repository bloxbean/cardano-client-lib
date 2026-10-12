package com.bloxbean.cardano.client.metadata.cbor;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;

/**
 * The metadata of the committed real transactions and blocks, as original bytes, from every aux data shape: the Shelley
 * metadata map, Allegra/Mary {@code [metadata, scripts]} and Alonzo tag 259 {@code {0: metadata, ...}}.
 */
final class MetadataCorpus {
    record Item(String source, byte[] cbor) {
        @Override
        public String toString() {
            return source;
        }
    }

    private MetadataCorpus() {
    }

    static List<Item> all() {
        List<Item> items = new ArrayList<>();
        for (JsonNode tx : read("real-txs.json").get("txs")) {
            List<CborSpan> parts = CborSpan.of(decodeHexString(tx.get("cbor").asText())).items();
            add(items, "tx " + tx.get("txHash").asText(), parts.get(parts.size() - 1));
        }
        addBlocks(items, read("real-blocks.json"));
        JsonNode more = readOptional("corpus-blocks.json.gz");
        if (more != null)
            addBlocks(items, more);
        return items;
    }

    private static void addBlocks(List<Item> items, JsonNode file) {
        for (JsonNode block : file.get("blocks")) {
            String name = "block " + block.get("network").asText() + " " + block.get("blockHeight").asLong();
            CborSpan aux = CborSpan.of(decodeHexString(block.get("cbor").asText())).get(1).get(3);
            for (Map.Entry<CborSpan, CborSpan> entry : aux.entries())
                add(items, name + " tx " + entry.getKey().asLong(), entry.getValue());
        }
    }

    private static void add(List<Item> items, String name, CborSpan aux) {
        CborSpan metadata = null;
        if (aux.majorType() == 5 && aux.tag() == 259)
            metadata = aux.untag().field(0).orElse(null);
        else if (aux.majorType() == 5)
            metadata = aux;
        else if (aux.majorType() == 4)
            metadata = aux.get(0);
        if (metadata != null)
            items.add(new Item(name, metadata.bytes()));
    }

    private static JsonNode read(String resource) {
        JsonNode node = readOptional(resource);
        if (node == null)
            throw new IllegalStateException("missing test resource " + resource);
        return node;
    }

    private static JsonNode readOptional(String resource) {
        try (InputStream in = MetadataCorpus.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null)
                return null;
            return new ObjectMapper().readTree(resource.endsWith(".gz") ? new GZIPInputStream(in) : in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
