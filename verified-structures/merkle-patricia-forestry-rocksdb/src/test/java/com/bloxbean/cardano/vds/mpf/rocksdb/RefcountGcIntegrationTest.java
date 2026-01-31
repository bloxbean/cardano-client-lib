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

                // Commit version 0 with k1
                try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
                    st.nodeStore().withBatch(wb, () -> {
                        trie.put(k1, "V0".getBytes());
                        st.rootsIndex().withBatch(wb, () -> {
                            long v = st.rootsIndex().nextVersion();
                            st.rootsIndex().put(v, trie.getRootHash());
                            return null;
                        });
                        RefcountGcStrategy.incrementAll(st.db(), st.nodeStore().nodesHandle(), st.nodeStore().nodesHandle(), trie.getRootHash(), wb, st.nodeStore().keyPrefixer());
                        return null;
                    });
                    st.db().write(wo, wb);
                }

                long nodesAfterV0 = countNodes(st);
                assertTrue(nodesAfterV0 > 0);

                // Commit version 1 with updated k1 and new k2; do not decrement old yet
                try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
                    st.nodeStore().withBatch(wb, () -> {
                        trie.put(k1, "V0b".getBytes());
                        trie.put(k2, "V1".getBytes());
                        st.rootsIndex().withBatch(wb, () -> {
                            long v = st.rootsIndex().nextVersion();
                            st.rootsIndex().put(v, trie.getRootHash());
                            return null;
                        });
                        RefcountGcStrategy.incrementAll(st.db(), st.nodeStore().nodesHandle(), st.nodeStore().nodesHandle(), trie.getRootHash(), wb, st.nodeStore().keyPrefixer());
                        return null;
                    });
                    st.db().write(wo, wb);
                }

                long nodesAfterV1 = countNodes(st);
                assertTrue(nodesAfterV1 >= nodesAfterV0);

                // Run retention: keep latest 1; strategy will decrement old versions and delete 0-ref nodes
                GcManager manager = new GcManager(st.nodeStore(), st.rootsIndex());
                GcReport rep = manager.runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
                long nodesAfterGc = countNodes(st);
                assertTrue(nodesAfterGc <= nodesAfterV1);

                // latest reads should work
                MpfTrie latestTrie = new MpfTrie(st.nodeStore(), st.rootsIndex().latest());
                assertEquals("V0b", new String(latestTrie.get(k1)));
                assertEquals("V1", new String(latestTrie.get(k2)));
            }
        } catch (UnsatisfiedLinkError e) {
            Assumptions.assumeTrue(false, "RocksDB JNI not available");
        }
    }

    private static long countNodes(RocksDbStateTrees st) {
        long cnt = 0;
        try (ReadOptions ro = new ReadOptions(); RocksIterator it = st.db().newIterator(st.nodeStore().nodesHandle(), ro)) {
            for (it.seekToFirst(); it.isValid(); it.next()) cnt++;
        }
        return cnt;
    }
}
