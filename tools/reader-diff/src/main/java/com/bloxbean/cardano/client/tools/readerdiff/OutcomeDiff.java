package com.bloxbean.cardano.client.tools.readerdiff;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Compares the outcomes of one input on the two sides ({@link Probes}). */
final class OutcomeDiff {
    static final String EQUAL = "A/A equal";
    static final String DIFFERENT = "A/A different";
    static final String ALLOWED = "A/A allowed";
    static final String EXPECTED_MISMATCH = "A/A expected-mismatch";
    private static final int VALUE_LIMIT = 300;

    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE)
                    .maxStringLength(Integer.MAX_VALUE).maxNumberLength(Integer.MAX_VALUE).build())
            .build());

    /** One differing leaf: its path with array indexes, and the two values. */
    record FieldDiff(String path, String oldValue, String newValue) {
    }

    /** An expected value checked at a path; null when the input has none (its header cannot be read). */
    record Check(String path, String expected, String oldValue, String newValue) {
        boolean newMatches() {
            return expected != null && expected.equals(newValue);
        }

        boolean oldMatches() {
            return expected != null && expected.equals(oldValue);
        }
    }

    /**
     * @param pair    the outcome pair, such as {@code A/R} (old accepted, new rejected), or one of the {@code A/A} kinds
     * @param first   the field to show: the first that is not allowed, otherwise the first expected-value mismatch
     * @param unlisted the paths of all differences not on the list
     * @param allowed the paths of all differences on the list, normalized
     * @param checks  the expected values checked
     */
    record Result(String pair, FieldDiff first, List<String> unlisted, List<String> allowed, List<Check> checks) {
    }

    private OutcomeDiff() {
    }

    static Result compare(String probe, byte[] input, String oldOutcome, String newOutcome, Allow allow) {
        char oldKind = oldOutcome.charAt(0);
        char newKind = newOutcome.charAt(0);
        if (oldKind != 'A' || newKind != 'A') {
            String pair = oldKind + "/" + newKind;
            return new Result(pair, new FieldDiff("", shorten(oldOutcome), shorten(newOutcome)), List.of(), List.of(),
                    List.of());
        }
        String oldJson = oldOutcome.substring(2);
        String newJson = newOutcome.substring(2);
        boolean same = oldJson.equals(newJson);
        List<String> checkedPaths = allow.checkedPaths(probe);
        if (same && checkedPaths.isEmpty())
            return new Result(EQUAL, null, List.of(), List.of(), List.of());

        JsonNode oldTree = tree(oldJson);
        JsonNode newTree = same ? oldTree : tree(newJson);
        List<Check> checks = new ArrayList<>();
        for (String path : checkedPaths)
            checks.add(check(path, input, oldTree, newTree, allow));

        List<String> unlisted = new ArrayList<>();
        List<String> allowed = new ArrayList<>();
        FieldDiff first = null;
        if (!same) {
            List<FieldDiff> diffs = new ArrayList<>();
            walk(oldTree, newTree, "", diffs);
            if (diffs.isEmpty()) // never call texts that differ equal: the walk does not see what differs
                diffs.add(new FieldDiff("<text>", shorten(oldJson), shorten(newJson)));
            for (FieldDiff diff : diffs) {
                String normalized = Allow.normalize(diff.path());
                if (allow.allows(normalized)) {
                    allowed.add(normalized);
                } else {
                    unlisted.add(diff.path());
                    if (first == null)
                        first = diff;
                }
            }
        }
        String pair = unlisted.isEmpty() ? (same ? EQUAL : ALLOWED) : DIFFERENT;
        for (Check check : checks) {
            if (check.expected() != null && !check.newMatches() && unlisted.isEmpty()) {
                pair = EXPECTED_MISMATCH;
                first = new FieldDiff(check.path(), check.oldValue(),
                        check.newValue() + " (expected " + check.expected() + ")");
                break;
            }
        }
        return new Result(pair, first, unlisted, allowed, checks);
    }

    private static Check check(String path, byte[] input, JsonNode oldTree, JsonNode newTree, Allow allow) {
        String expected;
        try {
            expected = allow.expectedValue(path, input);
        } catch (RuntimeException e) {
            expected = null;
        }
        return new Check(path, expected, at(oldTree, path), at(newTree, path));
    }

    private static String at(JsonNode tree, String path) {
        JsonNode node = tree;
        for (String step : path.split("\\."))
            node = node.path(step);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static JsonNode tree(String json) {
        try {
            return JSON.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Collects the differing leaves, in property and index order. */
    private static void walk(JsonNode oldNode, JsonNode newNode, String path, List<FieldDiff> diffs) {
        if (oldNode.equals(newNode))
            return;
        if (oldNode.isObject() && newNode.isObject()) {
            TreeSet<String> names = new TreeSet<>();
            oldNode.fieldNames().forEachRemaining(names::add);
            newNode.fieldNames().forEachRemaining(names::add);
            for (String name : names)
                walk(oldNode.path(name), newNode.path(name), path.isEmpty() ? name : path + "." + name, diffs);
        } else if (oldNode.isArray() && newNode.isArray()) {
            for (int i = 0; i < Math.max(oldNode.size(), newNode.size()); i++)
                walk(oldNode.path(i), newNode.path(i), path + "[" + i + "]", diffs);
        } else {
            diffs.add(new FieldDiff(path, value(oldNode), value(newNode)));
        }
    }

    private static String value(JsonNode node) {
        return node instanceof MissingNode ? "<missing>" : shorten(node.toString());
    }

    private static String shorten(String text) {
        return text.length() <= VALUE_LIMIT ? text : text.substring(0, VALUE_LIMIT) + "... (" + text.length() + " chars)";
    }
}
