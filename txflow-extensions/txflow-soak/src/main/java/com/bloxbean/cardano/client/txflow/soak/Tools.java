package com.bloxbean.cardano.client.txflow.soak;

/**
 * Entry point for TxFlow / TxStream soak tooling.
 *
 * <p>These are long-running operational tools, not tests: a soak run is expected to last hours
 * or days, on a machine next to a devnet or testnet, against a pinned published version of the
 * library. They are deliberately outside the JUnit suites so a soak can never run in CI, and so
 * the JVM — heap in particular — is under the operator's control rather than a test worker's.
 *
 * <p><b>Usage</b></p>
 * <pre>
 * java -jar cardano-client-txflow-soak-VERSION-all.jar [tool] [options]
 *
 * Available tools:
 *   txstream   - sustained TxStream submission with end-of-run reconciliation
 *   reconcile  - post-mortem reconciliation of an existing run's data directory
 *   help       - show this message
 * </pre>
 *
 * <p><b>Examples</b></p>
 * <pre>
 * # 30 minute smoke soak against a local Yaci DevKit
 * java -jar cardano-client-txflow-soak.jar txstream --duration=30m --rate=2
 *
 * # Overnight run, constrained heap so a leak shows up as an OOM rather than a slow trend
 * java -Xmx512m -jar cardano-client-txflow-soak.jar txstream \
 *      --duration=12h --rate=10 --data=/var/soak/run-17
 *
 * # Batching planner, more lanes
 * java -jar cardano-client-txflow-soak.jar txstream \
 *      --duration=2h --rate=20 --planner=batching --window=25
 * </pre>
 *
 * <p>Every run writes a CSV sample file and a final reconciliation report under {@code --data},
 * so a run can be analysed after the fact without re-running it.
 */
public final class Tools {

    private Tools() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || isHelp(args[0])) {
            printHelp();
            System.exit(args.length == 0 ? 1 : 0);
        }

        String tool = args[0];
        String[] toolArgs = new String[args.length - 1];
        System.arraycopy(args, 1, toolArgs, 0, args.length - 1);

        switch (tool.toLowerCase()) {
            case "txstream":
                TxStreamSoak.main(toolArgs);
                break;
            case "reconcile":
                ReconcileTool.main(toolArgs);
                break;
            default:
                System.err.println("Unknown tool: " + tool);
                System.err.println();
                printHelp();
                System.exit(1);
        }
    }

    private static boolean isHelp(String arg) {
        return "help".equals(arg) || "--help".equals(arg) || "-h".equals(arg);
    }

    private static void printHelp() {
        System.out.println("TxFlow / TxStream soak tools");
        System.out.println();
        System.out.println("  java -jar cardano-client-txflow-soak-VERSION-all.jar [tool] [options]");
        System.out.println();
        System.out.println("Tools:");
        System.out.println("  txstream   sustained TxStream submission + reconciliation");
        System.out.println("  reconcile  post-mortem reconciliation of an existing run's --data dir");
        System.out.println("  help       show this message");
        System.out.println();
        System.out.println("Run a tool with --help for its options, e.g.:");
        System.out.println("  java -jar cardano-client-txflow-soak.jar txstream --help");
    }
}
