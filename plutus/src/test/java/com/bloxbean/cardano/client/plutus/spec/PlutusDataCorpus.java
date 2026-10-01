package com.bloxbean.cardano.client.plutus.spec;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;

/**
 * Every Plutus data item of the committed real transactions and blocks, as original bytes: witness datums, redeemer
 * data (array and map form) and inline datums.
 */
final class PlutusDataCorpus {
    record Datum(String source, byte[] cbor) {
        @Override
        public String toString() {
            return source;
        }
    }

    private PlutusDataCorpus() {
    }

    static List<Datum> all() {
        List<Datum> datums = new ArrayList<>();
        for (JsonNode tx : read("real-txs.json").get("txs"))
            addTx(datums, "tx " + tx.get("txHash").asText(), CborSpan.of(decodeHexString(tx.get("cbor").asText())));
        JsonNode trigger = read("preprod-trigger-tx.json");
        addTx(datums, "trigger", CborSpan.of(decodeHexString(trigger.get("cbor").asText())));
        addBlocks(datums, read("real-blocks.json"));
        JsonNode more = readOptional("corpus-blocks.json.gz");
        if (more != null)
            addBlocks(datums, more);
        return datums;
    }

    private static void addBlocks(List<Datum> datums, JsonNode file) {
        for (JsonNode block : file.get("blocks")) {
            String name = "block " + block.get("network").asText() + " " + block.get("blockHeight").asLong();
            List<CborSpan> parts = CborSpan.of(decodeHexString(block.get("cbor").asText())).get(1).items();
            List<CborSpan> bodies = parts.get(1).items();
            List<CborSpan> witnesses = parts.get(2).items();
            for (int i = 0; i < bodies.size(); i++)
                addParts(datums, name + " tx " + i, bodies.get(i), witnesses.get(i));
        }
    }

    private static void addTx(List<Datum> datums, String name, CborSpan tx) {
        addParts(datums, name, tx.get(0), tx.get(1));
    }

    private static void addParts(List<Datum> datums, String name, CborSpan body, CborSpan witnesses) {
        witnesses.field(4).ifPresent(field -> field.untagIf(258).items()
                .forEach(datum -> datums.add(new Datum(name + " witness datum", datum.bytes()))));
        Optional<CborSpan> redeemers = witnesses.field(5);
        if (redeemers.isPresent()) {
            CborSpan field = redeemers.get();
            if (field.majorType() == 4) {
                for (CborSpan redeemer : field.items())
                    datums.add(new Datum(name + " redeemer", redeemer.get(2).bytes()));
            } else {
                for (Map.Entry<CborSpan, CborSpan> redeemer : field.entries())
                    datums.add(new Datum(name + " redeemer", redeemer.getValue().get(0).bytes()));
            }
        }
        Optional<CborSpan> outputs = body.field(1);
        if (outputs.isPresent()) {
            for (CborSpan output : outputs.get().untagIf(258).items()) {
                if (output.majorType() != 5)
                    continue;
                Optional<CborSpan> datumOption = output.field(2);
                if (datumOption.isPresent() && datumOption.get().get(0).asLong() == 1)
                    datums.add(new Datum(name + " inline datum", datumOption.get().get(1).embedded().bytes()));
            }
        }
    }

    private static JsonNode read(String resource) {
        JsonNode node = readOptional(resource);
        if (node == null)
            throw new IllegalStateException("missing test resource " + resource);
        return node;
    }

    private static JsonNode readOptional(String resource) {
        try (InputStream in = PlutusDataCorpus.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null)
                return null;
            return new ObjectMapper().readTree(resource.endsWith(".gz") ? new GZIPInputStream(in) : in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
