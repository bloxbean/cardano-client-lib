package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.NavigableMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RdbmsRootsIndex using H2 in-memory database.
 */
class RdbmsRootsIndexTest {

    private DbConfig dbConfig;
    private RdbmsRootsIndex rootsIndex;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:test_roots_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        dbConfig = DbConfig.builder()
            .simpleJdbcUrl(jdbcUrl)
            .build();
        createSchema(dbConfig);
        rootsIndex = new RdbmsRootsIndex(dbConfig);
    }

    @AfterEach
    void tearDown() {
        if (rootsIndex != null) rootsIndex.close();
    }

    private void createSchema(DbConfig config) throws Exception {
        try (Connection conn = config.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            String schema = new String(
                getClass().getResourceAsStream("/ddl/mpf/h2/schema.sql").readAllBytes(),
                StandardCharsets.UTF_8
            );
            stmt.execute(schema);
        }
    }

    @Test
    void putAndGetRoot() {
        byte[] root = Blake2b256.digest(bytes("root1"));
        rootsIndex.put(1L, root);

        byte[] retrieved = rootsIndex.get(1L);
        assertArrayEquals(root, retrieved);
    }

    @Test
    void getNonExistentVersionReturnsNull() {
        assertNull(rootsIndex.get(999L));
    }

    @Test
    void latestReturnsLastPut() {
        byte[] root1 = Blake2b256.digest(bytes("root1"));
        byte[] root2 = Blake2b256.digest(bytes("root2"));

        rootsIndex.put(1L, root1);
        assertArrayEquals(root1, rootsIndex.latest());

        rootsIndex.put(2L, root2);
        assertArrayEquals(root2, rootsIndex.latest());
    }

    @Test
    void latestReturnsNullWhenEmpty() {
        assertNull(rootsIndex.latest());
    }

    @Test
    void lastVersionTracksHighestVersion() {
        assertEquals(-1L, rootsIndex.lastVersion());

        rootsIndex.put(5L, Blake2b256.digest(bytes("r5")));
        assertEquals(5L, rootsIndex.lastVersion());

        rootsIndex.put(10L, Blake2b256.digest(bytes("r10")));
        assertEquals(10L, rootsIndex.lastVersion());
    }

    @Test
    void nextVersionReturnsIncrementedVersion() {
        assertEquals(0L, rootsIndex.nextVersion());

        rootsIndex.put(0L, Blake2b256.digest(bytes("r0")));
        assertEquals(1L, rootsIndex.nextVersion());

        rootsIndex.put(1L, Blake2b256.digest(bytes("r1")));
        assertEquals(2L, rootsIndex.nextVersion());
    }

    @Test
    void listAllReturnsSortedMap() {
        byte[] r1 = Blake2b256.digest(bytes("r1"));
        byte[] r2 = Blake2b256.digest(bytes("r2"));
        byte[] r3 = Blake2b256.digest(bytes("r3"));

        rootsIndex.put(3L, r3);
        rootsIndex.put(1L, r1);
        rootsIndex.put(2L, r2);

        NavigableMap<Long, byte[]> all = rootsIndex.listAll();
        assertEquals(3, all.size());
        assertArrayEquals(r1, all.get(1L));
        assertArrayEquals(r2, all.get(2L));
        assertArrayEquals(r3, all.get(3L));

        // Verify sorted order
        Long[] keys = all.keySet().toArray(new Long[0]);
        assertEquals(1L, keys[0]);
        assertEquals(2L, keys[1]);
        assertEquals(3L, keys[2]);
    }

    @Test
    void listRangeReturnsSubset() {
        for (int i = 0; i < 10; i++) {
            rootsIndex.put(i, Blake2b256.digest(bytes("r" + i)));
        }

        NavigableMap<Long, byte[]> range = rootsIndex.listRange(3L, 7L);
        assertEquals(5, range.size());
        assertTrue(range.containsKey(3L));
        assertTrue(range.containsKey(7L));
        assertFalse(range.containsKey(2L));
        assertFalse(range.containsKey(8L));
    }

    @Test
    void listRangeReturnsEmptyForInvalidRange() {
        rootsIndex.put(1L, Blake2b256.digest(bytes("r1")));
        NavigableMap<Long, byte[]> range = rootsIndex.listRange(10L, 5L);
        assertTrue(range.isEmpty());
    }

    @Test
    void namespaceIsolation() {
        RdbmsRootsIndex index1 = new RdbmsRootsIndex(dbConfig, (byte) 0x01);
        RdbmsRootsIndex index2 = new RdbmsRootsIndex(dbConfig, (byte) 0x02);

        try {
            byte[] root1 = Blake2b256.digest(bytes("ns1-root"));
            byte[] root2 = Blake2b256.digest(bytes("ns2-root"));

            index1.put(1L, root1);
            index2.put(1L, root2);

            assertArrayEquals(root1, index1.get(1L));
            assertArrayEquals(root2, index2.get(1L));

            // Each namespace has its own latest
            assertArrayEquals(root1, index1.latest());
            assertArrayEquals(root2, index2.latest());
        } finally {
            index1.close();
            index2.close();
        }
    }

    @Test
    void version0WorksForSnapshotMode() {
        byte[] root = Blake2b256.digest(bytes("snapshot-root"));

        rootsIndex.put(0L, root);
        assertArrayEquals(root, rootsIndex.get(0L));
        assertArrayEquals(root, rootsIndex.latest());

        // Overwrite version 0
        byte[] newRoot = Blake2b256.digest(bytes("new-snapshot-root"));
        rootsIndex.put(0L, newRoot);

        // H2 MERGE KEY will update existing row
        assertArrayEquals(newRoot, rootsIndex.get(0L));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
