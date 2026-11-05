package com.bloxbean.cardano.client.tools.readerdiff;

import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.custom.EncodedKeyMap;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.transaction.raw.RawBlock;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDeregistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import com.bloxbean.cardano.yaci.core.model.Block;
import com.bloxbean.cardano.yaci.core.model.serializers.BlockSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalJsonTest {
    private final CanonicalJson json = new CanonicalJson();

    @Test
    void showsEveryFieldOfARealBlock() throws Exception {
        new Probes(); // returns Yaci's CBOR fields, as a diff does
        FieldCoverage coverage = new FieldCoverage();
        coverage.check(BlockSerializer.INSTANCE.deserialize(Fixtures.block()));
        assertEquals(List.of(), coverage.problems);
        for (Class<?> c : List.of(Block.class)) {
            for (var field : c.getDeclaredFields())
                assertTrue(coverage.fieldsSeen.contains(c.getName() + "." + field.getName()), field.getName());
        }
        assertTrue(coverage.fieldsSeen.contains("com.bloxbean.cardano.yaci.core.model.HeaderBody.blockHash"));
        assertTrue(coverage.fieldsSeen.contains("com.bloxbean.cardano.yaci.core.model.TransactionBody.inputs"));
    }

    @Test
    void showsEveryFieldOfRealTransactions() throws Exception {
        RawBlock block = RawBlock.of(Fixtures.block());
        FieldCoverage coverage = new FieldCoverage();
        for (int i = 0; i < block.txCount(); i++)
            coverage.check(Transaction.deserialize(block.txBytes(i)));
        assertEquals(List.of(), coverage.problems);
        assertTrue(coverage.fieldsSeen.contains(
                "com.bloxbean.cardano.client.transaction.spec.TransactionBody.fee"));
    }

    /** {@code -Pcorpus=<file>}: the same check over a dumped corpus, which reaches many more classes than one block. */
    @Test
    void showsEveryFieldOfACorpus() throws Exception {
        String corpus = System.getProperty("reader.diff.corpus");
        Assumptions.assumeTrue(corpus != null, "no -Pcorpus");
        new Probes();
        FieldCoverage coverage = new FieldCoverage();
        Blocks.read(Path.of(corpus), bytes -> {
            coverage.check(BlockSerializer.INSTANCE.deserialize(bytes));
            RawBlock block = RawBlock.of(bytes);
            for (int i = 0; i < block.txCount(); i++)
                coverage.check(Transaction.deserialize(block.txBytes(i)));
        });
        System.out.println(coverage.fieldsSeen.size() + " fields seen:\n" + String.join("\n", coverage.fieldsSeen));
        assertEquals(List.of(), coverage.problems);
    }

    /** {@code -Pdataset=<dir>/conway}: and over the dataset's models, for what a corpus rarely holds (governance). */
    @Test
    void showsEveryFieldOfTheDatasetsModels() throws Exception {
        String dataset = System.getProperty("reader.diff.dataset");
        Assumptions.assumeTrue(dataset != null, "no -Pdataset");
        new Probes();
        Map<String, Probes.Decoder> decoders = DatasetDecoders.all();
        FieldCoverage coverage = new FieldCoverage();
        List<Path> samples;
        try (Stream<Path> files = Files.walk(Path.of(dataset))) {
            samples = files.filter(f -> f.getFileName().toString().endsWith(".input.cbor")).toList();
        }
        for (Path sample : samples) {
            String rule = Path.of(dataset).relativize(sample).getName(0).toString();
            for (String name : List.of("ccl:" + rule, "yaci:" + rule)) {
                if (!decoders.containsKey(name))
                    continue;
                Object model;
                try {
                    model = decoders.get(name).decode(Files.readAllBytes(sample));
                } catch (Exception | StackOverflowError rejected) {
                    continue;
                }
                coverage.check(model);
            }
        }
        System.out.println(coverage.fieldsSeen.size() + " fields seen:\n" + String.join("\n", coverage.fieldsSeen));
        assertEquals(List.of(), coverage.problems);
    }

    @Test
    void ignoresTheModelsJacksonAnnotations() throws Exception {
        String text = json.write(new BytesPlutusData(new byte[]{1, 2})); // has @JsonSerialize(using = ...)
        assertTrue(text.contains("\"@class\":\"" + BytesPlutusData.class.getName() + "\""), text);
        assertTrue(text.contains("\"value\":\"0102\""), text);

        assertTrue(json.write(new Annotated()).contains("\"hidden\":\"seen\""));
    }

    private static final class Annotated {
        @JsonIgnore
        private final String hidden = "seen";
    }

    @Test
    void tellsApartClassesWithTheSameFields() throws Exception {
        StakeCredential credential = StakeCredential.fromKeyHash(new byte[28]);
        assertNotEquals(json.write(new StakeRegistration(credential)), json.write(new StakeDeregistration(credential)));
    }

    @Test
    void writesAClassWithNoFields() throws Exception {
        assertEquals("{\"@class\":\"" + Empty.class.getName() + "\"}", json.write(new Empty()));
    }

    private static final class Empty {
    }

    private record Key(int id, String name) {
    }

    private static final class Holder {
        private final Map<Object, String> map;
        private final Set<Object> set;

        Holder(Map<Object, String> map, Set<Object> set) {
            this.map = map;
            this.set = set;
        }
    }

    @Test
    void sortsWhatHasNoOrderAndKeepsWhatHas() throws Exception {
        Map<Object, String> hashed = new HashMap<>();
        Map<Object, String> linked = new LinkedHashMap<>();
        Map<Object, String> otherLinked = new LinkedHashMap<>();
        for (int key : List.of(3, 1, 2)) {
            hashed.put(key, "v");
            linked.put(key, "v");
        }
        for (int key : List.of(1, 2, 3))
            otherLinked.put(key, "v");

        String sorted = "{\"1\":\"v\",\"2\":\"v\",\"3\":\"v\"}";
        assertTrue(json.write(new Holder(hashed, Set.of())).contains(sorted));
        assertTrue(json.write(new Holder(otherLinked, Set.of())).contains(sorted));
        assertFalse(json.write(new Holder(linked, Set.of())).contains(sorted), "a LinkedHashMap's order is kept");

        assertEquals(json.write(new Holder(Map.of(), new HashSet<>(List.of("b", "a")))),
                json.write(new Holder(Map.of(), Set.of("a", "b"))));
        assertNotEquals(json.write(new Holder(Map.of(), new LinkedHashSet<>(List.of("b", "a")))),
                json.write(new Holder(Map.of(), new LinkedHashSet<>(List.of("a", "b")))));
    }

    @Test
    void writesObjectKeysAsJsonNotToString() throws Exception {
        String text = json.write(new Holder(Map.of(new Key(1, "x"), "v"), Set.of()));
        assertTrue(new ObjectMapper().readTree(text).path("map").fieldNames().next().contains("\"id\":1"), text);
    }

    @Test
    void writesByteArraysAsHexAndKeepsADecimalsScale() throws Exception {
        assertEquals("\"00ff\"", json.write(new byte[]{0, (byte) 0xff}));
        assertNotEquals(json.write(new BigDecimal("1.0")), json.write(new BigDecimal("1.00")));
    }

    /** The hash in a key of CCL's map depends on the class loader: it must not reach the JSON. */
    @Test
    void writesADataItemAsItsEncoding() throws Exception {
        EncodedKeyMap map = new EncodedKeyMap();
        map.put(new UnicodeString("b"), new UnsignedInteger(1));
        map.put(new UnicodeString("a"), new UnsignedInteger(2));
        assertEquals("\"a2616201616102\"", json.write(map));
    }

    @Test
    void writesAModelTooDeepForTheStack() throws Exception {
        List<Object> deep = List.of();
        for (int i = 0; i < 50_000; i++)
            deep = List.of(deep);
        assertTrue(json.write(deep).startsWith("[[["));
    }
}
