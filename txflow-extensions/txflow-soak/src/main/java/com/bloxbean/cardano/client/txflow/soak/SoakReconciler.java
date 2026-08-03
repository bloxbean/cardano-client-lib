package com.bloxbean.cardano.client.txflow.soak;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.txflow.stream.TxStreamItemStatus;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Decides whether a soak run actually succeeded, using three checks that cannot all fail the
 * same way.
 *
 * <ol>
 *   <li><b>Item attribution</b> — every submitted item reached a terminal state, every confirmed
 *       item carries a transaction hash, and those transactions exist on chain. Catches work
 *       that was silently dropped.</li>
 *   <li><b>Value conservation</b> — each recipient's on-chain balance delta equals the sum of the
 *       payments the run believes it made to them. This is the only check that catches a
 *       <em>double payment</em>: a duplicated payout leaves every item looking perfectly
 *       {@code CONFIRMED}, so per-item status can never detect it.</li>
 *   <li><b>Store hygiene</b> — after draining there should be no non-terminal items and no
 *       resource leases left holding funds. Leftovers mean recovery did not complete.</li>
 * </ol>
 *
 * <p>The stream's own projection is deliberately <em>not</em> trusted as the oracle. It is one
 * of the things being tested; the chain is the authority.
 */
public final class SoakReconciler {

    public record Report(long submitted, long confirmed, long failed, long cancelled,
                         long recoveryRequired, long unresolvedRecovery,
                         long nonTerminal, long neverRegistered,
                         List<String> missingOnChain,
                         List<String> doublePaid,
                         List<String> underPaid,
                         List<String> paidButNotConfirmed,
                         Map<String, Long> failureKinds,
                         Map<String, String> failureSamples,
                         BigInteger expectedTotalLovelace,
                         BigInteger actualTotalLovelace,
                         int transactionsChecked,
                         boolean checkBudgetExhausted,
                         long orphanResourceLeases,
                         Map<String, Long> storeRows) {

        /**
         * Structural cleanliness: nothing lost, duplicated, or left behind.
         *
         * <p>Deliberately excludes {@link #neverRegistered()} — intents the store never heard
         * of. Whether those are a defect or an expected crash casualty depends on the restart
         * policy, which is the runner's business, not the reconciler's.
         *
         * <p>Also excludes {@link #paidButNotConfirmed()}: those payments DID land, are counted
         * in the expected total, and conserve value — the discrepancy is the reported status,
         * not the money. They are surfaced prominently as library-mislabel evidence instead of
         * being blamed on a recipient as a double payment.
         */
        /** A verdict is only authoritative when every planned chain check actually ran. */
        public boolean isConclusive() {
            return !checkBudgetExhausted;
        }

        public boolean isClean() {
            return nonTerminal == 0
                    && unresolvedRecovery == 0
                    && missingOnChain.isEmpty()
                    && doublePaid.isEmpty()
                    && underPaid.isEmpty()
                    && orphanResourceLeases == 0
                    && expectedTotalLovelace.equals(actualTotalLovelace);
        }
    }

    private final BackendService backend;
    private final String jdbcUrl;
    private final int maxTransactionChecks;

    public SoakReconciler(BackendService backend, String jdbcUrl, int maxTransactionChecks) {
        this.backend = backend;
        this.jdbcUrl = jdbcUrl;
        this.maxTransactionChecks = maxTransactionChecks;
    }

    /**
     * @param ledger   what the run asked for and was told
     * @param addressOf resolves a recipient index to its address
     */
    public Report reconcile(ExpectedLedger ledger, IntFunction<String> addressOf) {
        // An entry with no status at all means the durable store has never heard of it:
        // journalled, then the process died before the submit landed.
        long neverRegistered = ledger.entries().stream().filter(e -> e.status() == null).count();
        long confirmed = ledger.countWith(TxStreamItemStatus.CONFIRMED);
        long failed = ledger.countWith(TxStreamItemStatus.FAILED);
        long cancelled = ledger.countWith(TxStreamItemStatus.CANCELLED);
        long recoveryRequired = ledger.countWith(TxStreamItemStatus.RECOVERY_REQUIRED);

        int checked = 0;
        boolean checkBudgetExhausted = false;

        // ---- 1. the landed-without-confirmed check runs FIRST -------------------
        // A FAILED or RECOVERY_REQUIRED item that retained a hash is a claim about the
        // chain, and the chain gets the last word. A payment that landed anyway must be
        // counted in the expected totals — otherwise the recipient's honest balance shows
        // up as a phantom DOUBLE PAID, and the tool blames the wrong party. This is
        // exactly what the first preprod soak produced: 10 confirmation-timeout items
        // settled FAILED whose transactions were all on chain.
        //
        // Ordering matters: this pass feeds the value-conservation arithmetic, so it gets
        // the check budget before the (much larger, sampling-tolerant) confirmed-hash
        // sweep. Run the sweep first and a long run starves this pass, which resurrects
        // the phantom verdict the pass exists to prevent.
        List<String> paidButNotConfirmed = new ArrayList<>();
        Map<Integer, BigInteger> landedUnreported = new LinkedHashMap<>();
        long resolvedRecovery = 0;
        // Spend a tight budget where the money is likeliest to be: RECOVERY_REQUIRED means
        // "submitted, disposition unknown" and timeout-classified FAILED items are the known
        // mislabel shape — both far more likely to be on chain than a node-side rejection.
        List<ExpectedLedger.Entry> candidates = new ArrayList<>();
        for (ExpectedLedger.Entry entry : ledger.entries()) {
            if (entry.status() == TxStreamItemStatus.CONFIRMED) continue;
            if (entry.txHash() == null || entry.txHash().isBlank()) continue;
            candidates.add(entry);
        }
        candidates.sort((a, b) -> Integer.compare(landedLikelihoodRank(a), landedLikelihoodRank(b)));
        for (ExpectedLedger.Entry entry : candidates) {
            if (checked >= maxTransactionChecks) {
                checkBudgetExhausted = true;
                break;
            }
            checked++;
            if (existsOnChain(entry.txHash())) {
                landedUnreported.merge(entry.recipient(), entry.lovelace(), BigInteger::add);
                paidButNotConfirmed.add(String.format("%s reported %s but tx %s IS on chain (%s lovelace)",
                        entry.orderId(), entry.status(), entry.txHash(), entry.lovelace()));
                if (entry.status() == TxStreamItemStatus.RECOVERY_REQUIRED) resolvedRecovery++;
            }
        }
        // RECOVERY_REQUIRED settles the receipt, but it is NOT a final disposition
        // (TxStreamItemResult documents exactly that). An RR item this pass could not
        // prove on chain — no hash, hash not found, or budget exhausted before its turn —
        // is unresolved, and a run with unresolved recovery must not read CLEAN: the
        // money may move after the verdict is printed.
        long unresolvedRecovery = recoveryRequired - resolvedRecovery;

        // ---- 1b. confirmed-hash attribution (sampling-tolerant) ------------------
        List<String> missingOnChain = new ArrayList<>();
        Set<String> hashes = new LinkedHashSet<>();
        for (ExpectedLedger.Entry entry : ledger.entries()) {
            if (entry.status() != TxStreamItemStatus.CONFIRMED) continue;
            if (entry.txHash() == null || entry.txHash().isBlank()) {
                missingOnChain.add(entry.orderId() + " (confirmed with no tx hash)");
            } else {
                hashes.add(entry.txHash());
            }
        }
        for (String hash : hashes) {
            if (checked >= maxTransactionChecks) {
                checkBudgetExhausted = true;
                break;
            }
            checked++;
            if (!existsOnChain(hash)) {
                missingOnChain.add("tx " + hash + " reported confirmed but not found on chain");
            }
        }

        // ---- 1c. failure-reason histogram (FAILED and RECOVERY_REQUIRED both carry
        // the diagnostically interesting messages) ---------------------------------
        Map<String, Long> failureKinds = new LinkedHashMap<>();
        Map<String, String> failureSamples = new LinkedHashMap<>();
        for (ExpectedLedger.Entry entry : ledger.entries()) {
            if (entry.status() != TxStreamItemStatus.FAILED
                    && entry.status() != TxStreamItemStatus.RECOVERY_REQUIRED) continue;
            String kind = classifyFailure(entry.error());
            failureKinds.merge(kind, 1L, Long::sum);
            failureSamples.putIfAbsent(kind, entry.error() == null ? "(no message)"
                    : entry.error().replaceAll("\\s+", " ").trim());
        }

        // ---- 2. value conservation, per recipient --------------------------------
        List<String> doublePaid = new ArrayList<>();
        List<String> underPaid = new ArrayList<>();
        BigInteger expectedTotal = BigInteger.ZERO;
        BigInteger actualTotal = BigInteger.ZERO;

        for (Integer recipient : ledger.recipients()) {
            BigInteger expected = ledger.expectedDelta(recipient)
                    .add(landedUnreported.getOrDefault(recipient, BigInteger.ZERO));
            BigInteger actual = balanceOf(addressOf.apply(recipient))
                    .subtract(ledger.baselineOf(recipient));
            expectedTotal = expectedTotal.add(expected);
            actualTotal = actualTotal.add(actual);

            int cmp = actual.compareTo(expected);
            if (cmp > 0) {
                doublePaid.add(String.format(
                        "recipient %d received %s more lovelace than expected (expected %s, got %s)",
                        recipient, actual.subtract(expected), expected, actual));
            } else if (cmp < 0) {
                underPaid.add(String.format(
                        "recipient %d received %s less lovelace than expected (expected %s, got %s)",
                        recipient, expected.subtract(actual), expected, actual));
            }
        }

        // ---- 3. store hygiene ----------------------------------------------------
        Map<String, Long> storeRows = storeRowCounts();
        long orphanLeases = storeRows.getOrDefault("txflow_resource_lease", 0L);

        return new Report(ledger.submittedCount(), confirmed, failed, cancelled,
                recoveryRequired, unresolvedRecovery,
                ledger.nonTerminalCount() - neverRegistered, neverRegistered,
                missingOnChain, doublePaid, underPaid, paidButNotConfirmed,
                failureKinds, failureSamples,
                expectedTotal, actualTotal, checked, checkBudgetExhausted,
                orphanLeases, storeRows);
    }

    /** Lower rank = check first. See the pass-1 candidate ordering comment. */
    private static int landedLikelihoodRank(ExpectedLedger.Entry entry) {
        if (entry.status() == TxStreamItemStatus.RECOVERY_REQUIRED) return 0;
        if ("CONFIRMATION_TIMEOUT".equals(classifyFailure(entry.error()))) return 1;
        return 2;
    }

    /** Coarse failure classification for the report histogram. */
    static String classifyFailure(String message) {
        if (message == null || message.isBlank()) return "UNKNOWN";
        String m = message.toLowerCase();
        if (m.contains("confirmation timeout")) return "CONFIRMATION_TIMEOUT";
        if (m.contains("all inputs are spent")) return "INPUTS_ALREADY_SPENT";
        if (m.contains("not accepted:")) return "NOT_ACCEPTED_BY_STREAM";
        if (m.contains("transaction failed")) return "SUBMIT_REJECTED";
        return "OTHER";
    }

    private boolean existsOnChain(String txHash) {
        try {
            var result = backend.getTransactionService().getTransaction(txHash);
            return result.isSuccessful() && result.getValue() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Paginating balance — see {@link FundingPlan#balanceOf} for why that matters here. */
    private BigInteger balanceOf(String address) {
        return FundingPlan.balanceOf(backend, address);
    }

    /** Row counts for every txflow_/txstream_ table, so store growth is visible. */
    public Map<String, Long> storeRowCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (jdbcUrl == null) return counts;
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            List<String> tables = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                                 + "WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME")) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            for (String table : tables) {
                String lower = table.toLowerCase();
                if (!lower.startsWith("txflow") && !lower.startsWith("txstream")) continue;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM \"" + table + "\"")) {
                    if (rs.next()) counts.put(lower, rs.getLong(1));
                }
            }
        } catch (Exception e) {
            System.err.println("[reconcile] could not read store: " + e.getMessage());
        }
        return counts;
    }

    /** Total rows across store tables — the number to watch for unbounded growth. */
    public long totalStoreRows() {
        return storeRowCounts().values().stream().mapToLong(Long::longValue).sum();
    }
}
