package com.bloxbean.cardano.client.txflow.soak;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

/**
 * Post-mortem reconciliation of a finished (or crashed) soak run — no load generated.
 *
 * <p>Rebuilds the run's expectations from the on-disk journal, reads each item's settled
 * status straight out of the durable store, and re-runs the same three checks the live run
 * ends with, against the same chain. Because the inputs are files plus the chain, the same
 * run can be re-judged after a tool fix — which is exactly why this exists: the first preprod
 * soak was mis-reported by the reconciler itself, and the raw evidence was still sitting in
 * {@code --data} waiting to be re-read.
 *
 * <pre>
 * java -jar cardano-client-txflow-soak.jar reconcile --data=/tmp/soak-preprod-1
 * </pre>
 *
 * <p>Network options and environment variables are the same as {@code txstream}; the chain
 * being consulted must obviously be the one the run executed against.
 */
public final class ReconcileTool {

    public static void main(String[] args) throws Exception {
        SoakOptions options = SoakOptions.parse(args);
        if (options.has("help")) {
            printHelp();
            return;
        }

        Path dataDir = options.path("data", "./soak-data");
        if (!Files.isDirectory(dataDir.resolve("journal"))) {
            System.err.println("No journal under " + dataDir.toAbsolutePath()
                    + " — is --data pointing at a soak run?");
            System.exit(2);
        }

        String backendUrl = firstNonBlank(options.string("url", null),
                System.getenv("CARDANO_BF_URL"), "http://localhost:8080/api/v1/");
        String projectId = firstNonBlank(options.string("project-id", null),
                System.getenv("BF_PROJECT_ID"), "dummy-project-id");
        String mnemonic = firstNonBlank(options.string("mnemonic", null),
                System.getenv("SOAK_MNEMONIC"), TxStreamSoak.DEFAULT_MNEMONIC);
        Network network = "mainnet".equalsIgnoreCase(options.string("network", "testnet"))
                ? Networks.mainnet() : Networks.testnet();
        int maxTxChecks = options.integer("max-tx-checks", 2000);
        // IFEXISTS: a post-mortem must never CREATE a store — a journal dir without one
        // means the wrong --data, and a fresh empty DB would turn that into a confusing
        // "table not found" crash instead of a clear refusal.
        String jdbcUrl = "jdbc:h2:file:" + dataDir.toAbsolutePath().resolve("txflow-soak")
                + ";AUTO_SERVER=TRUE;IFEXISTS=TRUE";

        System.out.println("reconciling     : " + dataDir.toAbsolutePath());
        System.out.println("backend         : " + backendUrl);
        boolean defaultUrl = options.string("url", null) == null
                && System.getenv("CARDANO_BF_URL") == null;
        boolean defaultMnemonic = options.string("mnemonic", null) == null
                && System.getenv("SOAK_MNEMONIC") == null;
        if (defaultUrl || defaultMnemonic) {
            System.out.println();
            System.out.println("WARNING: using the DEFAULT " + (defaultUrl && defaultMnemonic
                    ? "backend AND mnemonic" : defaultUrl ? "backend (local DevKit)"
                    : "mnemonic (DevKit test phrase)") + ".");
            System.out.println("         The oracle must be the chain this run executed against —");
            System.out.println("         reconciling a preprod run against these defaults produces a");
            System.out.println("         confidently wrong verdict. Set CARDANO_BF_URL / SOAK_MNEMONIC");
            System.out.println("         (or --url / --mnemonic) if this run was not a local-devnet run.");
            System.out.println();
        }

        BackendService backend = new BFBackendService(backendUrl, projectId);
        SoakJournal journal = new SoakJournal(dataDir);

        // Expectations from the journal; verdicts from the durable store.
        ExpectedLedger ledger = new ExpectedLedger();
        journal.readBaselines().forEach(ledger::captureBaseline);
        List<SoakJournal.Intent> intents = journal.readIntents();
        intents.forEach(i -> ledger.recordSubmitted(i.orderId(), i.recipient(), i.lovelace()));
        System.out.println("journal         : " + intents.size() + " intent(s), "
                + ledger.recipients().size() + " recipient baseline(s)");

        int settled = loadStoreVerdicts(jdbcUrl, ledger);
        System.out.println("store           : " + settled + " item verdict(s) loaded");
        System.out.println("checking against the chain (may take a while at "
                + maxTxChecks + " max lookups)...");
        System.out.println();

        SoakReconciler reconciler = new SoakReconciler(backend, jdbcUrl, maxTxChecks);
        SoakReconciler.Report report = reconciler.reconcile(ledger,
                i -> new Account(network, mnemonic, TxStreamSoak.RECIPIENT_BASE + i).baseAddress());

        String rendered = render(report);
        System.out.println(rendered);
        Files.writeString(dataDir.resolve("reconcile-report.txt"), rendered);
        System.out.println("report written  : " + dataDir.resolve("reconcile-report.txt"));
        System.exit(report.isClean() && report.isConclusive() ? 0 : 1);
    }

    /** Item verdicts, straight out of {@code txstream_item}. */
    private static int loadStoreVerdicts(String jdbcUrl, ExpectedLedger ledger) throws Exception {
        int loaded = 0;
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT ITEM_ID, STATUS, TRANSACTION_HASH, ERROR_MESSAGE FROM TXSTREAM_ITEM");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String status = rows.getString(2);
                if (status == null) continue;
                TxStreamItemStatus parsed;
                try {
                    parsed = TxStreamItemStatus.valueOf(status);
                } catch (IllegalArgumentException unknownStatus) {
                    // A store written by a different library version may know statuses this
                    // tool does not; a post-mortem degrades per row, it does not abort.
                    System.out.println("  [store] skipping item " + rows.getString(1)
                            + " with unknown status '" + status + "'");
                    continue;
                }
                ledger.recordOutcome(rows.getString(1), parsed,
                        rows.getString(3), rows.getString(4));
                loaded++;
            }
        }
        return loaded;
    }

    private static String render(SoakReconciler.Report r) {
        StringBuilder out = new StringBuilder();
        String line = "=".repeat(78);
        out.append('\n').append(line).append('\n');
        out.append("  POST-MORTEM RECONCILIATION\n");
        out.append(line).append('\n');
        out.append(String.format("  submitted (journal)  %d%n", r.submitted()));
        out.append(String.format("  confirmed            %d%n", r.confirmed()));
        out.append(String.format("  failed               %d%n", r.failed()));
        out.append(String.format("  cancelled            %d%n", r.cancelled()));
        out.append(String.format("  recovery required    %d%s%n", r.recoveryRequired(),
                r.unresolvedRecovery() > 0
                        ? "   (" + r.unresolvedRecovery() + " UNRESOLVED — not proven on chain;"
                            + " reconcile before trusting this run)"
                        : r.recoveryRequired() > 0 ? "   (all resolved on chain)" : ""));
        out.append(String.format("  non-terminal         %d%n", r.nonTerminal()));
        out.append(String.format("  never registered     %d%n", r.neverRegistered()));
        out.append('\n');
        out.append("  -- value conservation (the double-pay check) --\n");
        out.append(String.format("  expected total       %s lovelace%n", r.expectedTotalLovelace()));
        out.append(String.format("  actual total         %s lovelace%n", r.actualTotalLovelace()));
        out.append(String.format("  transactions checked %d on chain%n", r.transactionsChecked()));
        out.append('\n');
        if (!r.failureKinds().isEmpty()) {
            out.append("  -- failure reasons --\n");
            r.failureKinds().entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .forEach(e -> {
                        out.append(String.format("  %-24s %d%n", e.getKey(), e.getValue()));
                        String sample = r.failureSamples().get(e.getKey());
                        if (sample != null) {
                            out.append("      e.g. ").append(sample.length() > 160
                                    ? sample.substring(0, 160) + " ..." : sample).append('\n');
                        }
                    });
            out.append('\n');
        }
        findings(out, "LOST / NOT ON CHAIN", r.missingOnChain());
        findings(out, "DOUBLE PAID", r.doublePaid());
        findings(out, "UNDER PAID", r.underPaid());
        findings(out, "PAID BUT NOT REPORTED CONFIRMED", r.paidButNotConfirmed());
        if (!r.paidButNotConfirmed().isEmpty()) {
            out.append("      These transactions landed; the money is counted and conserves.\n");
            out.append("      The discrepancy is the reported STATUS, not a double payment.\n");
            out.append("      Do not retry these items.\n");
        }
        out.append(line).append('\n');
        if (!r.isConclusive()) {
            out.append("  RESULT: INCONCLUSIVE — the on-chain check budget ran out before every\n");
            out.append("  planned lookup completed. Raise --max-tx-checks and reconcile again.\n");
        }
        if (r.isClean()) {
            out.append(r.paidButNotConfirmed().isEmpty()
                    ? "  RESULT: CLEAN — nothing lost, nothing paid twice, nothing left behind\n"
                    : "  RESULT: CLEAN (money conserved) — but " + r.paidButNotConfirmed().size()
                        + " item(s) were paid on chain without being reported CONFIRMED\n");
        } else {
            out.append("  RESULT: DISCREPANCIES FOUND — see above\n");
        }
        out.append(line).append('\n');
        return out.toString();
    }

    private static void findings(StringBuilder out, String title, List<String> items) {
        if (items.isEmpty()) {
            out.append(String.format("  %-32s none%n", title));
            return;
        }
        out.append(String.format("  %s (%d):%n", title, items.size()));
        items.stream().limit(20).forEach(f -> out.append("    - ").append(f).append('\n'));
        if (items.size() > 20) {
            out.append(String.format("    ... and %d more%n", items.size() - 20));
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static void printHelp() {
        System.out.println("reconcile — post-mortem reconciliation of a soak run's --data directory");
        System.out.println();
        System.out.println("  java -jar cardano-client-txflow-soak.jar reconcile --data=/tmp/soak-run-1");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --data=./soak-data     the run's data dir (journal + H2 store)");
        System.out.println("  --url / --project-id / --mnemonic / --network   same as txstream;");
        System.out.println("                         must target the chain the run executed against");
        System.out.println("  --max-tx-checks=2000   cap on per-transaction on-chain lookups");
        System.out.println();
        System.out.println("Exit codes: 0 clean, 1 discrepancies, 2 no run data found.");
    }

    private ReconcileTool() {
    }
}
