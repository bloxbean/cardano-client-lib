package com.bloxbean.cardano.client.ledger.conformance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Whether a dataset directory is exactly a revision of cardano-cbor-dataset (ADR 0005 D8): the {@code dataset}
 * directory of a git checkout whose {@code HEAD} is that revision and whose working tree is clean, with no modified,
 * deleted or untracked file. Git compares contents, so an edited input, expected output or normalization file, or a
 * substituted sample, is found. It runs the {@code git} executable, which fetching the dataset needs anyway; it adds no
 * Git library.
 */
public final class DatasetCheckout {

    private static final long TIMEOUT_SECONDS = 120;

    private DatasetCheckout() {
    }

    /**
     * @param dataset  the dataset's {@code dataset} directory, as the gate and Yano's report are given it
     * @param revision the full commit id the outcomes file was made for
     * @throws IllegalStateException if the directory is not that revision's {@code dataset} directory, clean
     */
    public static void verify(Path dataset, String revision) {
        Path directory = dataset.toAbsolutePath().normalize();
        String root = git(directory, "rev-parse", "--show-toplevel").trim();
        Path expected = Path.of(root).resolve("dataset");
        if (!sameFile(expected, directory)) {
            throw new IllegalStateException(directory + " is not the dataset directory of the checkout at " + root);
        }
        String head = git(directory, "rev-parse", "HEAD").trim();
        if (!head.equals(revision)) {
            throw new IllegalStateException("the dataset checkout is at " + head + ", expected " + revision);
        }
        String status = git(directory, "status", "--porcelain", "--untracked-files=all");
        if (!status.isEmpty()) {
            throw new IllegalStateException("the dataset checkout at " + revision + " has changes:\n" + status);
        }
    }

    private static boolean sameFile(Path a, Path b) {
        try {
            return a.toRealPath().equals(b.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static String git(Path directory, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(directory.toString());
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = read(process.getInputStream());
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("git " + String.join(" ", args) + " did not finish");
            }
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
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
