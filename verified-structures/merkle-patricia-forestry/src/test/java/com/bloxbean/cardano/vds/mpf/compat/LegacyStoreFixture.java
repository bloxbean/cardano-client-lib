package com.bloxbean.cardano.vds.mpf.compat;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.internal.NodeRefParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Backend-neutral dump of MPF tries persisted by CCL 0.8.0-pre6 (master 644e717b).
 *
 * <p>The committed fixture {@code /mpf-compat/legacy-store-v0.8.0-pre6.json} was generated before the
 * issue #715 proof fixes. It holds every node reachable from each scenario's root (store key to encoded node),
 * the root and the expected key/value entries. Tests load it into a store and check that the trie still opens
 * with the same root and values, and that the current code still produces byte-identical nodes.</p>
 */
public final class LegacyStoreFixture {

    public static final String RESOURCE = "/mpf-compat/legacy-store-v0.8.0-pre6.json";

    static final String[][] FRUITS = {
            {"apple[uid: 58]", "🍎"}, {"apricot[uid: 0]", "🤷"}, {"banana[uid: 218]", "🍌"},
            {"blueberry[uid: 0]", "🫐"}, {"cherry[uid: 0]", "🍒"}, {"coconut[uid: 0]", "🥥"},
            {"cranberry[uid: 0]", "🤷"}, {"fig[uid: 68267]", "🤷"}, {"grapefruit[uid: 0]", "🤷"},
            {"grapes[uid: 0]", "🍇"}, {"guava[uid: 344]", "🤷"}, {"kiwi[uid: 0]", "🥝"},
            {"kumquat[uid: 0]", "🤷"}, {"lemon[uid: 0]", "🍋"}, {"lime[uid: 0]", "🤷"},
            {"mango[uid: 0]", "🥭"}, {"orange[uid: 0]", "🍊"}, {"papaya[uid: 0]", "🤷"},
            {"passionfruit[uid: 0]", "🤷"}, {"peach[uid: 0]", "🍑"}, {"pear[uid: 0]", "🍐"},
            {"pineapple[uid: 12577]", "🍍"}, {"plum[uid: 15492]", "🤷"}, {"pomegranate[uid: 0]", "🤷"},
            {"raspberry[uid: 0]", "🤷"}, {"strawberry[uid: 2532]", "🍓"}, {"tangerine[uid: 11]", "🍊"},
            {"tomato[uid: 83468]", "🍅"}, {"watermelon[uid: 0]", "🍉"}, {"yuzu[uid: 0]", "🤷"}};

    /** One persisted trie. */
    public static final class Scenario {
        public final String name;
        public final String hash;
        public final byte[] root;
        public final LinkedHashMap<String, byte[]> nodes;   // hex store key -> encoded node
        public final LinkedHashMap<String, byte[]> entries; // hex original key -> value

        Scenario(String name, String hash, byte[] root, LinkedHashMap<String, byte[]> nodes,
                 LinkedHashMap<String, byte[]> entries) {
            this.name = name;
            this.hash = hash;
            this.root = root;
            this.nodes = nodes;
            this.entries = entries;
        }

        public HashFunction hashFunction() {
            return LegacyStoreFixture.hashFunction(hash);
        }

        /** Copies every node into {@code store}. */
        public void loadInto(NodeStore store) {
            for (Map.Entry<String, byte[]> e : nodes.entrySet()) store.put(Bytes.fromHex(e.getKey()), e.getValue());
        }
    }

    private LegacyStoreFixture() {
    }

    public static HashFunction hashFunction(String name) {
        switch (name) {
            case "blake2b-256":
                return Blake2b256::digest;
            case "sha-256":
                return LegacyStoreFixture::sha256;
            default:
                throw new IllegalArgumentException("unknown hash " + name);
        }
    }

    private static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ building (current code)

    /** Builds the scenarios with the current code into in-memory stores. */
    public static List<Scenario> build() {
        List<Scenario> out = new ArrayList<>();

        LinkedHashMap<String, String> fruits = new LinkedHashMap<>();
        for (String[] f : FRUITS) fruits.put(f[0], f[1]);
        out.add(buildFromModel("fruits", "blake2b-256", strings(fruits), null));

        Random rnd = new Random(715_001L);
        LinkedHashMap<byte[], byte[]> random = new LinkedHashMap<>();
        for (int i = 0; i < 300; i++) random.put(randomBytes(rnd, 1 + rnd.nextInt(40)), randomBytes(rnd, 1 + rnd.nextInt(64)));
        out.add(buildFromModel("random-300", "blake2b-256", random, null));

        out.add(buildDeleteHeavy());

        Random rnd2 = new Random(715_003L);
        LinkedHashMap<byte[], byte[]> sha = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) sha.put(("sha-key-" + i).getBytes(StandardCharsets.UTF_8), randomBytes(rnd2, 8));
        out.add(buildFromModel("sha256-100", "sha-256", sha, null));
        return out;
    }

    private static Scenario buildDeleteHeavy() {
        Random rnd = new Random(715_002L);
        MapStore store = new MapStore();
        MpfTrie trie = new MpfTrie(store);
        LinkedHashMap<String, byte[]> model = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            String k = "dh-" + i;
            byte[] v = randomBytes(rnd, 1 + rnd.nextInt(32));
            trie.put(k.getBytes(StandardCharsets.UTF_8), v);
            model.put(k, v);
            keys.add(k);
        }
        Collections.shuffle(keys, rnd);
        for (int i = 0; i < 250; i++) {
            trie.delete(keys.get(i).getBytes(StandardCharsets.UTF_8));
            model.remove(keys.get(i));
        }
        for (int i = 250; i < 300; i++) {
            byte[] v = randomBytes(rnd, 1 + rnd.nextInt(32));
            trie.put(keys.get(i).getBytes(StandardCharsets.UTF_8), v);
            model.put(keys.get(i), v);
        }
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        model.forEach((k, v) -> entries.put(Bytes.toHex(k.getBytes(StandardCharsets.UTF_8)), v));
        return new Scenario("delete-heavy", "blake2b-256", trie.getRootHash(), reachable(store, trie.getRootHash()), entries);
    }

    private static Scenario buildFromModel(String name, String hash, LinkedHashMap<byte[], byte[]> model, Void unused) {
        MapStore store = new MapStore();
        MpfTrie trie = new MpfTrie(store, hashFunction(hash));
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        for (Map.Entry<byte[], byte[]> e : model.entrySet()) {
            trie.put(e.getKey(), e.getValue());
            entries.put(Bytes.toHex(e.getKey()), e.getValue());
        }
        return new Scenario(name, hash, trie.getRootHash(), reachable(store, trie.getRootHash()), entries);
    }

    private static LinkedHashMap<byte[], byte[]> strings(Map<String, String> m) {
        LinkedHashMap<byte[], byte[]> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k.getBytes(StandardCharsets.UTF_8), v.getBytes(StandardCharsets.UTF_8)));
        return out;
    }

    private static byte[] randomBytes(Random rnd, int n) {
        byte[] b = new byte[n];
        rnd.nextBytes(b);
        return b;
    }

    /** All nodes reachable from {@code root}, sorted by store key for a stable fixture. */
    static LinkedHashMap<String, byte[]> reachable(NodeStore store, byte[] root) {
        TreeMap<String, byte[]> seen = new TreeMap<>();
        Deque<byte[]> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            byte[] h = q.removeFirst();
            String hex = Bytes.toHex(h);
            if (seen.containsKey(hex)) continue;
            byte[] enc = store.get(h);
            if (enc == null) throw new IllegalStateException("dangling node " + hex);
            seen.put(hex, enc);
            q.addAll(NodeRefParser.childRefs(enc));
        }
        return new LinkedHashMap<>(seen);
    }

    // ------------------------------------------------------------------ JSON

    public static String toJson(List<Scenario> scenarios) throws IOException {
        ObjectMapper om = new ObjectMapper();
        ObjectNode rootNode = om.createObjectNode();
        rootNode.put("format", "ccl-mpf-node-dump-v1");
        rootNode.put("generatedBy", "cardano-client-lib 0.8.0-pre6 (master 644e717b), before the issue #715 fixes");
        ArrayNode arr = rootNode.putArray("scenarios");
        for (Scenario s : scenarios) {
            ObjectNode o = arr.addObject();
            o.put("name", s.name);
            o.put("hash", s.hash);
            o.put("root", Bytes.toHex(s.root));
            ObjectNode n = o.putObject("nodes");
            s.nodes.forEach((k, v) -> n.put(k, Bytes.toHex(v)));
            ObjectNode e = o.putObject("entries");
            s.entries.forEach((k, v) -> e.put(k, Bytes.toHex(v)));
        }
        return om.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode) + "\n";
    }

    public static List<Scenario> load() throws IOException {
        try (InputStream in = LegacyStoreFixture.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing resource " + RESOURCE);
            JsonNode rootNode = new ObjectMapper().readTree(in);
            List<Scenario> out = new ArrayList<>();
            for (JsonNode o : rootNode.get("scenarios")) {
                LinkedHashMap<String, byte[]> nodes = new LinkedHashMap<>();
                o.get("nodes").fields().forEachRemaining(f -> nodes.put(f.getKey(), Bytes.fromHex(f.getValue().asText())));
                LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
                o.get("entries").fields().forEachRemaining(f -> entries.put(f.getKey(), Bytes.fromHex(f.getValue().asText())));
                out.add(new Scenario(o.get("name").asText(), o.get("hash").asText(),
                        Bytes.fromHex(o.get("root").asText()), nodes, entries));
            }
            return out;
        }
    }

    /** Minimal in-memory store keyed by hex. */
    public static final class MapStore implements NodeStore {
        final Map<String, byte[]> m = new HashMap<>();

        @Override
        public byte[] get(byte[] h) {
            return m.get(Bytes.toHex(h));
        }

        @Override
        public void put(byte[] h, byte[] v) {
            m.put(Bytes.toHex(h), v);
        }

        @Override
        public void delete(byte[] h) {
            m.remove(Bytes.toHex(h));
        }
    }
}
