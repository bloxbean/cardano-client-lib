package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.*;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.rocksdb.*;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class RefcountGcIntegrationTest {

    @Test
    void incrementalRefcountGcKeepsStoreBounded() throws Exception {
        try {
            Path dir = Files.createTempDirectory("rocks-refcnt");
            try (RocksDbStateTrees st = new RocksDbStateTrees(dir.toString())) {
                MpfTrie trie = new MpfTrie(st.nodeStore());

                // Refcounts are stored alongside nodes using a prefixed key; no separate CF needed

                byte[] k1 = HexUtil.decodeHexString("aa00");
                byte[] k2 = HexUtil.decodeHexString("aa01");

                RocksDbMptSession session = RocksDbMptSession.of(st);
                // Commit version 0 with k1 (nodes + root + refcounts in one batch)
                session.write(() -> {
                    trie.put(k1, "V0".getBytes());
                    st.putRootWithRefcount(trie.getRootHash());
                });

                long nodesAfterV0 = GcTestSupport.countNodeKeys(st.nodeStore());
                assertTrue(nodesAfterV0 > 0);

                // Commit version 1 with updated k1 and new k2; do not decrement old yet
                session.write(() -> {
                    trie.put(k1, "V0b".getBytes());
                    trie.put(k2, "V1".getBytes());
                    st.putRootWithRefcount(trie.getRootHash());
                });
                byte[] latest = st.rootsIndex().latest();

                long nodesAfterV1 = GcTestSupport.countNodeKeys(st.nodeStore());
                assertTrue(nodesAfterV1 > nodesAfterV0);

                // Run retention: keep latest 1; strategy decrements old versions and deletes 0-ref nodes
                GcManager manager = new GcManager(st.nodeStore(), st.rootsIndex());
                manager.runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());

                // Only the latest version stays counted, and it is fully readable
                assertTrue(GcTestSupport.countNodeKeys(st.nodeStore()) < nodesAfterV1);
                assertEquals(GcTestSupport.reach(st.nodeStore(), latest)[0], GcTestSupport.countRefcountKeys(st.nodeStore()));
                java.util.Map<String, byte[]> model = new java.util.LinkedHashMap<>();
                model.put(HexUtil.encodeHexString(k1), "V0b".getBytes());
                model.put(HexUtil.encodeHexString(k2), "V1".getBytes());
                GcTestSupport.assertIntact(st.nodeStore(), latest, model, "latest");
            }
        } catch (UnsatisfiedLinkError e) {
            Assumptions.assumeTrue(false, "RocksDB JNI not available");
        }
    }

}
