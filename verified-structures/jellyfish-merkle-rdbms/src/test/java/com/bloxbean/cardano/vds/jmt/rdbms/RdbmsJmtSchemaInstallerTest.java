package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.rdbms.dialect.H2Dialect;
import com.bloxbean.cardano.vds.rdbms.dialect.PostgresDialect;
import com.bloxbean.cardano.vds.rdbms.dialect.SqlDialect;
import com.bloxbean.cardano.vds.rdbms.dialect.SqliteDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Issue #715 item 6: the JMT RDBMS backend can create its own schema, like the MPF one. */
class RdbmsJmtSchemaInstallerTest {

    private static final Set<String> TABLES = Set.of(
            "jmt_nodes", "jmt_values", "jmt_roots", "jmt_latest", "jmt_stale", "jmt_metadata");

    @TempDir
    Path tempDir;

    @Test
    void h2CreatesSchemaIdempotentlyAndTreeRoundTrips() throws Exception {
        try (DbConfig config = DbConfig.builder().simpleJdbcUrl(h2Url()).build()) {
            assertThrows(RuntimeException.class, () -> new RdbmsJmtStore(config), "no tables yet");
            RdbmsJmtSchemaInstaller.createIfNotExists(config);
            RdbmsJmtSchemaInstaller.createIfNotExists(config);
            assertTrue(tables(config).containsAll(TABLES), tables(config).toString());
            roundTrip(config);
        }
    }

    @Test
    void h2PrefixedSchemasCoexist() throws Exception {
        String url = h2Url();
        try (DbConfig a = DbConfig.builder().simpleJdbcUrl(url).tablePrefix("acct").build();
             DbConfig b = DbConfig.builder().simpleJdbcUrl(url).tablePrefix("utxo").build()) {
            RdbmsJmtSchemaInstaller.createIfNotExists(a);
            RdbmsJmtSchemaInstaller.createIfNotExists(b);
            RdbmsJmtSchemaInstaller.createIfNotExists(a);
            Set<String> tables = tables(a);
            for (String t : TABLES) {
                assertTrue(tables.contains("acct_" + t) && tables.contains("utxo_" + t), tables.toString());
                assertFalse(tables.contains(t), tables.toString());
            }
            roundTrip(a);
            roundTrip(b);
        }
    }

    @Test
    void sqliteCreatesSchemaIdempotentlyWithAndWithoutPrefix() throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("jmt-schema.db").toAbsolutePath();
        try (DbConfig plain = DbConfig.builder().simpleJdbcUrl(url).build();
             DbConfig prefixed = DbConfig.builder().simpleJdbcUrl(url).tablePrefix("shard1").build()) {
            RdbmsJmtSchemaInstaller.createIfNotExists(plain);
            RdbmsJmtSchemaInstaller.createIfNotExists(plain);
            RdbmsJmtSchemaInstaller.createIfNotExists(prefixed);
            RdbmsJmtSchemaInstaller.createIfNotExists(prefixed);
            Set<String> tables = tables(plain);
            for (String t : TABLES) {
                assertTrue(tables.contains(t) && tables.contains("shard1_" + t), tables.toString());
            }
            assertEquals(2, indexes(plain, "idx_jmt_stale_ns_since"), "unprefixed + prefixed stale index");
            roundTrip(plain);
            roundTrip(prefixed);
        }
    }

    @Test
    void installerAndRawBundledResourceCreateTheSameColumns() throws Exception {
        for (String dialect : List.of("h2", "sqlite")) {
            String rawUrl = dialect.equals("h2") ? h2Url() : "jdbc:sqlite:" + tempDir.resolve("raw.db").toAbsolutePath();
            String helperUrl = dialect.equals("h2") ? h2Url() : "jdbc:sqlite:" + tempDir.resolve("helper.db").toAbsolutePath();
            try (DbConfig raw = DbConfig.builder().simpleJdbcUrl(rawUrl).build();
                 DbConfig helper = DbConfig.builder().simpleJdbcUrl(helperUrl).build()) {
                String ddl = new String(getClass().getResourceAsStream("/ddl/jmt/" + dialect + "/schema.sql").readAllBytes(),
                        StandardCharsets.UTF_8);
                try (Connection c = raw.dataSource().getConnection(); Statement st = c.createStatement()) {
                    for (String sql : ddl.replaceAll("--[^\n]*", "").split(";")) {
                        if (!sql.isBlank()) st.execute(sql);
                    }
                }
                RdbmsJmtSchemaInstaller.createIfNotExists(helper);
                assertEquals(columns(raw), columns(helper), dialect);
            }
        }
    }

    @Test
    void statementsRewriteTableAndIndexNames() {
        for (SqlDialect dialect : List.of(new H2Dialect(), new PostgresDialect(), new SqliteDialect())) {
            List<String> stmts = RdbmsJmtSchemaInstaller.statements(dialect, "p");
            assertEquals(12, stmts.size(), dialect.name());
            String all = String.join("\n", stmts);
            for (String t : TABLES) assertTrue(all.contains("p_" + t), t);
            assertTrue(all.contains("p_idx_jmt_nodes_ns_path") && all.contains("p_idx_jmt_stale_ns_since"), all);
            assertFalse(all.matches("(?s).*(?<![a-z_])(jmt_|idx_jmt_).*"), all);
            assertFalse(all.contains("--"), all);
        }
    }

    @Test
    void prefixLengthIsBoundedByPostgresIdentifierLimit() {
        // Longest generated name is "<prefix>_idx_jmt_stale_ns_since" (prefix + 23 characters)
        String longest = "p".repeat(RdbmsJmtSchemaInstaller.MAX_IDENTIFIER_LENGTH - 23);
        assertTrue(String.join("\n", RdbmsJmtSchemaInstaller.statements(new PostgresDialect(), longest))
                .contains(longest + "_idx_jmt_stale_ns_since"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RdbmsJmtSchemaInstaller.statements(new PostgresDialect(), longest + "x"));
        assertTrue(e.getMessage().contains("too long"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> RdbmsJmtSchemaInstaller.statements(new H2Dialect(), "bad-prefix;"));
    }

    private static String h2Url() {
        return "jdbc:h2:mem:jmt_schema_" + UUID.randomUUID().toString().replace('-', '_') + ";DB_CLOSE_DELAY=-1";
    }

    private static void roundTrip(DbConfig config) {
        try (RdbmsJmtStore store = new RdbmsJmtStore(config)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            tree.put(1, Map.of("alice".getBytes(StandardCharsets.UTF_8), "100".getBytes(StandardCharsets.UTF_8)));
            assertArrayEquals("100".getBytes(StandardCharsets.UTF_8),
                    tree.get("alice".getBytes(StandardCharsets.UTF_8)).orElseThrow());
        }
        try (RdbmsJmtStore reopened = new RdbmsJmtStore(config)) {
            assertEquals(1L, reopened.latestRoot().orElseThrow().version());
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

    private static Map<String, String> columns(DbConfig config) throws Exception {
        Map<String, String> out = new TreeMap<>();
        try (Connection c = config.dataSource().getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            for (String t : tables(config)) {
                if (!TABLES.contains(t)) continue;
                try (ResultSet rs = md.getColumns(null, null, t.toUpperCase(Locale.ROOT).equals(t) ? t : matchCase(md, t), "%")) {
                    while (rs.next()) {
                        out.put(t + "." + rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),
                                rs.getString("TYPE_NAME") + "/" + rs.getString("IS_NULLABLE"));
                    }
                }
            }
        }
        return out;
    }

    private static String matchCase(DatabaseMetaData md, String table) throws Exception {
        return md.storesUpperCaseIdentifiers() ? table.toUpperCase(Locale.ROOT) : table;
    }

    private static long indexes(DbConfig config, String suffix) throws Exception {
        Set<String> names = new HashSet<>(); // getIndexInfo returns one row per indexed column
        try (Connection c = config.dataSource().getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            for (String t : tables(config)) {
                try (ResultSet rs = md.getIndexInfo(null, null, t, false, false)) {
                    while (rs.next()) {
                        String name = rs.getString("INDEX_NAME");
                        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(suffix)) names.add(name);
                    }
                }
            }
        }
        return names.size();
    }
}
