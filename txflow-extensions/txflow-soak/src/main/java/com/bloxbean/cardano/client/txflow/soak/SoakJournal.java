package com.bloxbean.cardano.client.txflow.soak;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * On-disk record of what a soak run intended to do.
 *
 * <p>This exists because of crash chaos. {@code Runtime.halt()} takes the whole JVM with it, so
 * a soak that deliberately crashes itself would lose the very record needed to judge the run —
 * and "we lost our own bookkeeping" is indistinguishable from "the stream lost the work". The
 * journal makes a multi-process soak reconcilable as one run.
 *
 * <p>Only <em>intent</em> is journalled: order id, recipient, amount, lane. Outcomes are
 * deliberately not written, because they can always be recovered afterwards from the durable
 * store and the chain — and recovering them is exactly what is being tested. Writing outcomes
 * here would let the harness mark its own homework.
 *
 * <p>Baselines are captured once, on the first process of a run, and reused by every restart.
 * A per-process baseline would silently absorb any payment made before it.
 */
public final class SoakJournal {

    private final Path baselinesFile;
    private final Path intentsFile;
    private BufferedWriter intentsWriter;

    public SoakJournal(Path dataDir) {
        Path journal = dataDir.resolve("journal");
        this.baselinesFile = journal.resolve("baselines.csv");
        this.intentsFile = journal.resolve("intents.csv");
        try {
            Files.createDirectories(journal);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create journal dir " + journal, e);
        }
    }

    /** True when a previous process already started this run. */
    public boolean hasPriorRun() {
        return Files.exists(baselinesFile);
    }

    // ------------------------------------------------------------------ baselines

    public void writeBaselines(Map<Integer, BigInteger> baselines) {
        StringBuilder sb = new StringBuilder("recipient,lovelace\n");
        baselines.forEach((k, v) -> sb.append(k).append(',').append(v).append('\n'));
        try {
            Files.writeString(baselinesFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write baselines", e);
        }
    }

    public Map<Integer, BigInteger> readBaselines() {
        Map<Integer, BigInteger> out = new LinkedHashMap<>();
        if (!Files.exists(baselinesFile)) return out;
        try {
            for (String line : Files.readAllLines(baselinesFile)) {
                if (line.isBlank() || line.startsWith("recipient")) continue;
                String[] parts = line.split(",");
                out.put(Integer.parseInt(parts[0].trim()), new BigInteger(parts[1].trim()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read baselines", e);
        }
        return out;
    }

    // ------------------------------------------------------------------ intents

    /** One journalled submission intent. */
    public record Intent(String orderId, int recipient, BigInteger lovelace, int lane) {
    }

    public void openIntents() {
        try {
            boolean fresh = !Files.exists(intentsFile);
            intentsWriter = Files.newBufferedWriter(intentsFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (fresh) {
                intentsWriter.write("order_id,recipient,lovelace,lane\n");
                intentsWriter.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open intents journal", e);
        }
    }

    /**
     * Append an intent and flush immediately.
     *
     * <p>Flushing every record is the whole point: a buffered record lost to {@code halt()}
     * would look exactly like work the stream dropped.
     */
    public synchronized void recordIntent(Intent intent) {
        if (intentsWriter == null) return;
        try {
            intentsWriter.write(intent.orderId() + "," + intent.recipient() + ","
                    + intent.lovelace() + "," + intent.lane() + "\n");
            intentsWriter.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not journal intent " + intent.orderId(), e);
        }
    }

    public List<Intent> readIntents() {
        List<Intent> out = new ArrayList<>();
        if (!Files.exists(intentsFile)) return out;
        try {
            for (String line : Files.readAllLines(intentsFile)) {
                if (line.isBlank() || line.startsWith("order_id")) continue;
                String[] p = line.split(",");
                if (p.length < 4) continue;
                out.add(new Intent(p[0].trim(), Integer.parseInt(p[1].trim()),
                        new BigInteger(p[2].trim()), Integer.parseInt(p[3].trim())));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read intents journal", e);
        }
        return out;
    }

    /** Highest sequence number already used, so a restart never reuses an order id. */
    public long highestSequence() {
        long max = 0;
        for (Intent intent : readIntents()) {
            int dash = intent.orderId().lastIndexOf('-');
            if (dash < 0) continue;
            try {
                max = Math.max(max, Long.parseLong(intent.orderId().substring(dash + 1)));
            } catch (NumberFormatException ignored) {
                // non-numeric suffix, skip
            }
        }
        return max;
    }

    public void close() {
        try {
            if (intentsWriter != null) intentsWriter.close();
        } catch (IOException ignored) {
            // closing a journal on the way out must never mask the real outcome
        }
    }
}
