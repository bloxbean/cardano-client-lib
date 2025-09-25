package com.bloxbean.cardano.statetrees.rocksdb;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStore;
import com.bloxbean.cardano.statetrees.jmt.mode.JmtModes;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;
import com.bloxbean.cardano.statetrees.mpt.mpf.MpfProofFormatter;
import com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbJmtStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void generateProof() throws IOException {
        HashFunction hash = Blake2b256::digest; // our default

        String dbPath = tempDir.resolve("rocks-st").toString();
        try (RocksDbStateTrees st = new RocksDbStateTrees(dbPath)) {
            SecureTrie trie = new SecureTrie(st.nodeStore(), hash);
            trie.put("mango".getBytes(), "100".getBytes());
            trie.put("apple".getBytes(), "200".getBytes());
            trie.put("orange".getBytes(), "300".getBytes());
            trie.put("apple".getBytes(), "400".getBytes());
            byte[] root = trie.getRootHash();
            byte[] key = "mango".getBytes();
            byte[] value = "100".getBytes();
            byte[] proof = trie.getProofWire(key).orElseThrow();

            System.out.println("ROOT_HEX=" + encodeHexString(root));
            System.out.println("KEY_HEX=" + encodeHexString(key));
            System.out.println("VAL_HEX=" + encodeHexString(value));
            System.out.println("PROOF_HEX=" + encodeHexString(proof));

            boolean verify = trie.verifyProofWire(root, key, "100".getBytes(), true, proof);
            System.out.println("VERIFY=" + verify);

            String json = MpfProofFormatter.toJson(proof);
            System.out.println("JSON=" + json);

            String aiken = MpfProofFormatter.toAiken(proof);
            System.out.println("AIKEN=" + aiken);
            assertTrue(verify);
            assertEquals(new String(trie.get("apple".getBytes())), "400");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void generateProofJmt() {
        String dbPath = tempDir.resolve("jmt-facade-db").toString();
        try (RocksDbJmtStore store = new RocksDbJmtStore(dbPath)) {
            HashFunction hash = Blake2b256::digest; // our default
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, JmtModes.mpf(hash), hash);

            tree.commit(1, Map.of("mango".getBytes(), "100".getBytes()));
            tree.commit(2, Map.of("apple".getBytes(), "200".getBytes()));
            tree.commit(3, Map.of("orange".getBytes(), "300".getBytes()));

            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put("mango".getBytes(), "200".getBytes());
            updates.put("apple".getBytes(), "900".getBytes());
            tree.commit(4, updates);

            byte[] root = tree.rootHash(4);

            byte[] key = "apple".getBytes();
            byte[] value = "900".getBytes();

            byte[] proof = tree.getProofWire(key, 4).orElseThrow();

            System.out.println("ROOT_HEX=" + encodeHexString(root));
            System.out.println("KEY_HEX=" + encodeHexString(key));
            System.out.println("VAL_HEX=" + encodeHexString(value));
            System.out.println("PROOF_HEX=" + encodeHexString(proof));

            boolean verify = tree.verifyProofWire(root, key, value, true, proof);
            System.out.println("VERIFY=" + verify);

            String json = com.bloxbean.cardano.statetrees.jmt.mpf.MpfProofFormatter.toJson(proof);
            System.out.println("JSON=" + json);

            String aiken = com.bloxbean.cardano.statetrees.jmt.mpf.MpfProofFormatter.toAiken(proof);
            System.out.println("AIKEN=" + aiken);
            assertTrue(verify);
        }

    }
}
