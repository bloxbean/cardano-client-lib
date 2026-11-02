package com.bloxbean.cardano.client.tools.chainparse;

import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** The resumable run state: the last processed point, counters per era, issue counts and maxima. */
final class State {
    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public String network;
    public Map<String, String> identity;  // what produced the counters: see ChainParseCheck.identity
    public long lastSlot = -1;
    public String lastHash;
    public long lastBlockNo;
    public long processedBlocks;
    public long elapsedMillis;            // parse-active wall time accumulated across restarts
    public Map<String, Map<String, Long>> eras = new LinkedHashMap<>();
    public Map<String, Long> issueCounts = new TreeMap<>();
    public Map<String, Object> maxima = new LinkedHashMap<>();

    synchronized void inc(String era, String key, long n) {
        if (n == 0) return;
        eras.computeIfAbsent(era, k -> new TreeMap<>()).merge(key, n, Long::sum);
    }

    synchronized void max(String key, long value, String tx, long slot) {
        Object cur = maxima.get(key);
        long v = cur == null ? -1 : ((Number) ((Map<?, ?>) cur).get("value")).longValue();
        if (value > v)
            maxima.put(key, new LinkedHashMap<>(Map.of("value", value, "tx", tx, "slot", slot)));
    }

    synchronized void countBlock() {
        processedBlocks++;
    }

    /** Counts one issue of {@code check}; returns the new count. */
    synchronized long countIssue(String check) {
        return issueCounts.merge(check, 1L, Long::sum);
    }

    /** The last block of a fully processed batch: the resume point. */
    void advance(long slot, String hash, long blockNo) {
        lastSlot = slot;
        lastHash = hash;
        lastBlockNo = blockNo;
    }

    void save(Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        JSON.writeValue(tmp.toFile(), this);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Loads a saved run; it must have this identity, as counters of another CCL build or check option don't add up. */
    static State resume(Path file, Map<String, String> identity) throws IOException {
        State s = JSON.readValue(file.toFile(), State.class);
        if (!identity.equals(s.identity))
            throw new IllegalStateException("the run in " + file.toAbsolutePath().getParent() + " was made by "
                    + s.identity + ", not " + identity + ": start over with --fresh or use another --run-dir");
        return s;
    }
}

/** Mismatches and exceptions, appended to issues.jsonl: full detail plus CBOR for the first 200 of each check. */
final class Issues {
    private static final ObjectMapper LINE = new ObjectMapper();
    private final State state;
    private final BufferedWriter out;
    private final Path blockDir;

    Issues(State state, Path file, Path blockDir) throws IOException {
        this.state = state;
        this.out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        this.blockDir = blockDir;
    }

    synchronized void report(String check, Map<String, Object> m, byte[] txCbor, byte[] blockCbor) {
        long n = state.countIssue(check);
        if (n > 5000)
            return;
        try {
            if (n <= 200 && txCbor != null)
                m.put("txCbor", HexUtil.encodeHexString(txCbor));
            if (n <= 50 && blockCbor != null) {
                Files.createDirectories(blockDir);
                Path f = blockDir.resolve(m.get("slot") + "-" + m.get("block") + ".cbor.hex");
                Files.writeString(f, HexUtil.encodeHexString(blockCbor));
                m.put("blockCborFile", f.toString());
            }
            out.write(LINE.writeValueAsString(m));
            out.newLine();
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
