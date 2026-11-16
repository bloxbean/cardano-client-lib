package com.bloxbean.cardano.client.ledger.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Whether a dataset directory is exactly a revision of cardano-cbor-dataset (ADR 0005 D8): the {@code dataset}
 * directory of a git checkout whose {@code HEAD} is that revision, holding no file the revision does not have (untracked
 * or ignored), and whose every tracked file has the revision's content. The contents are hashed and compared with the
 * revision's blobs, so neither index flags ({@code assume-unchanged}, {@code skip-worktree}) nor a file edited with its
 * size and time kept can hide a change, as they can from {@code git status}. It runs the {@code git} executable, which
 * fetching the dataset needs anyway; it adds no Git library.
 */
public final class DatasetCheckout {

    private static final long TIMEOUT_SECONDS = 300;
    private static final String REGULAR_FILE = "100644";
    private static final String EXECUTABLE_FILE = "100755";

    private DatasetCheckout() {
    }

    /**
     * @param dataset  the dataset's {@code dataset} directory, as the gate and Yano's report are given it
     * @param revision the full commit id the outcomes file was made for
     * @throws IllegalStateException if the directory is not that revision's {@code dataset} directory, unchanged
     */
    public static void verify(Path dataset, String revision) {
        Path directory = dataset.toAbsolutePath().normalize();
        Path root = Path.of(git(directory, null, "rev-parse", "--show-toplevel").trim());
        if (!sameFile(root.resolve("dataset"), directory)) {
            throw new IllegalStateException(directory + " is not the dataset directory of the checkout at " + root);
        }
        String head = git(root, null, "rev-parse", "HEAD").trim();
        if (!head.equals(revision)) {
            throw new IllegalStateException("the dataset checkout is at " + head + ", expected " + revision);
        }
        // no file the revision does not have, ignored ones included: no exclude rules are read
        String others = git(root, null, "-c", "core.fsmonitor=false", "ls-files", "--others", "-z");
        if (!others.isEmpty()) {
            throw new IllegalStateException("the dataset checkout at " + revision + " has files the revision does not: "
                    + others.replace('\0', ' ').trim());
        }
        verifyContents(root, revision);
    }

    /** Every regular file of the revision, hashed as it is on disk, against the revision's blob. */
    private static void verifyContents(Path root, String revision) {
        List<String> paths = new ArrayList<>();
        List<String> blobs = new ArrayList<>();
        for (String entry : git(root, null, "ls-tree", "-r", "-z", revision).split("\0")) {
            if (entry.isEmpty()) {
                continue;
            }
            int tab = entry.indexOf('\t');
            String[] meta = entry.substring(0, tab).split(" ");
            String path = entry.substring(tab + 1);
            if (!meta[0].equals(REGULAR_FILE) && !meta[0].equals(EXECUTABLE_FILE)) {
                throw new IllegalStateException("the dataset revision holds " + path + " of mode " + meta[0]
                        + ", which this check does not compare");
            }
            if (path.indexOf('\n') >= 0) {
                throw new IllegalStateException("the dataset revision holds a path with a newline: " + path);
            }
            paths.add(path);
            blobs.add(meta[2]);
        }
        String hashed = git(root, String.join("\n", paths) + "\n", "hash-object", "--stdin-paths");
        String[] hashes = hashed.split("\n");
        if (hashes.length != blobs.size()) {
            throw new IllegalStateException("the dataset checkout at " + revision + " is missing files");
        }
        for (int i = 0; i < hashes.length; i++) {
            if (!hashes[i].trim().equals(blobs.get(i))) {
                throw new IllegalStateException("the dataset checkout at " + revision + " has changed "
                        + paths.get(i));
            }
        }
    }

    private static boolean sameFile(Path a, Path b) {
        try {
            return a.toRealPath().equals(b.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Runs git with its output in a file, so the deadline holds even when git keeps its output open while it stalls.
     *
     * @param input what to write to git's standard input, or null for none
     */
    private static String git(Path directory, String input, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(directory.toString());
        command.addAll(List.of(args));
        Path out = null;
        Path in = null;
        Process process = null;
        try {
            out = Files.createTempFile("dataset-checkout", ".out");
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(out.toFile());
            if (input != null) {
                in = Files.createTempFile("dataset-checkout", ".in");
                Files.writeString(in, input, StandardCharsets.UTF_8);
                builder.redirectInput(in.toFile());
            } else {
                builder.redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
            }
            process = builder.start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("git " + String.join(" ", args) + " did not finish");
            }
            String output = Files.readString(out, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(directory + " is not a git checkout of the dataset: git "
                        + String.join(" ", args) + ": " + output.trim());
            }
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot run git, which the dataset check needs", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while checking the dataset", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            delete(out);
            delete(in);
        }
    }

    private static java.io.File nullDevice() {
        return new java.io.File(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
    }

    private static void delete(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                // a temporary file left behind
            }
        }
    }
}
