package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.core.api.StorageMode;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opens an H2 database written by the published cardano-client 0.8.0-pre6 artifacts
 * ({@code mpf-compat/pre6-h2.zip}): a MULTI_VERSION trie in namespace 0x00 and a SINGLE_VERSION trie in namespace
 * 0x07. Roots, versions and values must read back unchanged, also after running the new
 * {@link RdbmsMpfSchema#createIfNotExists} on the existing schema, and the trie must accept new writes.
 */
class Pre6H2StoreCompatibilityTest {

    @TempDir
    Path tmp;

    @Test
    void pre6H2DatabaseOpensWithCurrentCode() throws Exception {
        Path dir = unzip("/mpf-compat/pre6-h2.zip");
        JsonNode manifest = new ObjectMapper().readTree(dir.resolve("manifest.json").toFile());
        String url = "jdbc:h2:file:" + dir.resolve("h2").resolve("mpf").toAbsolutePath();
        try (DbConfig config = DbConfig.builder().simpleJdbcUrl(url).build()) {
            RdbmsMpfSchema.createIfNotExists(config); // idempotent on a pre6 schema
            int scenarios = 0;
            for (JsonNode s : manifest.get("h2")) {
                scenarios++;
                StorageMode mode = StorageMode.valueOf(s.get("mode").asText());
                byte ns = (byte) s.get("keyPrefix").asInt();
                RdbmsStateTrees st = new RdbmsStateTrees(config, mode, ns);
                JsonNode versions = s.get("versions");
                JsonNode last = versions.get(versions.size() - 1);
                byte[] current = mode == StorageMode.MULTI_VERSION ? st.rootsIndex().latest() : st.getCurrentRoot();
                assertEquals(last.get("root").asText(), Bytes.toHex(current), s.get("name").asText());
                if (mode == StorageMode.MULTI_VERSION) {
                    for (JsonNode v : versions) {
                        assertEquals(v.get("root").asText(), Bytes.toHex(st.rootsIndex().get(v.get("version").asLong())));
                        assertTrie(s.get("name") + " v" + v.get("version"), MpfTrie.open(st.nodeStore(), Bytes.fromHex(v.get("root").asText())), v);
                    }
                } else {
                    assertTrie(s.get("name").asText(), MpfTrie.open(st.nodeStore(), current), last);
                }

                MpfTrie trie = MpfTrie.open(st.nodeStore(), current);
                trie.put("after-upgrade".getBytes(), "x".getBytes());
                assertArrayEquals("x".getBytes(), trie.get("after-upgrade".getBytes()));
                last.get("entries").fields().forEachRemaining(e ->
                        assertArrayEquals(Bytes.fromHex(e.getValue().asText()), trie.get(Bytes.fromHex(e.getKey()))));
            }
            assertEquals(2, scenarios);
        }
    }

    private static void assertTrie(String what, MpfTrie trie, JsonNode version) {
        Map<String, byte[]> expected = new TreeMap<>();
        version.get("entries").fields().forEachRemaining(e -> expected.put(e.getKey(), Bytes.fromHex(e.getValue().asText())));
        for (Map.Entry<String, byte[]> e : expected.entrySet()) {
            assertArrayEquals(e.getValue(), trie.get(Bytes.fromHex(e.getKey())), what);
        }
        assertEquals(expected.size(), trie.getAllEntries().size(), what);
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
