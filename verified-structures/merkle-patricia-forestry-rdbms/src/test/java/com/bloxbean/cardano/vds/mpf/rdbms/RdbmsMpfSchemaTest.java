package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.core.api.StorageMode;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.rdbms.dialect.H2Dialect;
import com.bloxbean.cardano.vds.rdbms.dialect.PostgresDialect;
import com.bloxbean.cardano.vds.rdbms.dialect.SqlDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RdbmsMpfSchemaTest {

    @TempDir
    Path tempDir;

    @Test
    void h2CreatesSchemaIdempotentlyAndTrieRoundTrips() throws Exception {
        String url = "jdbc:h2:mem:mpf_schema_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        try (DbConfig config = DbConfig.builder().simpleJdbcUrl(url).build()) {
            RdbmsMpfSchema.createIfNotExists(config);
            RdbmsMpfSchema.createIfNotExists(config);
            assertTrue(tables(config).containsAll(Set.of("mpt_nodes", "mpt_roots", "mpt_latest")));
            roundTrip(config);
        }
    }

    @Test
    void h2PrefixedSchemasCoexist() throws Exception {
        String url = "jdbc:h2:mem:mpf_schema_prefix_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        try (DbConfig a = DbConfig.builder().simpleJdbcUrl(url).tablePrefix("acct").build();
             DbConfig b = DbConfig.builder().simpleJdbcUrl(url).tablePrefix("utxo").build()) {
            RdbmsMpfSchema.createIfNotExists(a);
            RdbmsMpfSchema.createIfNotExists(b);
            RdbmsMpfSchema.createIfNotExists(a);
            Set<String> tables = tables(a);
            assertTrue(tables.containsAll(Set.of("acct_mpt_nodes", "acct_mpt_roots", "acct_mpt_latest",
                    "utxo_mpt_nodes", "utxo_mpt_roots", "utxo_mpt_latest")), tables.toString());
            assertFalse(tables.contains("mpt_nodes"));
            roundTrip(a);
            roundTrip(b);
        }
    }

    @Test
    void sqliteCreatesSchemaIdempotentlyWithAndWithoutPrefix() throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("mpf-schema.db").toAbsolutePath();
        try (DbConfig plain = DbConfig.builder().simpleJdbcUrl(url).build();
             DbConfig prefixed = DbConfig.builder().simpleJdbcUrl(url).tablePrefix("shard1").build()) {
            RdbmsMpfSchema.createIfNotExists(plain);
            RdbmsMpfSchema.createIfNotExists(plain);
            RdbmsMpfSchema.createIfNotExists(prefixed);
            RdbmsMpfSchema.createIfNotExists(prefixed);
            Set<String> tables = tables(plain);
            assertTrue(tables.containsAll(Set.of("mpt_nodes", "mpt_roots", "mpt_latest",
                    "shard1_mpt_nodes", "shard1_mpt_roots", "shard1_mpt_latest")), tables.toString());
            assertEquals(2, indexes(plain, "idx_mpt_nodes_ns"), "unprefixed + prefixed nodes index");
            roundTrip(plain);
            roundTrip(prefixed);
        }
    }

    @Test
    void statementsRewriteTableAndIndexNames() {
        for (SqlDialect dialect : List.of(new H2Dialect(), new PostgresDialect())) {
            List<String> stmts = RdbmsMpfSchema.statements(dialect, "p");
            assertEquals(5, stmts.size(), dialect.name());
            String all = String.join("\n", stmts);
            assertTrue(all.contains("p_mpt_nodes") && all.contains("p_idx_mpt_nodes_ns")
                    && all.contains("p_idx_mpt_roots_ns") && all.contains("p_mpt_latest"), all);
            assertFalse(all.matches("(?s).*(?<![a-z_])mpt_nodes.*"), all);
            assertFalse(all.contains("--"), all);
        }
    }

    @Test
    void invalidPrefixRejected() {
        assertThrows(IllegalArgumentException.class, () -> RdbmsMpfSchema.statements(new H2Dialect(), "bad-prefix;"));
    }

    @Test
    void prefixLengthIsBoundedByPostgresIdentifierLimit() {
        // Longest generated name is "<prefix>_idx_mpt_nodes_ns" (prefix + 17 characters)
        String longest = "p".repeat(RdbmsMpfSchema.MAX_IDENTIFIER_LENGTH - 17);
        for (SqlDialect dialect : List.of(new H2Dialect(), new PostgresDialect())) {
            List<String> stmts = RdbmsMpfSchema.statements(dialect, longest);
            assertTrue(String.join("\n", stmts).contains(longest + "_idx_mpt_nodes_ns"));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> RdbmsMpfSchema.statements(dialect, longest + "x"));
            assertTrue(e.getMessage().contains("too long"), e.getMessage());
        }
    }

    @Test
    void tooLongPrefixRejectedBeforeTouchingTheDatabase() {
        String url = "jdbc:h2:mem:mpf_schema_long_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String prefix = "p".repeat(RdbmsMpfSchema.MAX_IDENTIFIER_LENGTH);
        try (DbConfig config = DbConfig.builder().simpleJdbcUrl(url).tablePrefix(prefix).build()) {
            assertThrows(IllegalArgumentException.class, () -> RdbmsMpfSchema.createIfNotExists(config));
            assertTrue(tables(config).stream().noneMatch(n -> n.contains("mpt_")), tables(config).toString());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void failureRollsBackWhenAutoCommitIsOff() throws Exception {
        String url = "jdbc:h2:mem:mpf_schema_rollback_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        try (DbConfig real = DbConfig.builder().simpleJdbcUrl(url).build()) {
            AtomicInteger rollbacks = new AtomicInteger();
            AtomicInteger commits = new AtomicInteger();
            DataSource failing = failingOnThirdStatement(real.dataSource(), rollbacks, commits);
            DbConfig config = DbConfig.builder().dataSource(failing).dialect(new H2Dialect()).build();
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> RdbmsMpfSchema.createIfNotExists(config));
            assertTrue(e.getCause() instanceof SQLException, String.valueOf(e.getCause()));
            assertEquals(1, rollbacks.get());
            assertEquals(0, commits.get());

            // Retrying against a healthy data source completes the schema
            RdbmsMpfSchema.createIfNotExists(real);
            roundTrip(real);
        }
    }

    /** Data source whose connections have auto-commit off and whose third executed statement fails. */
    private static DataSource failingOnThirdStatement(DataSource delegate, AtomicInteger rollbacks, AtomicInteger commits) {
        AtomicInteger executed = new AtomicInteger();
        return (DataSource) Proxy.newProxyInstance(RdbmsMpfSchemaTest.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (dsProxy, dsMethod, dsArgs) -> {
                    if (!dsMethod.getName().equals("getConnection")) return invoke(dsMethod, delegate, dsArgs);
                    Connection conn = (Connection) invoke(dsMethod, delegate, dsArgs);
                    conn.setAutoCommit(false);
                    return Proxy.newProxyInstance(RdbmsMpfSchemaTest.class.getClassLoader(), new Class<?>[]{Connection.class},
                            (cProxy, cMethod, cArgs) -> {
                                switch (cMethod.getName()) {
                                    case "rollback": rollbacks.incrementAndGet(); break;
                                    case "commit": commits.incrementAndGet(); break;
                                    case "createStatement":
                                        Statement st = (Statement) invoke(cMethod, conn, cArgs);
                                        return Proxy.newProxyInstance(RdbmsMpfSchemaTest.class.getClassLoader(), new Class<?>[]{Statement.class},
                                                (sProxy, sMethod, sArgs) -> {
                                                    if (sMethod.getName().equals("execute") && executed.incrementAndGet() == 3) {
                                                        throw new SQLException("injected failure");
                                                    }
                                                    return invoke(sMethod, st, sArgs);
                                                });
                                    default:
                                }
                                return invoke(cMethod, conn, cArgs);
                            });
                });
    }

    private static Object invoke(java.lang.reflect.Method m, Object target, Object[] args) throws Throwable {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static void roundTrip(DbConfig config) {
        try (RdbmsStateTrees trees = new RdbmsStateTrees(config, StorageMode.MULTI_VERSION)) {
            MpfTrie trie = new MpfTrie(trees.nodeStore());
            trie.put("alice".getBytes(StandardCharsets.UTF_8), "100".getBytes(StandardCharsets.UTF_8));
            trie.put("bob".getBytes(StandardCharsets.UTF_8), "200".getBytes(StandardCharsets.UTF_8));
            long v = trees.putRootWithRefcount(trie.getRootHash());
            MpfTrie reopened = new MpfTrie(trees.nodeStore(), trees.rootsIndex().get(v));
            assertArrayEquals("100".getBytes(StandardCharsets.UTF_8), reopened.get("alice".getBytes(StandardCharsets.UTF_8)));
            assertArrayEquals("200".getBytes(StandardCharsets.UTF_8), reopened.get("bob".getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static Set<String> tables(DbConfig config) throws Exception {
        Set<String> out = new HashSet<>();
        try (Connection c = config.dataSource().getConnection();
             ResultSet rs = c.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) out.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static long indexes(DbConfig config, String suffix) throws Exception {
        long count = 0;
        try (Connection c = config.dataSource().getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            for (String t : tables(config)) {
                try (ResultSet rs = md.getIndexInfo(null, null, t, false, false)) {
                    while (rs.next()) {
                        String name = rs.getString("INDEX_NAME");
                        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(suffix)) count++;
                    }
                }
            }
        }
        return count;
    }
}
