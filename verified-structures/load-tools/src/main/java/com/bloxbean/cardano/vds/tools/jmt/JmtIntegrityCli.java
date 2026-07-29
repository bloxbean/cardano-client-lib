package com.bloxbean.cardano.vds.tools.jmt;

import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityChecker;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityIssue;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityMode;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityReport;
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtStore;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Command-line integrity checker for an existing RocksDB JMT namespace. */
@SuppressWarnings("java:S106") // Console output is the interface of this operator tool.
public final class JmtIntegrityCli {

    private JmtIntegrityCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Runs the checker.
     *
     * @return 0 for a healthy store, 1 for integrity/open failures, and 2 for invalid usage
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        final CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("Invalid arguments: " + e.getMessage());
            printUsage(err);
            return 2;
        }

        if (options.help) {
            printUsage(out);
            return 0;
        }

        Path dbPath = options.rocksDbPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(dbPath) || !Files.isRegularFile(dbPath.resolve("CURRENT"))) {
            err.println("Not an existing RocksDB database: " + dbPath);
            return 1;
        }

        RocksDbJmtStore.Options storeOptions = RocksDbJmtStore.Options.builder()
                .namespace(options.namespace)
                .enableRollbackIndex(options.rollbackIndex)
                .disableWalForBatches(false)
                .syncOnCommit(true)
                .syncOnPrune(true)
                .syncOnTruncate(true)
                .build();

        try (RocksDbJmtStore store = RocksDbJmtStore.open(dbPath.toString(), storeOptions)) {
            JmtIntegrityChecker.Options checkerOptions = JmtIntegrityChecker.Options.builder()
                    .maxRecords(options.maxRecords)
                    .quickNodeSample(options.quickNodeSample)
                    .allVersions(options.allVersions)
                    .versionRange(options.fromVersion, options.toVersion)
                    .build();
            JmtIntegrityReport report = new JmtIntegrityChecker(
                    store, JmtProfile.classicBlake2b256V1()).check(options.mode, checkerOptions);
            printReport(report, out);
            return report.healthy() ? 0 : 1;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            err.println("Integrity check failed: " + safeMessage(e));
            return 1;
        }
    }

    private static void printReport(JmtIntegrityReport report, PrintStream out) {
        out.printf("JMT integrity: %s%n", report.healthy() ? "HEALTHY" : "UNHEALTHY");
        out.printf("Mode: %s, roots: %d, nodes: %d, values: %d, truncated: %s, cancelled: %s%n",
                report.mode(), report.rootsChecked(), report.nodesChecked(), report.valuesChecked(),
                report.truncated(), report.cancelled());
        for (JmtIntegrityIssue issue : report.issues()) {
            StringBuilder context = new StringBuilder();
            issue.version().ifPresent(version -> context.append(" version=").append(version));
            issue.path().ifPresent(path -> context.append(" path=").append(path));
            out.printf("%s %s:%s %s%n", issue.severity(), issue.code(), context, issue.message());
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    private static void printUsage(PrintStream stream) {
        stream.println("Usage: jmt-integrity --rocksdb=PATH [options]");
        stream.println("  --mode=quick|full          Check mode (default: full)");
        stream.println("  --namespace=NAME          RocksDB JMT namespace (default: unnamespaced)");
        stream.println("  --rollback-index=BOOL     Match the store's rollback-index feature (default: true)");
        stream.println("  --max-records=N           Bound records loaded for inspection (default: 1000000)");
        stream.println("  --quick-node-sample=N     Nodes decoded in quick mode (default: 256)");
        stream.println("  --all-versions            Traverse every retained root in full mode");
        stream.println("  --from-version=N          Lowest retained version to traverse (inclusive)");
        stream.println("  --to-version=N            Highest retained version to traverse (inclusive)");
        stream.println("  --help                    Show this help");
    }

    private static final class CliOptions {
        private Path rocksDbPath;
        private String namespace;
        private JmtIntegrityMode mode = JmtIntegrityMode.FULL;
        private boolean rollbackIndex = true;
        private int maxRecords = 1_000_000;
        private int quickNodeSample = 256;
        private boolean allVersions;
        private Long fromVersion;
        private Long toVersion;
        private boolean help;

        private static CliOptions parse(String[] args) {
            CliOptions result = new CliOptions();
            for (String arg : args) {
                if (arg.equals("--help") || arg.equals("-h")) {
                    result.help = true;
                } else if (arg.startsWith("--rocksdb=")) {
                    result.rocksDbPath = Path.of(value(arg));
                } else if (arg.startsWith("--namespace=")) {
                    result.namespace = nonEmpty(value(arg), "namespace");
                } else if (arg.startsWith("--mode=")) {
                    result.mode = parseMode(value(arg));
                } else if (arg.startsWith("--rollback-index=")) {
                    result.rollbackIndex = parseBoolean(value(arg), "rollback-index");
                } else if (arg.startsWith("--max-records=")) {
                    result.maxRecords = positiveInt(value(arg), "max-records");
                } else if (arg.startsWith("--quick-node-sample=")) {
                    result.quickNodeSample = nonNegativeInt(value(arg), "quick-node-sample");
                } else if (arg.equals("--all-versions")) {
                    result.allVersions = true;
                } else if (arg.startsWith("--from-version=")) {
                    result.fromVersion = nonNegativeLong(value(arg), "from-version");
                } else if (arg.startsWith("--to-version=")) {
                    result.toVersion = nonNegativeLong(value(arg), "to-version");
                } else {
                    throw new IllegalArgumentException("unknown option " + arg);
                }
            }
            if (!result.help && result.rocksDbPath == null) {
                throw new IllegalArgumentException("--rocksdb=PATH is required");
            }
            if (result.allVersions && (result.fromVersion != null || result.toVersion != null)) {
                throw new IllegalArgumentException("--all-versions cannot be combined with a version range");
            }
            if (result.fromVersion != null && result.toVersion != null
                    && result.fromVersion > result.toVersion) {
                throw new IllegalArgumentException("from-version must be <= to-version");
            }
            return result;
        }

        private static String value(String argument) {
            return argument.substring(argument.indexOf('=') + 1);
        }

        private static String nonEmpty(String value, String name) {
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            return value;
        }

        private static JmtIntegrityMode parseMode(String value) {
            try {
                return JmtIntegrityMode.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("mode must be quick or full", e);
            }
        }

        private static boolean parseBoolean(String value, String name) {
            if (value.equalsIgnoreCase("true")) {
                return true;
            }
            if (value.equalsIgnoreCase("false")) {
                return false;
            }
            throw new IllegalArgumentException(name + " must be true or false");
        }

        private static int positiveInt(String value, String name) {
            int parsed = nonNegativeInt(value, name);
            if (parsed == 0) {
                throw new IllegalArgumentException(name + " must be > 0");
            }
            return parsed;
        }

        private static int nonNegativeInt(String value, String name) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 0) {
                    throw new IllegalArgumentException(name + " must be >= 0");
                }
                return parsed;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " must be an integer", e);
            }
        }

        private static long nonNegativeLong(String value, String name) {
            try {
                long parsed = Long.parseLong(value);
                if (parsed < 0) {
                    throw new IllegalArgumentException(name + " must be >= 0");
                }
                return parsed;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " must be an integer", e);
            }
        }
    }
}
