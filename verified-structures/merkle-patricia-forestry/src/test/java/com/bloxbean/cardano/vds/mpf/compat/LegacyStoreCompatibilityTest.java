package com.bloxbean.cardano.vds.mpf.compat;

import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against storage regressions: tries persisted by CCL 0.8.0-pre6 must open unchanged, and the current
 * code must still write byte-identical nodes and roots for the same inputs.
 *
 * <p>Regenerate the fixture (only intentionally, from a commit whose storage format is the reference) with
 * the environment variable {@code MPF_COMPAT_REGENERATE=<absolute path of legacy-store-v0.8.0-pre6.json>}.</p>
 */
class LegacyStoreCompatibilityTest {

    @Test
    void legacyStoresOpenWithSameRootsAndValues() throws Exception {
        List<LegacyStoreFixture.Scenario> scenarios = LegacyStoreFixture.load();
        assertEquals(4, scenarios.size());
        for (LegacyStoreFixture.Scenario s : scenarios) {
            LegacyStoreFixture.MapStore store = new LegacyStoreFixture.MapStore();
            s.loadInto(store);
            MpfTrie trie = new MpfTrie(store, s.hashFunction(), s.root);
            assertArrayEquals(s.root, trie.getRootHash(), s.name);
            for (Map.Entry<String, byte[]> e : s.entries.entrySet()) {
                assertArrayEquals(e.getValue(), trie.get(Bytes.fromHex(e.getKey())), s.name + " key " + e.getKey());
            }
            assertEquals(s.entries.size(), trie.getAllEntries().size(), s.name);
        }
    }

    @Test
    void currentCodeWritesIdenticalNodes() throws Exception {
        List<LegacyStoreFixture.Scenario> built = LegacyStoreFixture.build();
        String regenerate = System.getenv("MPF_COMPAT_REGENERATE");
        if (regenerate != null && !regenerate.isBlank()) {
            Files.writeString(Path.of(regenerate), LegacyStoreFixture.toJson(built), StandardCharsets.UTF_8);
        }
        List<LegacyStoreFixture.Scenario> legacy = LegacyStoreFixture.load();
        assertEquals(legacy.size(), built.size());
        for (int i = 0; i < legacy.size(); i++) {
            LegacyStoreFixture.Scenario l = legacy.get(i), b = built.get(i);
            assertEquals(l.name, b.name);
            assertArrayEquals(l.root, b.root, l.name + " root");
            assertEquals(l.nodes.keySet(), b.nodes.keySet(), l.name + " node keys");
            for (String k : l.nodes.keySet()) assertArrayEquals(l.nodes.get(k), b.nodes.get(k), l.name + " node " + k);
        }
    }
}
