package com.bloxbean.cardano.client.tools.readerdiff;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * The counts of a run, {@code <out>/summary.txt}, and the first differences of each kind in
 * {@code <out>/differences.jsonl}. Outcome pairs in {@link #CLEAN} are not differences: the rest are.
 */
final class Report implements AutoCloseable {
    static final Set<String> CLEAN = Set.of(OutcomeDiff.EQUAL, OutcomeDiff.ALLOWED, "R/R", "C/C");
    private static final int MAX_PER_KIND = 200;
    private static final int MAX_PATHS = 20;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path out;
    private final String header;
    private final BufferedWriter differences;
    private final ConcurrentSkipListMap<String, LongAdder> pairs = new ConcurrentSkipListMap<>();
    private final ConcurrentSkipListMap<String, LongAdder> rejections = new ConcurrentSkipListMap<>();
    private final ConcurrentSkipListMap<String, LongAdder> allowed = new ConcurrentSkipListMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> written = new ConcurrentHashMap<>();
    private final LongAdder inputs = new LongAdder();
    private final LongAdder differenceCount = new LongAdder();
    private final long started = System.currentTimeMillis();
    private final List<String> notes = new ArrayList<>();

    Report(Path out, String header) throws IOException {
        this.out = out;
        this.header = header;
        Files.createDirectories(out);
        differences = new BufferedWriter(new java.io.OutputStreamWriter(Files.newOutputStream(out.resolve("differences.jsonl")), java.nio.charset.StandardCharsets.UTF_8));
    }

    /** One top-level input done: a block, or a dataset sample. */
    void inputDone() {
        inputs.increment();
    }

    /** A line for the summary. */
    void note(String note) {
        notes.add(note);
    }

    void record(String probe, String group, String context, byte[] input, OutcomeDiff.Result result) throws IOException {
        count(pairs, probe + "\t" + group + "\t" + result.pair());
        if (result.pair().startsWith("R/") || result.pair().endsWith("/R"))
            count(rejections, probe + "\t" + result.first().oldValue() + " -> " + result.first().newValue());
        result.allowed().forEach(path -> count(allowed, path + "\tdiffer"));
        for (OutcomeDiff.Check check : result.checks()) {
            if (check.expected() == null) {
                count(allowed, check.path() + "\texpected unavailable");
            } else {
                count(allowed, check.path() + (check.newMatches() ? "\tnew matches expected" : "\tnew differs from expected"));
                count(allowed, check.path() + (check.oldMatches() ? "\told matches expected" : "\told differs from expected"));
            }
        }
        if (!CLEAN.contains(result.pair())) {
            differenceCount.increment();
            writeDifference(probe, group, context, input, result);
        }
    }

    /** A difference found outside the comparison of two outcomes, such as an input the tool could not split. */
    void recordFailure(String probe, String group, String context, byte[] input, String kind, String detail)
            throws IOException {
        record(probe, group, context, input, new OutcomeDiff.Result(kind, new OutcomeDiff.FieldDiff("", detail, detail),
                List.of(), List.of(), List.of()));
    }

    private void writeDifference(String probe, String group, String context, byte[] input, OutcomeDiff.Result result)
            throws IOException {
        if (written.computeIfAbsent(probe + "\t" + result.pair(), k -> new AtomicInteger()).incrementAndGet()
                > MAX_PER_KIND)
            return;
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("kind", result.pair());
        line.put("probe", probe);
        line.put("group", group);
        line.put("context", context);
        line.put("path", result.first().path());
        line.put("old", result.first().oldValue());
        line.put("new", result.first().newValue());
        line.put("differingFields", result.unlisted().size());
        line.put("firstFields", result.unlisted().subList(0, Math.min(MAX_PATHS, result.unlisted().size())));
        line.put("inputHex", HexFormat.of().formatHex(input));
        String text = JSON.writeValueAsString(line);
        synchronized (differences) {
            differences.write(text);
            differences.newLine();
        }
    }

    private static void count(Map<String, LongAdder> counts, String key) {
        counts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    long differences() {
        return differenceCount.sum();
    }

    String progress() {
        long seconds = Math.max(1, (System.currentTimeMillis() - started) / 1000);
        return inputs.sum() + " inputs, " + inputs.sum() / seconds + "/s, " + differences() + " differences";
    }

    /** Writes the summary, and returns it. */
    String finish() throws IOException {
        synchronized (differences) {
            differences.flush();
        }
        String summary = summary();
        Files.writeString(out.resolve("summary.txt"), new String(String.valueOf(summary).getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8));
        return summary;
    }

    private String summary() {
        StringBuilder text = new StringBuilder(header).append('\n');
        long seconds = Math.max(1, (System.currentTimeMillis() - started) / 1000);
        text.append("inputs ").append(inputs.sum()).append(" in ").append(seconds).append(" s (")
                .append(inputs.sum() / seconds).append("/s)\n");
        notes.forEach(note -> text.append(note).append('\n'));

        Map<String, Map<String, Long>> totals = new TreeMap<>();
        Map<String, Map<String, Long>> byGroup = new TreeMap<>();
        pairs.forEach((key, count) -> {
            String[] parts = key.split("\t");
            totals.computeIfAbsent(parts[0], k -> new TreeMap<>()).merge(parts[2], count.sum(), Long::sum);
            byGroup.computeIfAbsent(parts[0] + "  " + parts[1], k -> new TreeMap<>()).put(parts[2], count.sum());
        });
        text.append("\nper probe:\n");
        totals.forEach((probe, counts) -> text.append("  ").append(probe).append("  ").append(counts).append('\n'));
        text.append("\nper probe and group:\n");
        byGroup.forEach((key, counts) -> text.append("  ").append(key).append("  ").append(counts).append('\n'));

        text.append("\nallowed fields:\n");
        if (allowed.isEmpty())
            text.append("  none seen\n");
        Map<String, Map<String, Long>> byPath = new TreeMap<>();
        allowed.forEach((key, count) -> {
            String[] parts = key.split("\t");
            byPath.computeIfAbsent(parts[0], k -> new TreeMap<>()).put(parts[1], count.sum());
        });
        byPath.forEach((path, counts) -> text.append("  ").append(path).append("  ").append(counts).append('\n'));

        text.append("\nrejections (old -> new):\n");
        if (rejections.isEmpty())
            text.append("  none\n");
        rejections.forEach((key, count) -> text.append("  ").append(key.replace('\t', ' ')).append("  ")
                .append(count.sum()).append('\n'));

        text.append("\ndifferences: ").append(differences()).append(" (the first ").append(MAX_PER_KIND)
                .append(" of each kind per probe are in differences.jsonl)\n");
        return text.toString();
    }

    @Override
    public void close() throws IOException {
        differences.close();
    }
}
