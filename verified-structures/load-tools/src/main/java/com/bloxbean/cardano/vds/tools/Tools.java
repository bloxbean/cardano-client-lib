package com.bloxbean.cardano.vds.tools;

import com.bloxbean.cardano.vds.tools.jmt.JmtConcurrentLoadTester;
import com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester;
import com.bloxbean.cardano.vds.tools.jmt.RdbmsJmtLoadTester;
import com.bloxbean.cardano.vds.tools.mpf.GcTool;
import com.bloxbean.cardano.vds.tools.mpf.MptLoadTester;
import com.bloxbean.cardano.vds.tools.mpf.RdbmsMptLoadTester;

/**
 * Main entry point for VDS Load Testing Tools.
 *
 * <p>Routes to the appropriate tool based on command-line arguments.
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * # Run via fat JAR (recommended)
 * java -jar cardano-client-vds-load-tools-VERSION-all.jar [tool] [options]
 *
 * # Available tools:
 *   jmt              - JMT with RocksDB backend
 *   jmt-rdbms        - JMT with H2/SQLite/PostgreSQL backend
 *   jmt-concurrent   - JMT concurrent load testing
 *   mpt              - MPT with RocksDB backend
 *   mpt-rdbms        - MPT with H2/SQLite/PostgreSQL backend
 *   gc               - Garbage collection tool for MPT
 *   help             - Show this help message
 * </pre>
 *
 * <p><b>Examples:</b></p>
 * <pre>
 * # JMT with RocksDB
 * java -jar cardano-client-vds-load-tools.jar jmt --records=100000 --batch=1000 --rocksdb=/tmp/jmt
 *
 * # JMT with PostgreSQL
 * java -jar cardano-client-vds-load-tools.jar jmt-rdbms --records=100000 --batch=1000 \
 *     --db=postgresql --db-host=localhost --db-name=testdb --db-user=postgres --db-password=secret
 *
 * # MPT with RocksDB and deletes
 * java -jar cardano-client-vds-load-tools.jar mpt --records=100000 --batch=1000 \
 *     --rocksdb=/tmp/mpt --delete-ratio=0.1
 *
 * # MPT garbage collection
 * java -jar cardano-client-vds-load-tools.jar gc --db=/tmp/mpt-rocksdb \
 *     --gc-type=refcount --version=10000
 * </pre>
 */
public final class Tools {

    private Tools() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h") || args[0].equals("help")) {
            printHelp();
            System.exit(0);
        }

        String tool = args[0];
        String[] toolArgs = new String[args.length - 1];
        System.arraycopy(args, 1, toolArgs, 0, args.length - 1);

        switch (tool.toLowerCase()) {
            case "jmt":
                System.out.println("=== JMT Load Tester (RocksDB) ===\n");
                JmtLoadTester.main(toolArgs);
                break;

            case "jmt-rdbms":
                System.out.println("=== JMT RDBMS Load Tester (H2/SQLite/PostgreSQL) ===\n");
                RdbmsJmtLoadTester.main(toolArgs);
                break;

            case "jmt-concurrent":
                System.out.println("=== JMT Concurrent Load Tester (RocksDB) ===\n");
                JmtConcurrentLoadTester.main(toolArgs);
                break;

            case "mpt":
                System.out.println("=== MPT Load Tester (RocksDB) ===\n");
                MptLoadTester.main(toolArgs);
                break;

            case "mpt-rdbms":
                System.out.println("=== MPT RDBMS Load Tester (H2/SQLite/PostgreSQL) ===\n");
                RdbmsMptLoadTester.main(toolArgs);
                break;

            case "gc":
                System.out.println("=== MPT Garbage Collection Tool ===\n");
                GcTool.main(toolArgs);
                break;

            default:
                System.err.println("Unknown tool: " + tool);
                System.err.println("Run with 'help' to see available tools.");
                System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("Cardano Client VDS Load Testing Tools");
        System.out.println("=====================================\n");
        System.out.println("Comprehensive load testing and benchmarking for Verifiable Data Structures");
        System.out.println("(JMT, MPT, MPF) across all storage backends (RocksDB, H2, SQLite, PostgreSQL).\n");

        System.out.println("Usage:");
        System.out.println("  java -jar cardano-client-vds-load-tools-VERSION-all.jar [tool] [options]\n");

        System.out.println("Available Tools:");
        System.out.println("  jmt              - Jellyfish Merkle Tree with RocksDB backend");
        System.out.println("  jmt-rdbms        - Jellyfish Merkle Tree with H2/SQLite/PostgreSQL backend");
        System.out.println("  jmt-concurrent   - JMT concurrent load testing with multiple threads");
        System.out.println("  mpt              - Merkle Patricia Trie with RocksDB backend");
        System.out.println("  mpt-rdbms        - Merkle Patricia Trie with H2/SQLite/PostgreSQL backend");
        System.out.println("  gc               - Garbage collection tool for MPT (refcount/mark-sweep)");
        System.out.println("  help             - Show this help message\n");

        System.out.println("Quick Examples:");
        System.out.println();
        System.out.println("  # JMT with RocksDB - 100k operations");
        System.out.println("  java -jar cardano-client-vds-load-tools.jar jmt \\");
        System.out.println("      --records=100000 --batch=1000 --rocksdb=/tmp/jmt-test");
        System.out.println();
        System.out.println("  # JMT with PostgreSQL");
        System.out.println("  java -jar cardano-client-vds-load-tools.jar jmt-rdbms \\");
        System.out.println("      --records=100000 --batch=1000 --db=postgresql \\");
        System.out.println("      --db-host=localhost --db-name=testdb \\");
        System.out.println("      --db-user=postgres --db-password=secret");
        System.out.println();
        System.out.println("  # MPT with RocksDB and deletes");
        System.out.println("  java -jar cardano-client-vds-load-tools.jar mpt \\");
        System.out.println("      --records=100000 --batch=1000 --rocksdb=/tmp/mpt \\");
        System.out.println("      --delete-ratio=0.1 --secure");
        System.out.println();
        System.out.println("  # MPT with H2 database");
        System.out.println("  java -jar cardano-client-vds-load-tools.jar mpt-rdbms \\");
        System.out.println("      --records=100000 --batch=1000 --db=h2 --path=/tmp/mpt-h2");
        System.out.println();
        System.out.println("  # JMT concurrent testing with 8 threads");
        System.out.println("  java -jar cardano-client-vds-load-tools.jar jmt-concurrent \\");
        System.out.println("      --threads=8 --records=1000000 --batch=1000 --rocksdb=/tmp/jmt-concurrent");
        System.out.println();
        System.out.println("  # Run garbage collection on MPT");
        System.out.println("  java -jar cardano-client-vds-load-tools.jar gc \\");
        System.out.println("      --db=/tmp/mpt-rocksdb --gc-type=refcount --version=10000");
        System.out.println();
        System.out.println("Getting Tool-Specific Help:");
        System.out.println("  Add --help after the tool name for detailed options:");
        System.out.println("  java -jar cardano-client-vds-load-tools.jar jmt --help");
        System.out.println("  java -jar cardano-client-vds-load-tools.jar mpt-rdbms --help");
        System.out.println();
        System.out.println("Common Options (vary by tool):");
        System.out.println("  --records=N          Total operations to perform");
        System.out.println("  --batch=N            Operations per batch");
        System.out.println("  --value-size=N       Value size in bytes");
        System.out.println("  --rocksdb=PATH       RocksDB directory (for RocksDB tools)");
        System.out.println("  --db=TYPE            Database type: h2, sqlite, postgresql (for RDBMS tools)");
        System.out.println("  --db-host=HOST       Database host (for PostgreSQL)");
        System.out.println("  --db-user=USER       Database username (for PostgreSQL)");
        System.out.println("  --db-password=PASS   Database password (for PostgreSQL)");
        System.out.println("  --memory             Use in-memory store (baseline comparison)");
        System.out.println();
        System.out.println("Documentation:");
        System.out.println("  Full documentation: verified-structures/load-tools/README.md");
        System.out.println("  GitHub: https://github.com/bloxbean/cardano-client-lib");
    }
}
