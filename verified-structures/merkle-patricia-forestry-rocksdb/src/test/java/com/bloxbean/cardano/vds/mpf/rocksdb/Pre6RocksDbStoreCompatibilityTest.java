package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.vds.core.api.StorageMode;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcManager;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcStrategy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.RetentionPolicy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.OnDiskMarkSweepStrategy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.RefcountGcStrategy;
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opens a RocksDB database written by the published cardano-client 0.8.0-pre6 artifacts
 * ({@code mpf-compat/pre6-rocksdb.zip}, generator in {@code src/test/pre6-generator}). It has four namespaces:
 * the default one with versions recorded by {@code putRootWithRefcount} inside the session (pre6 counted only the
 * roots), key prefix 0x01 recorded after the session, key prefix 0x02 recorded with {@code rootsIndex.put} only,
 * and column-family namespace {@code acct}.
 *
 * <p>Every version must read back unchanged. Then each namespace gets a new version written by the current code
 * and is garbage-collected (refcount GC where versions were refcounted, mark-sweep otherwise) keeping the latest
 * version, twice; the latest version and every other namespace must stay intact.</p>
 */
class Pre6RocksDbStoreCompatibilityTest {

    @TempDir
    Path tmp;

    @Test
    void pre6DatabaseOpensAndSurvivesGcWithCurrentCode() throws Exception {
        Path dir = unzip("/mpf-compat/pre6-rocksdb.zip");
        JsonNode manifest = new ObjectMapper().readTree(dir.resolve("manifest.json").toFile());
        String db = dir.resolve("rocksdb").toString();
        List<JsonNode> scenarios = new ArrayList<>();
        manifest.get("rocksdb").forEach(scenarios::add);
        assertEquals(4, scenarios.size());

        Map<String, long[]> retained = new HashMap<>(); // scenario -> versions that must still read back
        for (JsonNode s : scenarios) {
            List<Long> all = new ArrayList<>();
            s.get("versions").forEach(v -> all.add(v.get("version").asLong()));
            retained.put(s.get("name").asText(), all.stream().mapToLong(Long::longValue).toArray());
        }
        for (JsonNode s : scenarios) assertVersions(db, s, retained.get(s.get("name").asText()), null);

        for (JsonNode s : scenarios) {
            String name = s.get("name").asText();
            boolean refcounted = !name.contains("index-only");
            Map<String, byte[]> latestEntries;
            long newVersion;
            byte[] newRoot;
            try (RocksDbStateTrees st = open(db, s)) {
                JsonNode last = s.get("versions").get(s.get("versions").size() - 1);
                MpfTrie trie = MpfTrie.open(st.nodeStore(), Bytes.fromHex(last.get("root").asText()));
                RocksDbMptSession session = RocksDbMptSession.of(st);
                byte[] k = ("new-" + name).getBytes();
                newVersion = session.write(() -> {
                    trie.put(k, "after-upgrade".getBytes());
                    return refcounted ? st.putRootWithRefcount(trie.getRootHash()) : recordIndexOnly(st, trie.getRootHash());
                });
                newRoot = trie.getRootHash();
                latestEntries = entries(last);
                latestEntries.put(Bytes.toHex(k), "after-upgrade".getBytes());

                GcStrategy strategy = refcounted ? new RefcountGcStrategy() : new OnDiskMarkSweepStrategy();
                for (int run = 0; run < 2; run++) {
                    new GcManager(st.nodeStore(), st.rootsIndex()).runSync(strategy, RetentionPolicy.keepLatestN(1), new GcOptions());
                }
                assertTrie(name + " latest after GC", MpfTrie.open(st.nodeStore(), newRoot), latestEntries);
            }
            retained.put(name, new long[0]);
            // Every other namespace keeps all versions that were not collected.
            for (JsonNode other : scenarios) {
                if (other != s) assertVersions(db, other, retained.get(other.get("name").asText()), null);
            }
            retained.put(name, new long[]{newVersion});
            assertVersions(db, s, new long[0], new AbstractMap.SimpleEntry<>(newRoot, latestEntries));
        }
    }

    private static long recordIndexOnly(RocksDbStateTrees st, byte[] root) {
        long v = st.rootsIndex().nextVersion();
        st.rootsIndex().put(v, root);
        return v;
    }

    private void assertVersions(String db, JsonNode s, long[] versions, Map.Entry<byte[], Map<String, byte[]>> extra) {
        try (RocksDbStateTrees st = open(db, s)) {
            for (JsonNode v : s.get("versions")) {
                long version = v.get("version").asLong();
                if (Arrays.stream(versions).noneMatch(x -> x == version)) continue;
                assertEquals(v.get("root").asText(), Bytes.toHex(st.rootsIndex().get(version)), s.get("name") + " v" + version);
                assertTrie(s.get("name") + " v" + version, MpfTrie.open(st.nodeStore(), Bytes.fromHex(v.get("root").asText())), entries(v));
            }
            if (extra != null) assertTrie(s.get("name") + " latest", MpfTrie.open(st.nodeStore(), extra.getKey()), extra.getValue());
        }
    }

    private static RocksDbStateTrees open(String db, JsonNode s) {
        String cf = s.get("cfPrefix").asText();
        byte prefix = (byte) s.get("keyPrefix").asInt();
        return new RocksDbStateTrees(db, new NamespaceOptions(cf, prefix), StorageMode.MULTI_VERSION);
    }

    private static Map<String, byte[]> entries(JsonNode version) {
        Map<String, byte[]> out = new TreeMap<>();
        version.get("entries").fields().forEachRemaining(e -> out.put(e.getKey(), Bytes.fromHex(e.getValue().asText())));
        return out;
    }

    private static void assertTrie(String what, MpfTrie trie, Map<String, byte[]> expected) {
        for (Map.Entry<String, byte[]> e : expected.entrySet()) {
            assertArrayEquals(e.getValue(), trie.get(Bytes.fromHex(e.getKey())), what + " key " + e.getKey());
        }
        assertEquals(expected.size(), trie.getAllEntries().size(), what);
        for (String k : expected.keySet()) {
            byte[] key = Bytes.fromHex(k);
            assertTrue(trie.verifyProofWire(trie.getRootHash(), key, expected.get(k), true, trie.getProofWire(key).orElseThrow()), what);
            break;
        }
    }

    private Path unzip(String resource) throws Exception {
        Path out = tmp.resolve("pre6");
        try (InputStream in = getClass().getResourceAsStream(resource); ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                Path target = out.resolve(e.getName()).normalize();
                assertTrue(target.startsWith(out));
                if (e.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target);
                }
            }
        }
        return out;
    }
}
