package com.bloxbean.cardano.client.tools.readerdiff;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * One complete class path (a Yaci and a CCL build) in a class loader of its own, whose parent is the platform loader,
 * so it shares only JDK classes with the tool and the other side. This tool's own jar is on it too, as it holds the
 * probes, which are loaded here against the side's classes. Jars load in name order.
 */
final class Side implements AutoCloseable {
    private final Path dir;
    private final List<Path> jars;
    private final URLClassLoader loader;

    private Side(Path dir, List<Path> jars, URLClassLoader loader) {
        this.dir = dir;
        this.jars = jars;
        this.loader = loader;
    }

    static Side open(Path dir) throws IOException {
        List<Path> jars;
        try (Stream<Path> files = Files.list(dir)) {
            jars = files.filter(f -> f.toString().endsWith(".jar")).sorted().toList();
        }
        if (jars.isEmpty())
            throw new IllegalArgumentException("no jars in " + dir);
        List<URL> urls = new ArrayList<>();
        for (Path jar : jars)
            urls.add(jar.toUri().toURL());
        urls.add(Side.class.getProtectionDomain().getCodeSource().getLocation());
        return new Side(dir, jars, new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader()));
    }

    /** @return the probes by name, each from input bytes to an outcome ({@link Probes}) */
    Map<String, Function<byte[], String>> probes() throws ReflectiveOperationException {
        return load(Probes.class.getName());
    }

    /** @return the bare parsers by name, for the benchmark ({@link Probes.Parsers}) */
    Map<String, Function<byte[], Object>> parsers() throws ReflectiveOperationException {
        return load(Probes.Parsers.class.getName());
    }

    @SuppressWarnings("unchecked")
    private <T> T load(String className) throws ReflectiveOperationException {
        return ((Supplier<T>) loader.loadClass(className).getConstructor().newInstance()).get();
    }

    /** @return what this side is: its directory and a SHA-256 of the names and contents of its jars */
    String identity() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        for (Path jar : jars) {
            sha.update(jar.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            sha.update(Files.readAllBytes(jar));
        }
        return dir + " (" + jars.size() + " jars, sha256 " + HexFormat.of().formatHex(sha.digest()) + ")";
    }

    @Override
    public void close() throws IOException {
        loader.close();
    }
}
