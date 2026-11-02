package demo;

import com.bloxbean.cardano.vds.core.api.StorageMode;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rdbms.RdbmsStateTrees;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbMptSession;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbStateTrees;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

/**
 * Writes real databases with the PUBLISHED cardano-client 0.8.0-pre6 artifacts (Maven Central), for CCL's
 * backward-compatibility tests. Usage: GenPre6Stores <outDir>. Produces outDir/rocksdb, outDir/h2 and
 * outDir/manifest.json (expected roots and entries per namespace and version).
 */
public class GenPre6Stores {
    static final ObjectMapper OM = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        ObjectNode manifest = OM.createObjectNode();
        manifest.put("generatedBy", "cardano-client 0.8.0-pre6 artifacts from Maven Central (demo.GenPre6Stores)");
        ArrayNode rocks = manifest.putArray("rocksdb");
        String rocksPath = out.resolve("rocksdb").toString();

        // 0x00 default namespace: versions recorded with putRootWithRefcount INSIDE the session (pre6 counted roots only).
        rocks.add(rocksScenario(rocksPath, "default-in-session", NamespaceOptions.defaults(), 1, true));
        // key-prefix namespace 0x01: nodes committed first, then putRootWithRefcount (full counts).
        rocks.add(rocksScenario(rocksPath, "keyprefix-01-after-session", NamespaceOptions.keyPrefix((byte) 0x01), 2, false));
        // key-prefix namespace 0x02: versions recorded with rootsIndex.put only (mark-sweep users).
        rocks.add(rocksScenario(rocksPath, "keyprefix-02-index-only", NamespaceOptions.keyPrefix((byte) 0x02), 3, null));
        // column-family namespace "acct".
        rocks.add(rocksScenario(rocksPath, "cf-acct-after-session", NamespaceOptions.columnFamily("acct"), 4, false));

        ArrayNode h2 = manifest.putArray("h2");
        String url = "jdbc:h2:file:" + out.resolve("h2").resolve("mpf").toAbsolutePath();
        Files.createDirectories(out.resolve("h2"));
        try (DbConfig config = DbConfig.builder().simpleJdbcUrl(url).build()) {
            applyDdl(config);
            h2.add(rdbmsScenario(config, "h2-default-multi", StorageMode.MULTI_VERSION, (byte) 0x00, 5));
            h2.add(rdbmsScenario(config, "h2-ns-07-single", StorageMode.SINGLE_VERSION, (byte) 0x07, 6));
        }
        Files.writeString(out.resolve("manifest.json"), OM.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
        System.out.println("wrote " + out);
    }

    /** refcountInSession: true = inside session, false = after session, null = rootsIndex.put only. */
    static ObjectNode rocksScenario(String path, String name, NamespaceOptions ns, long seed, Boolean refcountInSession) throws Exception {
        ObjectNode sc = OM.createObjectNode();
        sc.put("name", name);
        sc.put("cfPrefix", ns.columnFamilyPrefix());
        sc.put("keyPrefix", ns.keyPrefix() & 0xFF);
        ArrayNode versions = sc.putArray("versions");
        Random rnd = new Random(seed);
        Map<String, byte[]> model = new TreeMap<>();
        List<byte[]> keys = new ArrayList<>();
        for (int i = 0; i < 120; i++) keys.add(("k-" + name + "-" + i).getBytes(StandardCharsets.UTF_8));
        try (RocksDbStateTrees st = new RocksDbStateTrees(path, ns, StorageMode.MULTI_VERSION)) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            RocksDbMptSession session = RocksDbMptSession.of(st.nodeStore());
            for (int v = 0; v < 5; v++) {
                List<Runnable> ops = new ArrayList<>();
                int n = v == 0 ? 80 : 25;
                for (int i = 0; i < n; i++) {
                    byte[] k = keys.get(rnd.nextInt(keys.size()));
                    if (v > 0 && rnd.nextInt(4) == 0) {
                        ops.add(() -> trie.delete(k));
                        model.remove(hex(k));
                    } else {
                        byte[] val = ("v" + v + "-" + rnd.nextInt(1000)).getBytes(StandardCharsets.UTF_8);
                        ops.add(() -> trie.put(k, val));
                        model.put(hex(k), val);
                    }
                }
                long version;
                if (Boolean.TRUE.equals(refcountInSession)) {
                    version = session.write(() -> {
                        ops.forEach(Runnable::run);
                        return st.putRootWithRefcount(trie.getRootHash());
                    });
                } else {
                    session.write(() -> ops.forEach(Runnable::run));
                    if (refcountInSession == null) {
                        version = st.rootsIndex().nextVersion();
                        st.rootsIndex().put(version, trie.getRootHash());
                    } else {
                        version = st.putRootWithRefcount(trie.getRootHash());
                    }
                }
                ObjectNode ver = versions.addObject();
                ver.put("version", version);
                ver.put("root", hex(trie.getRootHash()));
                ObjectNode entries = ver.putObject("entries");
                model.forEach((k, val) -> entries.put(k, hex(val)));
            }
        }
        return sc;
    }

    static ObjectNode rdbmsScenario(DbConfig config, String name, StorageMode mode, byte ns, long seed) throws Exception {
        ObjectNode sc = OM.createObjectNode();
        sc.put("name", name);
        sc.put("mode", mode.name());
        sc.put("keyPrefix", ns & 0xFF);
        ArrayNode versions = sc.putArray("versions");
        Random rnd = new Random(seed);
        Map<String, byte[]> model = new TreeMap<>();
        RdbmsStateTrees st = new RdbmsStateTrees(config, mode, ns);
        MpfTrie trie = new MpfTrie(st.nodeStore());
        for (int v = 0; v < 3; v++) {
            for (int i = 0; i < 40; i++) {
                byte[] k = ("r-" + name + "-" + rnd.nextInt(80)).getBytes(StandardCharsets.UTF_8);
                if (v > 0 && rnd.nextInt(4) == 0) {
                    trie.delete(k);
                    model.remove(hex(k));
                } else {
                    byte[] val = ("v" + v + "-" + rnd.nextInt(1000)).getBytes(StandardCharsets.UTF_8);
                    trie.put(k, val);
                    model.put(hex(k), val);
                }
            }
            long version;
            if (mode == StorageMode.MULTI_VERSION) {
                version = st.putRootWithRefcount(trie.getRootHash());
            } else {
                st.putRootSnapshot(trie.getRootHash());
                version = 0;
            }
            ObjectNode ver = versions.addObject();
            ver.put("version", version);
            ver.put("root", hex(trie.getRootHash()));
            ObjectNode entries = ver.putObject("entries");
            model.forEach((k, val) -> entries.put(k, hex(val)));
        }
        return sc;
    }

    static void applyDdl(DbConfig config) throws Exception {
        String ddl;
        try (InputStream in = RdbmsStateTrees.class.getResourceAsStream("/ddl/mpf/h2/schema.sql")) {
            ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        StringBuilder sb = new StringBuilder();
        for (String line : ddl.split("\n")) if (!line.trim().startsWith("--")) sb.append(line).append('\n');
        try (Connection c = config.dataSource().getConnection(); Statement s = c.createStatement()) {
            for (String stmt : sb.toString().split(";")) if (!stmt.isBlank()) s.execute(stmt);
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
