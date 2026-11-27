package com.bloxbean.cardano.client.tools.readerdiff;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The approved list (ADR 0003 D8): JSON paths where old and new may differ, one per line, with {@code [*]} for any array
 * index, and optionally {@code expected=<name>}, an independently computed value the new side must match:
 * <pre>
 * header.headerBody.blockHash expected=block-header-hash
 * </pre>
 * An allowed field is not dropped: its differences are counted apart, and the expected value is checked on every input
 * of the probes it applies to, whether or not the sides differ. It is computed by the tool from the input's original
 * bytes, on the tool's own {@code CborSpan} and independently of both sides.
 */
final class Allow {
    private record Expected(Set<String> probes, Function<byte[], String> value) {
    }

    private static final Map<String, Expected> EXPECTED = Map.of(
            "block-header-hash", new Expected(Set.of("yaci-block", "yaci:block"),
                    block -> blake2b256(Blocks.headerBytes(block))),
            "header-hash", new Expected(Set.of("yaci:header"), Allow::blake2b256));
    private static final Pattern INDEX = Pattern.compile("\\[\\d+]");

    static final Allow NONE = of(List.of());

    private final Map<String, String> paths = new HashMap<>();

    private Allow() {
    }

    static Allow load(Path file) throws IOException {
        return of(Files.readAllLines(file));
    }

    /** @param lines the lines of the file; blank ones and comments are skipped */
    static Allow of(List<String> lines) {
        Allow allow = new Allow();
        for (String line : lines) {
            String entry = line.strip();
            if (!entry.isEmpty() && !entry.startsWith("#"))
                allow.add(entry);
        }
        return allow;
    }

    private void add(String entry) {
        String[] parts = entry.split("\\s+");
        String expected = null;
        if (parts.length == 2 && parts[1].startsWith("expected=")) {
            expected = parts[1].substring("expected=".length());
            if (!EXPECTED.containsKey(expected))
                throw new IllegalArgumentException("unknown expected value in: " + entry);
            if (parts[0].contains("[*]"))
                throw new IllegalArgumentException("an expected value needs a path without [*]: " + entry);
        } else if (parts.length != 1) {
            throw new IllegalArgumentException("not <path> [expected=<name>]: " + entry);
        }
        paths.put(parts[0], expected);
    }

    /** @return the path with any array index as {@code [*]} */
    static String normalize(String path) {
        return INDEX.matcher(path).replaceAll("[*]");
    }

    boolean allows(String normalizedPath) {
        return paths.containsKey(normalizedPath);
    }

    /** @return the paths with an expected value for this probe */
    List<String> checkedPaths(String probe) {
        List<String> checked = new ArrayList<>();
        paths.forEach((path, expected) -> {
            if (expected != null && EXPECTED.get(expected).probes().contains(probe))
                checked.add(path);
        });
        return checked;
    }

    /** @return the expected value of a checked path for this input; a parse failure means it has none */
    String expectedValue(String path, byte[] input) {
        return EXPECTED.get(paths.get(path)).value().apply(input);
    }

    private static String blake2b256(byte[] bytes) {
        return HexFormat.of().formatHex(Blake2bUtil.blake2bHash256(bytes));
    }
}
